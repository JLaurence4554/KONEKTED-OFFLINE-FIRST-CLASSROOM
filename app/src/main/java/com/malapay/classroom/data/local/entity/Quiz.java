package com.malapay.classroom.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a row in the local "quizzes" table. A quiz always belongs to
 * exactly one lesson. Status is DRAFT until the teacher explicitly
 * publishes it - students can only see and take PUBLISHED quizzes.
 */
@Entity(
        tableName = "quizzes",
        indices = {@Index("lesson_id")},
        foreignKeys = @ForeignKey(
                entity = Lesson.class,
                parentColumns = "id",
                childColumns = "lesson_id",
                onDelete = ForeignKey.CASCADE
        )
)
public class Quiz {

    public static final String STATUS_DRAFT = "Draft";
    public static final String STATUS_PUBLISHED = "Published";

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "lesson_id")
    private long lessonId;

    @NonNull
    @ColumnInfo(name = "title")
    private String title;

    @NonNull
    @ColumnInfo(name = "instructions")
    private String instructions;

    /** Minutes, or null for no time limit. */
    @Nullable
    @ColumnInfo(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    /** Percentage (0-100) required to pass. */
    @ColumnInfo(name = "passing_score")
    private int passingScore;

    @NonNull
    @ColumnInfo(name = "status")
    private String status;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    /** Server-side ID for syncing with the backend. 0 = not yet synced. */
    @ColumnInfo(name = "server_id", defaultValue = "0")
    private long serverId;

    /** One of {@link com.malapay.classroom.sync.SyncStatus}. Defaults to PENDING. */
    @NonNull
    @ColumnInfo(name = "sync_status", defaultValue = "PENDING")
    private String syncStatus = com.malapay.classroom.sync.SyncStatus.PENDING;

    /** Epoch millis of the last successful sync, or null if never synced. */
    @ColumnInfo(name = "last_synced_at")
    private Long lastSyncedAt;

    /** The server-side lesson_id this quiz belongs to. 0 = not set. */
    @ColumnInfo(name = "server_lesson_id", defaultValue = "0")
    private long serverLessonId;

    public Quiz(long lessonId, @NonNull String title, @NonNull String instructions,
                @Nullable Integer timeLimitMinutes, int passingScore, @NonNull String status, long createdAt) {
        this.lessonId = lessonId;
        this.title = title;
        this.instructions = instructions;
        this.timeLimitMinutes = timeLimitMinutes;
        this.passingScore = passingScore;
        this.status = status;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getLessonId() {
        return lessonId;
    }

    public void setLessonId(long lessonId) {
        this.lessonId = lessonId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        this.title = title;
    }

    @NonNull
    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(@NonNull String instructions) {
        this.instructions = instructions;
    }

    @Nullable
    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setTimeLimitMinutes(@Nullable Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public int getPassingScore() {
        return passingScore;
    }

    public void setPassingScore(int passingScore) {
        this.passingScore = passingScore;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    public void setStatus(@NonNull String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    @NonNull
    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(@NonNull String syncStatus) {
        this.syncStatus = syncStatus;
    }

    @Nullable
    public Long getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(@Nullable Long lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public long getServerLessonId() {
        return serverLessonId;
    }

    public void setServerLessonId(long serverLessonId) {
        this.serverLessonId = serverLessonId;
    }

    public boolean isPublished() {
        return STATUS_PUBLISHED.equalsIgnoreCase(status);
    }
}
