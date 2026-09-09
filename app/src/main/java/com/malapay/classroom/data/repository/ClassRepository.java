package com.malapay.classroom.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.ClassDao;
import com.malapay.classroom.data.local.dao.ClassMemberDao;
import com.malapay.classroom.data.local.dao.JoinRequestDao;
import com.malapay.classroom.data.local.entity.ClassMember;
import com.malapay.classroom.data.local.entity.JoinRequest;
import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.data.local.model.ClassWithStudentCount;
import com.malapay.classroom.data.local.model.JoinRequestWithDetails;
import com.malapay.classroom.data.local.model.StudentInClass;
import com.malapay.classroom.util.ClassCodeGenerator;

import java.util.List;

/**
 * Single entry point for all class-management data operations: creating,
 * editing, deleting, and searching classes (teacher side), and joining /
 * listing joined classes (student side). Also backs the stat cards on both
 * dashboards.
 *
 * All SQLite work runs on {@link AppDatabase#DATABASE_WRITE_EXECUTOR} and
 * results are always delivered back on the main thread, so Activities and
 * Fragments can safely touch views inside the callback.
 */
public class ClassRepository {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final ClassDao classDao;
    private final ClassMemberDao classMemberDao;
    private final JoinRequestDao joinRequestDao;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public ClassRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.classDao = db.classDao();
        this.classMemberDao = db.classMemberDao();
        this.joinRequestDao = db.joinRequestDao();
    }

    // ---------------------------------------------------------------
    // Dashboard stat cards
    // ---------------------------------------------------------------

    /** Total number of classes owned by this teacher. */
    public void getTotalClasses(long teacherId, RepositoryCallback<Integer> callback) {
        runAsync(() -> classDao.countClassesByTeacher(teacherId), callback);
    }

    /** Total number of distinct students across all of this teacher's classes. */
    public void getTotalStudents(long teacherId, RepositoryCallback<Integer> callback) {
        runAsync(() -> classMemberDao.countDistinctStudentsForTeacher(teacherId), callback);
    }

    /** Number of classes this student has joined. */
    public void getJoinedClassesCount(long studentId, RepositoryCallback<Integer> callback) {
        runAsync(() -> classMemberDao.countJoinedClassesForStudent(studentId), callback);
    }

    // ---------------------------------------------------------------
    // Teacher: create / edit / delete / search / view roster
    // ---------------------------------------------------------------

    /**
     * Creates a new class with an auto-generated, guaranteed-unique class code.
     * Field validation (non-empty) is expected to have already happened in the UI.
     */
    public void createClass(long teacherId, String className, String subject,
                             String section, String schoolYear, RepositoryCallback<SchoolClass> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                String code = generateUniqueClassCode();
                SchoolClass schoolClass = new SchoolClass(
                        teacherId, className.trim(), subject.trim(), section.trim(),
                        schoolYear.trim(), code, System.currentTimeMillis());
                long id = classDao.insert(schoolClass);
                schoolClass.setId(id);
                postSuccess(callback, schoolClass);
            } catch (Exception e) {
                postError(callback, "Could not create the class. Please try again.");
            }
        });
    }

    public void updateClass(SchoolClass schoolClass, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                classDao.update(schoolClass);
                postSuccess(callback, null);
            } catch (Exception e) {
                postError(callback, "Could not save changes. Please try again.");
            }
        });
    }

    public void deleteClass(SchoolClass schoolClass, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                classDao.delete(schoolClass);
                postSuccess(callback, null);
            } catch (Exception e) {
                postError(callback, "Could not delete the class. Please try again.");
            }
        });
    }

    public void getClassById(long classId, RepositoryCallback<SchoolClass> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                SchoolClass result = classDao.findById(classId);
                if (result == null) {
                    postError(callback, "Class not found.");
                } else {
                    postSuccess(callback, result);
                }
            } catch (Exception e) {
                postError(callback, "Could not load the class. Please try again.");
            }
        });
    }

    public void getClassesForTeacher(long teacherId, RepositoryCallback<List<ClassWithStudentCount>> callback) {
        runAsync(() -> classDao.getClassesForTeacher(teacherId), callback);
    }

    public void searchClassesForTeacher(long teacherId, String query, RepositoryCallback<List<ClassWithStudentCount>> callback) {
        runAsync(() -> classDao.searchClassesForTeacher(teacherId, query.trim()), callback);
    }

    public void getStudentsInClass(long classId, RepositoryCallback<List<StudentInClass>> callback) {
        runAsync(() -> classMemberDao.getStudentsInClass(classId), callback);
    }

    // ---------------------------------------------------------------
    // Student: join by code / list joined classes
    // ---------------------------------------------------------------

    /**
     * Requests to join a class by code. Instead of immediately creating a
     * ClassMember, this creates a JoinRequest with PENDING status that the
     * teacher must approve.
     */
    public void joinClass(long studentId, String classCode, RepositoryCallback<SchoolClass> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                String normalizedCode = classCode.trim().toUpperCase();
                SchoolClass schoolClass = classDao.findByCode(normalizedCode);
                if (schoolClass == null) {
                    postError(callback, "Invalid class code. Please check and try again.");
                    return;
                }
                if (classMemberDao.isStudentInClass(schoolClass.getId(), studentId)) {
                    postError(callback, "You already joined this class.");
                    return;
                }
                if (joinRequestDao.hasApprovedRequest(schoolClass.getId(), studentId)) {
                    postError(callback, "You already joined this class.");
                    return;
                }
                if (joinRequestDao.hasPendingRequest(schoolClass.getId(), studentId)) {
                    postError(callback, "You already have a pending request for this class.");
                    return;
                }
                JoinRequest request = new JoinRequest(schoolClass.getId(), studentId, System.currentTimeMillis());
                joinRequestDao.insert(request);
                postSuccess(callback, schoolClass);
            } catch (Exception e) {
                postError(callback, "Could not send join request. Please try again.");
            }
        });
    }

    /**
     * Removes a student's membership from a class (local DB).
     * Also cleans up any join requests for this student/class pair.
     */
    public void leaveClass(long classId, long studentId, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                classMemberDao.deleteByClassAndStudent(classId, studentId);
                joinRequestDao.deleteByClassAndStudent(classId, studentId);
                postSuccess(callback, null);
            } catch (Exception e) {
                postError(callback, "Could not leave the class. Please try again.");
            }
        });
    }

    /** Approves a join request: creates a ClassMember and marks the request APPROVED. */
    public void approveJoinRequest(long requestId, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                JoinRequest request = joinRequestDao.findById(requestId);
                if (request == null) {
                    postError(callback, "Request not found.");
                    return;
                }
                classMemberDao.insert(new ClassMember(request.getClassId(), request.getStudentId(), System.currentTimeMillis()));
                joinRequestDao.approveRequest(requestId, System.currentTimeMillis());
                postSuccess(callback, null);
            } catch (Exception e) {
                postError(callback, "Could not approve request. Please try again.");
            }
        });
    }

    /** Denies a join request. */
    public void denyJoinRequest(long requestId, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                joinRequestDao.denyRequest(requestId, System.currentTimeMillis());
                postSuccess(callback, null);
            } catch (Exception e) {
                postError(callback, "Could not deny request. Please try again.");
            }
        });
    }

    /** Count of pending join requests for the notification badge. */
    public void getPendingJoinRequestCount(long teacherId, RepositoryCallback<Integer> callback) {
        runAsync(() -> joinRequestDao.countPendingForTeacher(teacherId), callback);
    }

    public void getJoinedClasses(long studentId, RepositoryCallback<List<SchoolClass>> callback) {
        runAsync(() -> classMemberDao.getJoinedClasses(studentId), callback);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Generates a class code and retries on the astronomically rare collision. */
    private String generateUniqueClassCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = ClassCodeGenerator.generate();
            if (!classDao.classCodeExists(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate a unique class code after "
                + MAX_CODE_GENERATION_ATTEMPTS + " attempts.");
    }

    private interface DbWork<T> {
        T run();
    }

    private <T> void runAsync(DbWork<T> work, RepositoryCallback<T> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                T result = work.run();
                postSuccess(callback, result);
            } catch (Exception e) {
                postError(callback, "Something went wrong. Please try again.");
            }
        });
    }

    private <T> void postSuccess(RepositoryCallback<T> callback, T result) {
        mainThreadHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(RepositoryCallback<T> callback, String message) {
        mainThreadHandler.post(() -> callback.onError(message));
    }
}
