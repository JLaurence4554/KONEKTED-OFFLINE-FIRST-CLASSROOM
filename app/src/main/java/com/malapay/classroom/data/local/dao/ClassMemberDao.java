package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.malapay.classroom.data.local.entity.ClassMember;
import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.data.local.model.StudentInClass;

import java.util.List;

@Dao
public interface ClassMemberDao {

    /** Aborts on unique-constraint conflict (duplicate class_id + student_id). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(ClassMember classMember);

    @Query("SELECT EXISTS(SELECT 1 FROM class_members WHERE class_id = :classId AND student_id = :studentId)")
    boolean isStudentInClass(long classId, long studentId);

    @Query("SELECT COUNT(*) FROM class_members WHERE class_id = :classId")
    int countStudentsInClass(long classId);

    /** Distinct student count across every class owned by this teacher (a student in 2 classes counts once). */
    @Query("SELECT COUNT(DISTINCT cm.student_id) FROM class_members cm " +
            "INNER JOIN classes c ON cm.class_id = c.id WHERE c.teacher_id = :teacherId")
    int countDistinctStudentsForTeacher(long teacherId);

    @Query("SELECT COUNT(*) FROM class_members WHERE student_id = :studentId")
    int countJoinedClassesForStudent(long studentId);

    @Query("SELECT u.id AS id, u.fullname AS fullName, u.email AS email FROM users u " +
            "INNER JOIN class_members cm ON u.id = cm.student_id " +
            "WHERE cm.class_id = :classId ORDER BY u.fullname ASC")
    List<StudentInClass> getStudentsInClass(long classId);

    @Query("SELECT c.* FROM classes c INNER JOIN class_members cm ON c.id = cm.class_id " +
            "WHERE cm.student_id = :studentId ORDER BY cm.joined_at DESC")
    List<SchoolClass> getJoinedClasses(long studentId);

    @Query("SELECT * FROM class_members WHERE id = :id LIMIT 1")
    ClassMember findById(long id);

    /** Removes a student's membership from a class. */
    @Query("DELETE FROM class_members WHERE class_id = :classId AND student_id = :studentId")
    void deleteByClassAndStudent(long classId, long studentId);

    /** All class IDs a student currently belongs to (for server reconciliation). */
    @Query("SELECT class_id FROM class_members WHERE student_id = :studentId")
    List<Long> getClassIdsForStudent(long studentId);

    // ---------------------------------------------------------------
    // PHASE: online sync
    // ---------------------------------------------------------------

    /** Every membership not yet confirmed synced (PENDING or FAILED) — what SyncManager needs to (re)upload. */
    @Query("SELECT * FROM class_members WHERE sync_status != 'SYNCED'")
    List<ClassMember> getPendingMemberships();

    @Query("UPDATE class_members SET sync_status = 'SYNCED', last_synced_at = :timestamp WHERE id = :id")
    void markMembershipSynced(long id, long timestamp);

    @Query("UPDATE class_members SET sync_status = 'FAILED' WHERE id = :id")
    void markMembershipFailed(long id);
}
