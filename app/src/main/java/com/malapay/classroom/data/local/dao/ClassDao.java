package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.data.local.model.ClassWithStudentCount;

import java.util.List;

@Dao
public interface ClassDao {

    /** Aborts on unique-constraint conflict (duplicate class_code). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(SchoolClass schoolClass);

    @Update
    void update(SchoolClass schoolClass);

    @Delete
    void delete(SchoolClass schoolClass);

    @Query("SELECT EXISTS(SELECT 1 FROM classes WHERE class_code = :code)")
    boolean classCodeExists(String code);

    @Query("SELECT * FROM classes WHERE class_code = :code LIMIT 1")
    SchoolClass findByCode(String code);

    @Query("SELECT * FROM classes WHERE id = :id LIMIT 1")
    SchoolClass findById(long id);

    @Query("SELECT COUNT(*) FROM classes WHERE teacher_id = :teacherId")
    int countClassesByTeacher(long teacherId);

    @Query("SELECT c.*, (SELECT COUNT(*) FROM class_members cm WHERE cm.class_id = c.id) AS studentCount " +
            "FROM classes c WHERE c.teacher_id = :teacherId ORDER BY c.created_at DESC")
    List<ClassWithStudentCount> getClassesForTeacher(long teacherId);

    @Query("SELECT c.*, (SELECT COUNT(*) FROM class_members cm WHERE cm.class_id = c.id) AS studentCount " +
            "FROM classes c WHERE c.teacher_id = :teacherId AND (" +
            "c.class_name LIKE '%' || :query || '%' OR " +
            "c.subject LIKE '%' || :query || '%' OR " +
            "c.section LIKE '%' || :query || '%' OR " +
            "c.class_code LIKE '%' || :query || '%') " +
            "ORDER BY c.created_at DESC")
    List<ClassWithStudentCount> searchClassesForTeacher(long teacherId, String query);

    // ---------------------------------------------------------------
    // PHASE: online sync
    // ---------------------------------------------------------------

    /** Every class not yet confirmed synced (PENDING or FAILED) — what SyncManager needs to (re)upload. */
    @Query("SELECT * FROM classes WHERE sync_status != 'SYNCED'")
    List<SchoolClass> getPendingClasses();

    @Query("UPDATE classes SET sync_status = 'SYNCED', server_id = :serverId, last_synced_at = :timestamp WHERE id = :id")
    void markClassSynced(long id, long serverId, long timestamp);

    @Query("UPDATE classes SET sync_status = 'FAILED' WHERE id = :id")
    void markClassFailed(long id);

    @Query("SELECT * FROM classes WHERE server_id = :serverId LIMIT 1")
    SchoolClass findByServerId(long serverId);
}
