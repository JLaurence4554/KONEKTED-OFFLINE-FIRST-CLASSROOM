package com.malapay.classroom.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a row in the local "class_members" table — the join table
 * between a student and a class they've joined.
 *
 * The unique composite index on (class_id, student_id) is what makes a
 * duplicate join structurally impossible at the database level, in
 * addition to the pre-check performed in the repository.
 */
@Entity(
        tableName = "class_members",
        indices = {@Index(value = {"class_id", "student_id"}, unique = true)},
        foreignKeys = {
                @ForeignKey(entity = SchoolClass.class, parentColumns = "id",
                        childColumns = "class_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = User.class, parentColumns = "id",
                        childColumns = "student_id", onDelete = ForeignKey.CASCADE)
        }
)
public class ClassMember {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "class_id")
    private long classId;

    @ColumnInfo(name = "student_id")
    private long studentId;

    @ColumnInfo(name = "joined_at")
    private long joinedAt;

    /**
     * PHASE: online sync. One of {@link com.malapay.classroom.sync.SyncStatus}.
     * A membership can legitimately exist locally before the backend knows
     * about it (e.g. joined while offline, or the join_class.php call
     * failed after the local row was already written) — SyncManager
     * retries anything not SYNCED.
     */
    @NonNull
    @ColumnInfo(name = "sync_status", defaultValue = "PENDING")
    private String syncStatus = com.malapay.classroom.sync.SyncStatus.PENDING;

    /** Epoch millis of the last successful sync, or null if never synced. */
    @ColumnInfo(name = "last_synced_at")
    private Long lastSyncedAt;

    public ClassMember(long classId, long studentId, long joinedAt) {
        this.classId = classId;
        this.studentId = studentId;
        this.joinedAt = joinedAt;
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

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }

    @NonNull
    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(@NonNull String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public Long getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Long lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
