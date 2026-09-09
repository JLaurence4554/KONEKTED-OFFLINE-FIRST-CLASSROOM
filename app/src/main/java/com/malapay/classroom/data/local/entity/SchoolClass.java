package com.malapay.classroom.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a row in the local "classes" table.
 * Named SchoolClass (not "Class") to avoid colliding with java.lang.Class.
 *
 * class_code has a unique index — this is the structural guarantee against
 * duplicate class codes, not just an application-level check.
 */
@Entity(
        tableName = "classes",
        indices = {@Index(value = "class_code", unique = true), @Index("teacher_id")},
        foreignKeys = @ForeignKey(
                entity = User.class,
                parentColumns = "id",
                childColumns = "teacher_id",
                onDelete = ForeignKey.CASCADE
        )
)
public class SchoolClass {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "teacher_id")
    private long teacherId;

    @NonNull
    @ColumnInfo(name = "class_name")
    private String className;

    @NonNull
    @ColumnInfo(name = "subject")
    private String subject;

    @NonNull
    @ColumnInfo(name = "section")
    private String section;

    @NonNull
    @ColumnInfo(name = "school_year")
    private String schoolYear;

    @NonNull
    @ColumnInfo(name = "class_code")
    private String classCode;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    /**
     * PHASE: online sync. One of {@link com.malapay.classroom.sync.SyncStatus}.
     * Defaults to PENDING for every locally-created row — a brand new class
     * hasn't been uploaded yet. NOT NULL with a DEFAULT so the v5->v6
     * migration can backfill existing rows without touching app code that
     * doesn't know about sync yet.
     */
    @NonNull
    @ColumnInfo(name = "sync_status", defaultValue = "PENDING")
    private String syncStatus = com.malapay.classroom.sync.SyncStatus.PENDING;

    /**
     * The backend's own primary key for this class, once synced. Null until
     * the first successful {@code sync_class.php} call. Boxed Long (not
     * primitive) specifically so "no server id yet" is representable.
     */
    @ColumnInfo(name = "server_id")
    private Long serverId;

    /** Epoch millis of the last successful sync, or null if never synced. */
    @ColumnInfo(name = "last_synced_at")
    private Long lastSyncedAt;

    public SchoolClass(long teacherId, @NonNull String className, @NonNull String subject,
                        @NonNull String section, @NonNull String schoolYear,
                        @NonNull String classCode, long createdAt) {
        this.teacherId = teacherId;
        this.className = className;
        this.subject = subject;
        this.section = section;
        this.schoolYear = schoolYear;
        this.classCode = classCode;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(long teacherId) {
        this.teacherId = teacherId;
    }

    @NonNull
    public String getClassName() {
        return className;
    }

    public void setClassName(@NonNull String className) {
        this.className = className;
    }

    @NonNull
    public String getSubject() {
        return subject;
    }

    public void setSubject(@NonNull String subject) {
        this.subject = subject;
    }

    @NonNull
    public String getSection() {
        return section;
    }

    public void setSection(@NonNull String section) {
        this.section = section;
    }

    @NonNull
    public String getSchoolYear() {
        return schoolYear;
    }

    public void setSchoolYear(@NonNull String schoolYear) {
        this.schoolYear = schoolYear;
    }

    @NonNull
    public String getClassCode() {
        return classCode;
    }

    public void setClassCode(@NonNull String classCode) {
        this.classCode = classCode;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @NonNull
    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(@NonNull String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public Long getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Long lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
