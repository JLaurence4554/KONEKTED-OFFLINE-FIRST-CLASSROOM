package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.malapay.classroom.data.local.entity.JoinRequest;
import com.malapay.classroom.data.local.model.JoinRequestWithDetails;

import java.util.List;

@Dao
public interface JoinRequestDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(JoinRequest joinRequest);

    @Query("SELECT EXISTS(SELECT 1 FROM join_requests WHERE class_id = :classId " +
            "AND student_id = :studentId AND status = 'PENDING')")
    boolean hasPendingRequest(long classId, long studentId);

    @Query("SELECT EXISTS(SELECT 1 FROM join_requests WHERE class_id = :classId " +
            "AND student_id = :studentId AND status = 'APPROVED')")
    boolean hasApprovedRequest(long classId, long studentId);

    /** All pending requests across every class owned by this teacher. */
    @Query("SELECT jr.* FROM join_requests jr " +
            "INNER JOIN classes c ON jr.class_id = c.id " +
            "WHERE c.teacher_id = :teacherId AND jr.status = 'PENDING' " +
            "ORDER BY jr.requested_at DESC")
    List<JoinRequest> getPendingRequestsForTeacher(long teacherId);

    /** Pending requests with student name and class name for display. */
    @Query("SELECT jr.id AS requestId, jr.class_id AS classId, jr.student_id AS studentId, " +
            "u.fullname AS studentName, u.email AS studentEmail, c.class_name AS className, c.class_code AS classCode, " +
            "jr.status AS status, jr.requested_at AS requestedAt " +
            "FROM join_requests jr " +
            "INNER JOIN classes c ON jr.class_id = c.id " +
            "INNER JOIN users u ON jr.student_id = u.id " +
            "WHERE c.teacher_id = :teacherId AND jr.status = 'PENDING' " +
            "ORDER BY jr.requested_at DESC")
    List<JoinRequestWithDetails> getPendingRequestsWithDetails(long teacherId);

    /** Count of pending requests for the notification badge. */
    @Query("SELECT COUNT(*) FROM join_requests jr " +
            "INNER JOIN classes c ON jr.class_id = c.id " +
            "WHERE c.teacher_id = :teacherId AND jr.status = 'PENDING'")
    int countPendingForTeacher(long teacherId);

    @Query("UPDATE join_requests SET status = 'APPROVED', resolved_at = :resolvedAt " +
            "WHERE id = :requestId")
    void approveRequest(long requestId, long resolvedAt);

    @Query("UPDATE join_requests SET status = 'DENIED', resolved_at = :resolvedAt " +
            "WHERE id = :requestId")
    void denyRequest(long requestId, long resolvedAt);

    @Query("SELECT * FROM join_requests WHERE id = :requestId LIMIT 1")
    JoinRequest findById(long requestId);

    @Query("SELECT * FROM join_requests WHERE student_id = :studentId " +
            "ORDER BY requested_at DESC")
    List<JoinRequest> getRequestsForStudent(long studentId);

    /** Removes all join requests for a student in a class (used when leaving a class). */
    @Query("DELETE FROM join_requests WHERE class_id = :classId AND student_id = :studentId")
    void deleteByClassAndStudent(long classId, long studentId);

    // Sync support
    @Query("SELECT * FROM join_requests WHERE sync_status != 'SYNCED'")
    List<JoinRequest> getPendingSyncRequests();

    @Query("UPDATE join_requests SET sync_status = 'SYNCED', last_synced_at = :timestamp " +
            "WHERE id = :id")
    void markSynced(long id, long timestamp);

    @Query("UPDATE join_requests SET sync_status = 'FAILED' WHERE id = :id")
    void markFailed(long id);

    @Query("SELECT * FROM join_requests WHERE class_id = :classId AND student_id = :studentId LIMIT 1")
    JoinRequest findByClassAndStudent(long classId, long studentId);
}
