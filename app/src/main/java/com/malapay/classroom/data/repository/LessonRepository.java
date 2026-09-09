package com.malapay.classroom.data.repository;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.ClassDao;
import com.malapay.classroom.data.local.dao.LessonDao;
import com.malapay.classroom.data.local.entity.Lesson;
import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.sync.api.ApiClient;
import com.malapay.classroom.sync.api.ApiEnvelope;
import com.malapay.classroom.sync.api.GetLessonsData;
import com.malapay.classroom.sync.api.SyncLessonData;
import com.malapay.classroom.sync.api.SyncLessonRequest;
import com.malapay.classroom.util.FileUtil;
import com.malapay.classroom.util.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.List;

/**
 * Single entry point for all lesson data operations. Image and attachment
 * are two independent optional files; each is copied into app-private
 * storage as part of create/update, and deleted alongside the DB row on
 * delete or on removal/replacement - the DB row and files on disk are
 * always kept in sync. The video link is plain text only, never downloaded.
 */
public class LessonRepository {

    private final LessonDao lessonDao;
    private final ClassDao classDao;
    private final Context appContext;
    private final SessionManager sessionManager;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public LessonRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.lessonDao = db.lessonDao();
        this.classDao = db.classDao();
        this.appContext = context.getApplicationContext();
        this.sessionManager = new SessionManager(context);
    }

    /**
     * Creates a lesson. {@code imageUri}, {@code attachmentUri}, and
     * {@code videoLink} may all be null/blank (a lesson can be text-only).
     * Field validation (non-empty title/description) is expected to have
     * already happened in the UI.
     */
    public void createLesson(long classId, String title, String description, String subject,
                              @Nullable String videoLink, @Nullable Uri imageUri,
                              @Nullable Uri attachmentUri, RepositoryCallback<Lesson> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                String imagePath = null, imageName = null;
                if (imageUri != null) {
                    imageName = FileUtil.getFileName(appContext, imageUri);
                    imagePath = FileUtil.copyToInternalStorage(appContext, imageUri, imageName);
                }

                String attachmentPath = null, attachmentName = null, attachmentMime = null;
                if (attachmentUri != null) {
                    attachmentName = FileUtil.getFileName(appContext, attachmentUri);
                    attachmentMime = FileUtil.getMimeType(appContext, attachmentUri);
                    attachmentPath = FileUtil.copyToInternalStorage(appContext, attachmentUri, attachmentName);
                }

                long now = System.currentTimeMillis();
                Lesson lesson = new Lesson(classId, title.trim(), description.trim(), subject.trim(),
                        normalizeLink(videoLink), imagePath, imageName,
                        attachmentPath, attachmentName, attachmentMime, now, now);
                long id = lessonDao.insert(lesson);
                lesson.setId(id);
                postSuccess(callback, lesson);
            } catch (Exception e) {
                postError(callback, "Could not save the lesson. Please try again.");
            }
        });
    }

    /**
     * Updates a lesson's text fields, and optionally its image and/or attachment.
     */
    public void updateLesson(Lesson lesson, @Nullable Uri newImageUri, boolean removeImage,
                              @Nullable Uri newAttachmentUri, boolean removeAttachment,
                              RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                if (newImageUri != null) {
                    FileUtil.deleteFile(lesson.getImagePath());
                    String name = FileUtil.getFileName(appContext, newImageUri);
                    String path = FileUtil.copyToInternalStorage(appContext, newImageUri, name);
                    lesson.setImagePath(path);
                    lesson.setImageName(name);
                } else if (removeImage) {
                    FileUtil.deleteFile(lesson.getImagePath());
                    lesson.setImagePath(null);
                    lesson.setImageName(null);
                }

                if (newAttachmentUri != null) {
                    FileUtil.deleteFile(lesson.getAttachmentPath());
                    String name = FileUtil.getFileName(appContext, newAttachmentUri);
                    String mime = FileUtil.getMimeType(appContext, newAttachmentUri);
                    String path = FileUtil.copyToInternalStorage(appContext, newAttachmentUri, name);
                    lesson.setAttachmentPath(path);
                    lesson.setAttachmentName(name);
                    lesson.setAttachmentMimeType(mime);
                } else if (removeAttachment) {
                    FileUtil.deleteFile(lesson.getAttachmentPath());
                    lesson.setAttachmentPath(null);
                    lesson.setAttachmentName(null);
                    lesson.setAttachmentMimeType(null);
                }

                lesson.setUpdatedAt(System.currentTimeMillis());
                lessonDao.update(lesson);
                postSuccess(callback, null);
            } catch (Exception e) {
                postError(callback, "Could not save changes. Please try again.");
            }
        });
    }

    public void deleteLesson(Lesson lesson, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                FileUtil.deleteFile(lesson.getImagePath());
                FileUtil.deleteFile(lesson.getAttachmentPath());
                lessonDao.delete(lesson);
                postSuccess(callback, null);
            } catch (Exception e) {
                postError(callback, "Could not delete the lesson. Please try again.");
            }
        });
    }

    public void getLessonById(long lessonId, RepositoryCallback<Lesson> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                Lesson result = lessonDao.findById(lessonId);
                if (result == null) {
                    postError(callback, "Lesson not found.");
                } else {
                    postSuccess(callback, result);
                }
            } catch (Exception e) {
                postError(callback, "Could not load the lesson. Please try again.");
            }
        });
    }

    public void getLessonsForClass(long classId, RepositoryCallback<List<Lesson>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                List<Lesson> result = lessonDao.getLessonsForClass(classId);
                postSuccess(callback, result);
            } catch (Exception e) {
                postError(callback, "Could not load lessons. Please try again.");
            }
        });
    }

    public void searchLessonsForClass(long classId, String query, RepositoryCallback<List<Lesson>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                List<Lesson> result = lessonDao.searchLessonsForClass(classId, query.trim());
                postSuccess(callback, result);
            } catch (Exception e) {
                postError(callback, "Could not search lessons. Please try again.");
            }
        });
    }

    /**
     * Push a locally-created lesson to the server so other devices can see it.
     * Uses the class's server_id (not the local Room id) so the backend can
     * match the lesson to the correct server-side class.
     * Called silently after local save - fire-and-forget, no callback needed.
     */
    public void pushLessonToServer(Lesson lesson) {
        SessionManager sm = sessionManager;
        if (sm == null || sm.getEmail() == null) return;

        // Look up the parent class to get its server_id for the API call
        SchoolClass schoolClass = classDao.findById(lesson.getClassId());
        if (schoolClass == null || schoolClass.getServerId() == null) {
            // Class hasn't been synced yet - lesson will be picked up by syncPendingLessons later
            return;
        }
        long serverClassId = schoolClass.getServerId();

        SyncLessonRequest request = new SyncLessonRequest(
                serverClassId,
                lesson.getTitle(),
                lesson.getBody(),
                lesson.getSubject(),
                lesson.getVideoLink(),
                sm.getEmail()
        );

        ApiClient.getApiService().syncLesson(request).enqueue(new Callback<ApiEnvelope<SyncLessonData>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SyncLessonData>> call,
                                   Response<ApiEnvelope<SyncLessonData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success) {
                    SyncLessonData data = response.body().data;
                    if (data != null && data.serverId > 0) {
                        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                            lesson.setServerId(data.serverId);
                            lessonDao.update(lesson);
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<SyncLessonData>> call, Throwable t) {
                // Silent fail - will retry on next sync
            }
        });
    }

    /**
     * Gets all lessons that haven't been synced to the server (server_id == 0).
     * Used by SyncManager for catch-up sync.
     */
    public List<Lesson> getUnsyncedLessons() {
        return lessonDao.getUnsyncedLessons();
    }

    /**
     * Uploads all locally-created lessons that haven't been synced yet.
     * Called by SyncManager's catch-up sync. Should be called on a background thread.
     */
    public void pushUnsyncedLessons() {
        List<Lesson> unsynced = getUnsyncedLessons();
        for (Lesson lesson : unsynced) {
            pushLessonToServer(lesson);
        }
    }

    /**
     * Pull lessons from the server for a class and merge into local DB.
     * Used by students to see teacher-created lessons.
     * Uses the class's server_id for the API call, not the local class ID.
     */
    public void pullLessonsFromServer(long classId, RepositoryCallback<List<Lesson>> callback) {
        // Look up the server_id for this class
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            SchoolClass schoolClass = classDao.findById(classId);
            if (schoolClass == null || schoolClass.getServerId() == null) {
                // Class hasn't been synced yet, can't pull lessons
                getLessonsForClass(classId, callback);
                return;
            }
            long serverClassId = schoolClass.getServerId();

            ApiClient.getApiService().getLessons(serverClassId).enqueue(new Callback<ApiEnvelope<GetLessonsData>>() {
                @Override
                public void onResponse(Call<ApiEnvelope<GetLessonsData>> call,
                                       Response<ApiEnvelope<GetLessonsData>> response) {
                    if (!response.isSuccessful() || response.body() == null || !response.body().success) {
                        getLessonsForClass(classId, callback);
                        return;
                    }

                    GetLessonsData data = response.body().data;
                    if (data == null || data.lessons == null || data.lessons.isEmpty()) {
                        getLessonsForClass(classId, callback);
                        return;
                    }

                    // Merge server lessons into local DB
                    AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                        for (GetLessonsData.ServerLesson sl : data.lessons) {
                            Lesson existing = lessonDao.findByServerId(sl.id);
                            if (existing == null) {
                                Lesson lesson = new Lesson(
                                        classId, sl.title, sl.body, sl.subject,
                                        sl.video_link, null, null,
                                        null, null, null,
                                        parseTimestamp(sl.created_at),
                                        parseTimestamp(sl.updated_at)
                                );
                                lesson.setServerId(sl.id);
                                lessonDao.insert(lesson);
                            }
                        }
                        List<Lesson> local = lessonDao.getLessonsForClass(classId);
                        postSuccess(callback, local);
                    });
                }

                @Override
                public void onFailure(Call<ApiEnvelope<GetLessonsData>> call, Throwable t) {
                    getLessonsForClass(classId, callback);
                }
            });
        });
    }

    private long parseTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) return System.currentTimeMillis();
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            java.util.Date date = sdf.parse(ts.replace("Z", ""));
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    @Nullable
    private String normalizeLink(@Nullable String videoLink) {
        if (videoLink == null || videoLink.trim().isEmpty()) {
            return null;
        }
        return videoLink.trim();
    }

    private <T> void postSuccess(RepositoryCallback<T> callback, T result) {
        mainThreadHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(RepositoryCallback<T> callback, String message) {
        mainThreadHandler.post(() -> callback.onError(message));
    }
}
