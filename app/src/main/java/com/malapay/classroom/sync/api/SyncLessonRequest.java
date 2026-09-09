package com.malapay.classroom.sync.api;

import androidx.annotation.Nullable;
import okhttp3.RequestBody;

/** Request body for POST /api/sync_lesson.php — uploads a lesson to the server.
 *
 * When a lesson has an attachment, the file bytes are sent as a multipart part
 * alongside the text fields. The request can be built either as a plain JSON
 * payload (no attachment) or as a multipart payload (with an optional
 * attachment file). {@link ApiService#syncLesson(SyncLessonRequest)} handles
 * both shapes.
 */
public class SyncLessonRequest {
    public long classId;
    public String title;
    public String body;
    public String subject;
    public String videoLink;
    public String teacherEmail;

    /** Optional attachment file bytes, set only when the lesson has an attachment. */
    @Nullable
    public RequestBody attachmentFile;

    /** Original file name of the attachment, set only when the lesson has one. */
    @Nullable
    public String attachmentName;

    /** MIME type of the attachment, set only when the lesson has one. */
    @Nullable
    public String attachmentMime;

    public SyncLessonRequest(long classId, String title, String body, String subject,
                              String videoLink, String teacherEmail) {
        this.classId = classId;
        this.title = title;
        this.body = body;
        this.subject = subject;
        this.videoLink = videoLink;
        this.teacherEmail = teacherEmail;
    }

    /** Builder-style helper to attach a file to this request. */
    @SuppressWarnings("unused")
    public void setAttachment(@Nullable RequestBody file, @Nullable String name, @Nullable String mime) {
        this.attachmentFile = file;
        this.attachmentName = name;
        this.attachmentMime = mime;
    }
}
