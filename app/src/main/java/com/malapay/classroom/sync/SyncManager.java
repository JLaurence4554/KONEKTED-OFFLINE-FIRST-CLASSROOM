package com.malapay.classroom.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.ClassDao;
import com.malapay.classroom.data.local.dao.ClassMemberDao;
import com.malapay.classroom.data.local.dao.JoinRequestDao;
import com.malapay.classroom.data.local.dao.UserDao;
import com.malapay.classroom.data.local.entity.ClassMember;
import com.malapay.classroom.data.local.entity.JoinRequest;
import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.data.local.entity.User;
import com.malapay.classroom.data.repository.LessonRepository;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.sync.api.ApiClient;
import com.malapay.classroom.sync.api.ApiEnvelope;
import com.malapay.classroom.sync.api.ApiService;
import com.malapay.classroom.sync.api.FindClassData;
import com.malapay.classroom.sync.api.JoinClassRequest;
import com.malapay.classroom.sync.api.LeaveClassRequest;
import com.malapay.classroom.sync.api.SyncClassData;
import com.malapay.classroom.sync.api.SyncClassRequest;
import com.malapay.classroom.sync.api.GetJoinRequestsData;
import com.malapay.classroom.sync.api.ServerJoinRequest;
import com.malapay.classroom.sync.api.SyncJoinRequest;
import com.malapay.classroom.sync.api.ApproveJoinRequest;
import com.malapay.classroom.sync.api.SyncJoinRequestResult;
import com.malapay.classroom.sync.api.GetStudentClassesData;
import com.malapay.classroom.sync.api.ServerClassData;
import com.malapay.classroom.util.PasswordUtil;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The synchronization layer described in the capstone spec: local Room
 * stays the source of truth for offline operation, and this class is the
 * only thing that talks to the backend and then reconciles the result back
 * into Room. Nothing in the Repository classes or any Activity calls the
 * network directly.
 *
 * Two kinds of work:
 *  1. "Catch-up" sync - syncPendingClasses, syncPendingMemberships,
 *     syncPendingLessons, syncPendingQuizzes, syncPendingQuizResults
 *     find whatever's still PENDING/FAILED and (re)upload it.
 *  2. The online Join Class flow - joinClassOnline - which is the
 *     one operation that's inherently synchronous-feeling from the user's
 *     point of view.
 *
 * "automatically sync when internet becomes available" is implemented by
 * calling the catch-up methods at natural checkpoints already in the app
 * (dashboard onResume, right after creating content) rather than a true
 * always-on trigger.
 */
public class SyncManager {

    private static final String TAG = "SyncManager";
    private static final String API_UNAVAILABLE = "API not available. Check your BASE_URL configuration.";

    private final Context appContext;
    private final ClassDao classDao;
    private final ClassMemberDao classMemberDao;
    private final JoinRequestDao joinRequestDao;
    private final UserDao userDao;
    private final ApiService apiService;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public SyncManager(Context context) {
        this.appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        this.classDao = db.classDao();
        this.classMemberDao = db.classMemberDao();
        this.joinRequestDao = db.joinRequestDao();
        this.userDao = db.userDao();
        this.apiService = ApiClient.getApiService();
    }

    /** Returns true if the backend API is reachable (valid BASE_URL and initialized). */
    public boolean isApiAvailable() {
        return apiService != null;
    }

    // ---------------------------------------------------------------
    // Catch-up sync (teacher classes, student memberships)
    // ---------------------------------------------------------------

    /**
     * Uploads every locally-created/edited class that isn't SYNCED yet.
     */
    public void syncPendingClasses(RepositoryCallback<Integer> callback) {
        if (!NetworkMonitor.isOnline(appContext) || !isApiAvailable()) {
            callback.onError("You're offline. Your changes will be synchronized when internet is available.");
            return;
        }
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            List<SchoolClass> pending = classDao.getPendingClasses();
            if (pending.isEmpty()) {
                postSuccess(callback, 0);
                return;
            }
            int[] remaining = {pending.size()};
            for (SchoolClass schoolClass : pending) {
                uploadClass(schoolClass, () -> {
                    synchronized (remaining) {
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() ->
                                    postSuccess(callback, classDao.getPendingClasses().size()));
                        }
                    }
                });
            }
        });
    }

    /** Uploads every local membership that isn't SYNCED yet. */
    public void syncPendingMemberships(RepositoryCallback<Integer> callback) {
        if (!NetworkMonitor.isOnline(appContext) || !isApiAvailable()) {
            callback.onError("You're offline. Your changes will be synchronized when internet is available.");
            return;
        }
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            List<ClassMember> pending = classMemberDao.getPendingMemberships();
            if (pending.isEmpty()) {
                postSuccess(callback, 0);
                return;
            }
            int[] remaining = {pending.size()};
            for (ClassMember member : pending) {
                SchoolClass schoolClass = classDao.findById(member.getClassId());
                User student = userDao.findById(member.getStudentId());
                if (schoolClass == null || student == null) {
                    decrementAndMaybeFinish(remaining, callback);
                    continue;
                }
                uploadMembership(member, schoolClass.getClassCode(), student.getFullName(), student.getEmail(),
                        () -> decrementAndMaybeFinish(remaining, callback));
            }
        });
    }

    // ---------------------------------------------------------------
    // Catch-up sync: Lessons, Quizzes, Quiz Results
    // ---------------------------------------------------------------

    /**
     * Best-effort, silent catch-up of all syncable content - no UI
     * feedback, safe to call from onResume(). Does nothing if offline
     * or if there's nothing pending.
     */
    public void syncAllSilently() {
        if (!NetworkMonitor.isOnline(appContext) || !isApiAvailable()) {
            return;
        }
        RepositoryCallback<Integer> silent = new RepositoryCallback<Integer>() {
            @Override public void onSuccess(Integer result) { }
            @Override public void onError(String message) { }
        };
        syncPendingClasses(silent);
        syncPendingMemberships(silent);
        syncPendingJoinRequests();
        syncPendingLessons();
        syncPendingQuizzes();
        syncPendingQuizResults();
    }

    /**
     * Push all locally-created lessons that haven't been synced yet.
     * Runs on a background thread.
     */
    private void syncPendingLessons() {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                LessonRepository lessonRepo = new LessonRepository(appContext);
                lessonRepo.pushUnsyncedLessons();
            } catch (Exception e) {
                Log.w(TAG, "syncPendingLessons error: " + e.getMessage());
            }
        });
    }

    /**
     * Push all locally-created quizzes (with their questions) that haven't been synced yet.
     * Runs on a background thread.
     */
    private void syncPendingQuizzes() {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                QuizRepository quizRepo = new QuizRepository(appContext);
                quizRepo.pushUnsyncedQuizzes();
            } catch (Exception e) {
                Log.w(TAG, "syncPendingQuizzes error: " + e.getMessage());
            }
        });
    }

    private void syncPendingQuizResults() {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                QuizRepository quizRepo = new QuizRepository(appContext);
                quizRepo.pushUnsyncedQuizResults();
            } catch (Exception e) {
                Log.w(TAG, "syncPendingQuizResults error: " + e.getMessage());
            }
        });
    }

    private void syncPendingJoinRequests() {
        if (!isApiAvailable()) return;
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                List<JoinRequest> pending = joinRequestDao.getPendingSyncRequests();
                for (JoinRequest request : pending) {
                    SchoolClass sc = classDao.findById(request.getClassId());
                    User s = userDao.findById(request.getStudentId());
                    if (sc == null || s == null) {
                        joinRequestDao.markSynced(request.getId(), System.currentTimeMillis());
                        continue;
                    }
                    SyncJoinRequest body = new SyncJoinRequest(sc.getClassCode(), s.getFullName(), s.getEmail());
                    apiService.syncJoinRequest(body).enqueue(new Callback<ApiEnvelope<SyncJoinRequestResult>>() {
                        @Override
                        public void onResponse(Call<ApiEnvelope<SyncJoinRequestResult>> c, Response<ApiEnvelope<SyncJoinRequestResult>> resp) {
                            AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                                ApiEnvelope<SyncJoinRequestResult> b = resp.body();
                                if (resp.isSuccessful() && b != null && b.success) {
                                    joinRequestDao.markSynced(request.getId(), System.currentTimeMillis());
                                } else {
                                    joinRequestDao.markFailed(request.getId());
                                }
                            });
                        }
                        @Override
                        public void onFailure(Call<ApiEnvelope<SyncJoinRequestResult>> c, Throwable t) {
                            Log.w(TAG, "syncJoinReq failed: " + t.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "syncPendingJoinRequests error: " + e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------
    // Online Join Class flow
    // ---------------------------------------------------------------

    public void joinClassOnline(long studentId, String studentName, String studentEmail,
                                 String rawClassCode, RepositoryCallback<SchoolClass> callback) {
        String classCode = rawClassCode.trim().toUpperCase();
        boolean online = NetworkMonitor.isOnline(appContext);

        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            SchoolClass local = classDao.findByCode(classCode);
            if (local != null) {
                requestToJoinLocalClass(local, studentId, callback);
                return;
            }
            if (!online || !isApiAvailable()) {
                postError(callback, "You are offline. Your changes will be synchronized when internet is available.");
                return;
            }
            findClassAndRequest(classCode, studentId, callback);
        });
    }

    private void requestToJoinLocalClass(SchoolClass local, long studentId, RepositoryCallback<SchoolClass> callback) {
        if (classMemberDao.isStudentInClass(local.getId(), studentId)) {
            postError(callback, "You are already a member of this class.");
            return;
        }
        if (joinRequestDao.hasApprovedRequest(local.getId(), studentId)) {
            postError(callback, "You are already a member of this class.");
            return;
        }
        if (joinRequestDao.hasPendingRequest(local.getId(), studentId)) {
            postError(callback, "You already have a pending request for this class.");
            return;
        }
        JoinRequest request = new JoinRequest(local.getId(), studentId, System.currentTimeMillis());
        joinRequestDao.insert(request);
        postSuccess(callback, local);
    }
    private void findClassAndRequest(String classCode, long studentId, RepositoryCallback<SchoolClass> callback) {
        apiService.findClass(classCode).enqueue(new Callback<ApiEnvelope<FindClassData>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<FindClassData>> call, Response<ApiEnvelope<FindClassData>> response) {
                AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                    ApiEnvelope<FindClassData> body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        postError(callback, "Unable to connect to the server. Please try again.");
                        return;
                    }
                    if (!body.success || body.data == null) {
                        postError(callback, body.message != null && !body.message.isEmpty() ? body.message : "Class code not found.");
                        return;
                    }
                    saveFoundClassAndRequest(body.data, studentId, callback);
                });
            }
            @Override
            public void onFailure(Call<ApiEnvelope<FindClassData>> call, Throwable t) {
                String message = (t instanceof SocketTimeoutException) ? "Connection timed out. Please try again." : "Unable to connect to the server. Please try again.";
                postError(callback, message);
            }
        });
    }

    private void saveFoundClassAndRequest(FindClassData data, long studentId, RepositoryCallback<SchoolClass> callback) {
        long teacherLocalId = getOrCreateLocalProfile(data.teacherName, data.teacherEmail, User.ROLE_TEACHER);
        SchoolClass newClass = new SchoolClass(teacherLocalId, data.className, data.subject, data.section, data.schoolYear, data.classCode, System.currentTimeMillis());
        newClass.setSyncStatus(SyncStatus.SYNCED);
        newClass.setServerId(data.serverId);
        newClass.setLastSyncedAt(System.currentTimeMillis());
        long localClassId;
        try {
            localClassId = classDao.insert(newClass);
        } catch (Exception e) {
            SchoolClass existing = classDao.findByCode(data.classCode);
            if (existing == null) {
                postError(callback, "Could not save the class. Please try again.");
                return;
            }
            localClassId = existing.getId();
        }
        newClass.setId(localClassId);
        if (classMemberDao.isStudentInClass(localClassId, studentId)) {
            postError(callback, "You are already a member of this class.");
            return;
        }
        if (joinRequestDao.hasPendingRequest(localClassId, studentId)) {
            postError(callback, "You already have a pending request for this class.");
            return;
        }
        JoinRequest request = new JoinRequest(localClassId, studentId, System.currentTimeMillis());
        joinRequestDao.insert(request);
        postSuccess(callback, newClass);
    }

    private long getOrCreateLocalProfile(String fullName, String email, String role) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        User existing = userDao.findByEmail(normalizedEmail);
        if (existing != null) {
            return existing.getId();
        }
        String unusablePasswordHash = PasswordUtil.hashPassword(UUID.randomUUID().toString());
        User placeholder = new User(fullName, normalizedEmail, unusablePasswordHash, role, System.currentTimeMillis());
        return userDao.insert(placeholder);
    }
    private void decrementAndMaybeFinish(int[] remaining, RepositoryCallback<Integer> callback) {
        synchronized (remaining) {
            remaining[0]--;
            if (remaining[0] == 0) {
                AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> postSuccess(callback, classMemberDao.getPendingMemberships().size()));
            }
        }
    }

    private void uploadClass(SchoolClass schoolClass, Runnable onDone) {
        User teacher = userDao.findById(schoolClass.getTeacherId());
        if (teacher == null) {
            classDao.markClassFailed(schoolClass.getId());
            onDone.run();
            return;
        }
        SyncClassRequest request = new SyncClassRequest(schoolClass.getClassName(), schoolClass.getSubject(), schoolClass.getSection(), schoolClass.getSchoolYear(), schoolClass.getClassCode(), teacher.getFullName(), teacher.getEmail());
        apiService.syncClass(request).enqueue(new Callback<ApiEnvelope<SyncClassData>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SyncClassData>> call, Response<ApiEnvelope<SyncClassData>> response) {
                AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                    ApiEnvelope<SyncClassData> body = response.body();
                    if (response.isSuccessful() && body != null && body.success && body.data != null) {
                        classDao.markClassSynced(schoolClass.getId(), body.data.serverId, System.currentTimeMillis());
                    } else {
                        Log.w(TAG, "SYNC_ERROR class upload rejected: " + (body != null ? body.message : "HTTP " + response.code()));
                        classDao.markClassFailed(schoolClass.getId());
                    }
                    onDone.run();
                });
            }
            @Override
            public void onFailure(Call<ApiEnvelope<SyncClassData>> call, Throwable t) {
                AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                    Log.w(TAG, "SYNC_ERROR class upload failed: " + t.getMessage());
                    classDao.markClassFailed(schoolClass.getId());
                    onDone.run();
                });
            }
        });
    }

    private void uploadMembership(ClassMember member, String classCode, String studentName, String studentEmail, Runnable onDone) {
        JoinClassRequest request = new JoinClassRequest(classCode, studentName, studentEmail);
        apiService.joinClass(request).enqueue(new Callback<ApiEnvelope<Void>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<Void>> call, Response<ApiEnvelope<Void>> response) {
                AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                    ApiEnvelope<Void> body = response.body();
                    boolean serverHasIt = response.isSuccessful() && body != null && (body.success || "You are already a member of this class.".equals(body.message));
                    if (serverHasIt) {
                        classMemberDao.markMembershipSynced(member.getId(), System.currentTimeMillis());
                    } else {
                        Log.w(TAG, "SYNC_ERROR membership upload rejected: " + (body != null ? body.message : "HTTP " + response.code()));
                        classMemberDao.markMembershipFailed(member.getId());
                    }
                    onDone.run();
                });
            }
            @Override
            public void onFailure(Call<ApiEnvelope<Void>> call, Throwable t) {
                AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                    Log.w(TAG, "SYNC_ERROR membership upload failed: " + t.getMessage());
                    classMemberDao.markMembershipFailed(member.getId());
                    onDone.run();
                });
            }
        });
    }

    // ---------------------------------------------------------------
    // Online Leave Class flow
    // ---------------------------------------------------------------

    /**
     * Pushes a leave-class action to the server. Best-effort — if the
     * device is offline or the call fails, the local removal already
     * happened and the server will eventually be consistent on next sync.
     */
    public void leaveClassOnline(String classCode, String studentEmail, RepositoryCallback<Void> callback) {
        if (!NetworkMonitor.isOnline(appContext) || !isApiAvailable()) {
            postError(callback, "Offline — leave saved locally.");
            return;
        }
        LeaveClassRequest request = new LeaveClassRequest(classCode, studentEmail);
        apiService.leaveClass(request).enqueue(new Callback<ApiEnvelope<Void>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<Void>> call, Response<ApiEnvelope<Void>> response) {
                ApiEnvelope<Void> body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    Log.d(TAG, "leaveClassOnline: left class on server");
                    postSuccess(callback, null);
                } else {
                    Log.w(TAG, "leaveClassOnline: server rejected: " + (body != null ? body.message : "HTTP " + response.code()));
                    postError(callback, body != null ? body.message : "Server error.");
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<Void>> call, Throwable t) {
                Log.e(TAG, "leaveClassOnline failed: " + t.getMessage());
                postError(callback, "Unable to connect to server.");
            }
        });
    }

    // ---------------------------------------------------------------
    // Server fetch: Join Requests & Student Classes
    // ---------------------------------------------------------------

    public void fetchJoinRequestsFromServer(String teacherEmail, RepositoryCallback<Integer> cb) {
        if (!isApiAvailable()) { cb.onError("API not available."); return; }
        apiService.getJoinRequests(teacherEmail).enqueue(new Callback<ApiEnvelope<GetJoinRequestsData>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<GetJoinRequestsData>> call, Response<ApiEnvelope<GetJoinRequestsData>> response) {
                AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                    try {
                        ApiEnvelope<GetJoinRequestsData> body = response.body();
                        if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                            postError(cb, "Unable to fetch join requests."); return;
                        }
                        int saved = 0;
                        List<ServerJoinRequest> requests = body.data.requests;
                        if (requests == null) { postSuccess(cb, 0); return; }
                        for (ServerJoinRequest sr : requests) {
                            long localStudentId = getOrCreateLocalProfile(sr.studentName, sr.studentEmail != null && !sr.studentEmail.isEmpty() ? sr.studentEmail : "", User.ROLE_STUDENT);
                            SchoolClass localClass = classDao.findByServerId(sr.classId);
                            if (localClass == null && sr.classCode != null) {
                                localClass = classDao.findByCode(sr.classCode);
                            }
                            if (localClass == null) { continue; }
                            if (joinRequestDao.hasPendingRequest(localClass.getId(), localStudentId)) continue;
                            if (joinRequestDao.hasApprovedRequest(localClass.getId(), localStudentId)) continue;
                            joinRequestDao.insert(new JoinRequest(localClass.getId(), localStudentId, sr.requestedAt));
                            saved++;
                        }
                        postSuccess(cb, saved);
                    } catch (Exception e) {
                        Log.e(TAG, "fetchJoinRequestsFromServer error: " + e.getMessage());
                        postError(cb, "Unable to fetch join requests.");
                    }
                });
            }
            @Override public void onFailure(Call<ApiEnvelope<GetJoinRequestsData>> call, Throwable t) { postError(cb, "Unable to connect."); }
        });
    }

    public void fetchStudentClassesFromServer(String studentEmail, RepositoryCallback<Integer> cb) {
        if (!isApiAvailable()) { cb.onError("API not available."); return; }
        apiService.getStudentClasses(studentEmail).enqueue(new Callback<ApiEnvelope<GetStudentClassesData>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<GetStudentClassesData>> call, Response<ApiEnvelope<GetStudentClassesData>> response) {
                AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                    try {
                        ApiEnvelope<GetStudentClassesData> body = response.body();
                        if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                            postError(cb, "Unable to fetch classes."); return;
                        }
                        List<ServerClassData> classes = body.data.classes;
                        if (classes == null) { postSuccess(cb, 0); return; }

                        User student = userDao.findByEmail(studentEmail.trim().toLowerCase());
                        if (student == null) { postError(cb, "Student not found locally."); return; }

                        // 1. Upsert classes and memberships returned by server
                        java.util.Set<Long> serverClassLocalIds = new java.util.HashSet<>();
                        int saved = 0;
                        for (ServerClassData sc : classes) {
                            long teacherId = getOrCreateLocalProfile(sc.teacherName, sc.teacherEmail, User.ROLE_TEACHER);
                            SchoolClass localClass = classDao.findByServerId(sc.serverId);
                            if (localClass == null && sc.classCode != null) {
                                localClass = classDao.findByCode(sc.classCode);
                            }
                            if (localClass == null) {
                                SchoolClass nc = new SchoolClass(teacherId, sc.className, sc.subject, sc.section, sc.schoolYear, sc.classCode, System.currentTimeMillis());
                                nc.setSyncStatus(SyncStatus.SYNCED);
                                nc.setServerId(sc.serverId);
                                nc.setLastSyncedAt(System.currentTimeMillis());
                                try { long lid = classDao.insert(nc); nc.setId(lid); localClass = nc; } catch (Exception e) { localClass = classDao.findByCode(sc.classCode); }
                            }
                            if (localClass == null) continue;
                            serverClassLocalIds.add(localClass.getId());
                            if (!classMemberDao.isStudentInClass(localClass.getId(), student.getId())) {
                                classMemberDao.insert(new ClassMember(localClass.getId(), student.getId(), System.currentTimeMillis()));
                                saved++;
                            }
                        }

                        // 2. Reconciliation: remove local memberships for classes the server
                        //    no longer returns (teacher removed student, or student left on
                        //    another device). This prevents stale memberships from lingering.
                        java.util.List<Long> currentLocalIds = classMemberDao.getClassIdsForStudent(student.getId());
                        for (Long localClassId : currentLocalIds) {
                            if (!serverClassLocalIds.contains(localClassId)) {
                                Log.d(TAG, "Reconciling: removing stale membership for class " + localClassId);
                                classMemberDao.deleteByClassAndStudent(localClassId, student.getId());
                            }
                        }

                        postSuccess(cb, saved);
                    } catch (Exception e) {
                        Log.e(TAG, "fetchStudentClassesFromServer error: " + e.getMessage());
                        postError(cb, "Unable to fetch classes.");
                    }
                });
            }
            @Override public void onFailure(Call<ApiEnvelope<GetStudentClassesData>> call, Throwable t) { postError(cb, "Unable to connect."); }
        });
    }

    /**
     * Sends the approve action to the server so the student appears
     * in the class_members table for get_student_classes.php.
     */
    public void approveJoinRequestOnServer(String classCode, String studentEmail, RepositoryCallback<Void> cb) {
        if (!NetworkMonitor.isOnline(appContext)) {
            postSuccess(cb, null);
            return;
        }
        try {
            ApproveJoinRequest request = new ApproveJoinRequest(classCode, studentEmail);
            apiService.approveJoinRequest(request).enqueue(new retrofit2.Callback<ApiEnvelope<Void>>() {
                @Override
                public void onResponse(retrofit2.Call<ApiEnvelope<Void>> call, retrofit2.Response<ApiEnvelope<Void>> response) {
                    ApiEnvelope<Void> body = response.body();
                    if (body != null && body.success) {
                        Log.d(TAG, "approveJoinRequestOnServer: approved on server");
                    } else {
                        Log.w(TAG, "approveJoinRequestOnServer: server said no: " + (body != null ? body.message : "null response"));
                    }
                    postSuccess(cb, null);
                }
                @Override
                public void onFailure(retrofit2.Call<ApiEnvelope<Void>> call, Throwable t) {
                    Log.e(TAG, "approveJoinRequestOnServer failed: " + t.getMessage());
                    postSuccess(cb, null);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "approveJoinRequestOnServer error: " + e.getMessage());
            postSuccess(cb, null);
        }
    }
    private <T> void postSuccess(RepositoryCallback<T> callback, T result) {
        mainThreadHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(RepositoryCallback<T> callback, String message) {
        mainThreadHandler.post(() -> callback.onError(message));
    }
}
