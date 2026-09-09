package com.malapay.classroom.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a row in the local "lessons" table. A lesson always belongs to
 * exactly one class and has text content (title + body/description); subject,
 * image, video link, and a generic attachment are all optional. Image and
 * attachment are each copied into app-private storage (see
 * {@link com.malapay.classroom.util.FileUtil}); the video link is stored as
 * plain text only — no video file is ever downloaded or stored, keeping the
 * app fully offline-first.
 */
@Entity(
        tableName = "lessons",
        indices = {@Index("class_id")},
        foreignKeys = @ForeignKey(
                entity = SchoolClass.class,
                parentColumns = "id",
                childColumns = "class_id",
                onDelete = ForeignKey.CASCADE
        )
)
public class Lesson {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "class_id")
    private long classId;

    /** Server-side ID for syncing with the backend. 0 = not yet synced. */
    @ColumnInfo(name = "server_id", defaultValue = "0")
    private long serverId;

    @NonNull
    @ColumnInfo(name = "title")
    private String title;

    /** Shown as "Description" in the UI. */
    @NonNull
    @ColumnInfo(name = "body")
    private String body;

    /** Optional topic/subject label for this specific lesson (distinct from the class's subject). */
    @NonNull
    @ColumnInfo(name = "subject")
    private String subject;

    /** Optional external video URL. Never downloaded — just opened in a browser/video app. */
    @Nullable
    @ColumnInfo(name = "video_link")
    private String videoLink;

    @Nullable
    @ColumnInfo(name = "image_path")
    private String imagePath;

    @Nullable
    @ColumnInfo(name = "image_name")
    private String imageName;

    /** Absolute path to the copied attachment file (e.g. PDF/doc) in app-private storage, or null. */
    @Nullable
    @ColumnInfo(name = "attachment_path")
    private String attachmentPath;

    /** Original file name shown to the user, or null if no attachment. */
    @Nullable
    @ColumnInfo(name = "attachment_name")
    private String attachmentName;

    /** MIME type used when opening the attachment, or null if no attachment. */
    @Nullable
    @ColumnInfo(name = "attachment_mime_type")
    private String attachmentMimeType;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public Lesson(long classId, @NonNull String title, @NonNull String body, @NonNull String subject,
                  @Nullable String videoLink, @Nullable String imagePath, @Nullable String imageName,
                  @Nullable String attachmentPath, @Nullable String attachmentName,
                  @Nullable String attachmentMimeType, long createdAt, long updatedAt) {
        this.classId = classId;
        this.title = title;
        this.body = body;
        this.subject = subject;
        this.videoLink = videoLink;
        this.imagePath = imagePath;
        this.imageName = imageName;
        this.attachmentPath = attachmentPath;
        this.attachmentName = attachmentName;
        this.attachmentMimeType = attachmentMimeType;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getClassId() {
        return classId;
    }

    public void setClassId(long classId) {
        this.classId = classId;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        this.title = title;
    }

    @NonNull
    public String getBody() {
        return body;
    }

    public void setBody(@NonNull String body) {
        this.body = body;
    }

    @NonNull
    public String getSubject() {
        return subject;
    }

    public void setSubject(@NonNull String subject) {
        this.subject = subject;
    }

    @Nullable
    public String getVideoLink() {
        return videoLink;
    }

    public void setVideoLink(@Nullable String videoLink) {
        this.videoLink = videoLink;
    }

    @Nullable
    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(@Nullable String imagePath) {
        this.imagePath = imagePath;
    }

    @Nullable
    public String getImageName() {
        return imageName;
    }

    public void setImageName(@Nullable String imageName) {
        this.imageName = imageName;
    }

    @Nullable
    public String getAttachmentPath() {
        return attachmentPath;
    }

    public void setAttachmentPath(@Nullable String attachmentPath) {
        this.attachmentPath = attachmentPath;
    }

    @Nullable
    public String getAttachmentName() {
        return attachmentName;
    }

    public void setAttachmentName(@Nullable String attachmentName) {
        this.attachmentName = attachmentName;
    }

    @Nullable
    public String getAttachmentMimeType() {
        return attachmentMimeType;
    }

    public void setAttachmentMimeType(@Nullable String attachmentMimeType) {
        this.attachmentMimeType = attachmentMimeType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean hasAttachment() {
        return attachmentPath != null && attachmentName != null;
    }

    public boolean hasImage() {
        return imagePath != null && imageName != null;
    }

    public boolean hasVideoLink() {
        return videoLink != null && !videoLink.trim().isEmpty();
    }

    /** Count of optional file/link attachments this lesson has, for display on the list card. */
    public int attachmentCount() {
        int count = 0;
        if (hasImage()) count++;
        if (hasAttachment()) count++;
        if (hasVideoLink()) count++;
        return count;
    }
}
