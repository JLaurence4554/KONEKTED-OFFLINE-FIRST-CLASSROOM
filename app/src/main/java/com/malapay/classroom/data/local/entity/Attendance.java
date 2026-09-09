package com.malapay.classroom.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a row in the local "attendance" table. Tracks student
 * attendance per class per date. Minimal implementation - only created
 * because the backend schema already includes this table.
 */
@Entity(
        tableName = "attendance",
        indices = {
                @Index(value = {"class_id", "student_id", "date"}, unique = true),
                @Index("class_id"),
                @Index("student_id")
        },
        foreignKeys = {
                @ForeignKey(entity = SchoolClass.class, parentColumns = "id",
                        childColumns = "class_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = User.class, parentColumns = "id",
                        childColumns = "student_id", onDelete = ForeignKey.CASCADE)
        }
)
public class Attendance {

    public static final String STATUS_PRESENT = "Present";
    public static final String STATUS_ABSENT = "Absent";
    public static final String STATUS_LATE = "Late";
    public static final String STATUS_EXCUSED = "Excused";

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "class_id")
    private long classId;

    @ColumnInfo(name = "student_id")
    private long studentId;

    @NonNull
    @ColumnInfo(name = "status")
    private String status;

    @ColumnInfo(name = "date")
    private long date;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    /** Server-side ID for syncing. 0 = not yet synced. */
    @ColumnInfo(name = "server_id", defaultValue = "0")
    private long serverId;

    @NonNull
    @ColumnInfo(name = "sync_status", defaultValue = "PENDING")
    private String syncStatus = com.malapay.classroom.sync.SyncStatus.PENDING;

    @ColumnInfo(name = "last_synced_at")
    private Long lastSyncedAt;

    public Attendance(long classId, long studentId, @NonNull String status, long date) {
        this.classId = classId;
        this.studentId = studentId;
        this.status = status;
        this.date = date;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getClassId() { return classId; }
    public void setClassId(long classId) { this.classId = classId; }
    public long getStudentId() { return studentId; }
    public void setStudentId(long studentId) { this.studentId = studentId; }
    @NonNull
    public String getStatus() { return status; }
    public void setStatus(@NonNull String status) { this.status = status; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public long getServerId() { return serverId; }
    public void setServerId(long serverId) { this.serverId = serverId; }
    @NonNull
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(@NonNull String syncStatus) { this.syncStatus = syncStatus; }
    public Long getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Long lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
