package com.malapay.classroom.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a student's request to join a class.
 * The teacher must approve or deny this before the student becomes a class member.
 */
@Entity(
        tableName = "join_requests",
        indices = {
                @Index(value = {"class_id", "student_id"}, unique = true),
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
public class JoinRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_DENIED = "DENIED";

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "class_id")
    private long classId;

    @ColumnInfo(name = "student_id")
    private long studentId;

    @NonNull
    @ColumnInfo(name = "status")
    private String status = STATUS_PENDING;

    @ColumnInfo(name = "requested_at")
    private long requestedAt;

    @ColumnInfo(name = "resolved_at")
    private Long resolvedAt;

    @NonNull
    @ColumnInfo(name = "sync_status", defaultValue = "PENDING")
    private String syncStatus = "PENDING";

    @ColumnInfo(name = "server_id")
    private Long serverId;

    @ColumnInfo(name = "last_synced_at")
    private Long lastSyncedAt;

    public JoinRequest(long classId, long studentId, long requestedAt) {
        this.classId = classId;
        this.studentId = studentId;
        this.requestedAt = requestedAt;
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

    public long getRequestedAt() { return requestedAt; }
    public void setRequestedAt(long requestedAt) { this.requestedAt = requestedAt; }

    @Nullable
    public Long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(@Nullable Long resolvedAt) { this.resolvedAt = resolvedAt; }

    @NonNull
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(@NonNull String syncStatus) { this.syncStatus = syncStatus; }

    @Nullable
    public Long getServerId() { return serverId; }
    public void setServerId(@Nullable Long serverId) { this.serverId = serverId; }

    @Nullable
    public Long getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(@Nullable Long lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
