package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.malapay.classroom.data.local.entity.Attendance;

import java.util.List;

@Dao
public interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Attendance attendance);

    @Update
    void update(Attendance attendance);

    @Query("SELECT * FROM attendance WHERE id = :id LIMIT 1")
    Attendance findById(long id);

    @Query("SELECT * FROM attendance WHERE class_id = :classId AND date = :date ORDER BY student_id ASC")
    List<Attendance> getAttendanceForClassOnDate(long classId, long date);

    @Query("SELECT * FROM attendance WHERE class_id = :classId ORDER BY date DESC")
    List<Attendance> getAttendanceForClass(long classId);

    @Query("SELECT * FROM attendance WHERE student_id = :studentId ORDER BY date DESC")
    List<Attendance> getAttendanceForStudent(long studentId);

    @Query("SELECT * FROM attendance WHERE class_id = :classId AND student_id = :studentId AND date = :date LIMIT 1")
    Attendance findByClassStudentDate(long classId, long studentId, long date);

    // ---------------------------------------------------------------
    // PHASE: online sync
    // ---------------------------------------------------------------

    @Query("SELECT * FROM attendance WHERE server_id = 0")
    List<Attendance> getUnsyncedAttendance();

    @Query("UPDATE attendance SET server_id = :serverId, sync_status = 'SYNCED', last_synced_at = :timestamp WHERE id = :id")
    void markAttendanceSynced(long id, long serverId, long timestamp);

    @Query("UPDATE attendance SET sync_status = 'FAILED' WHERE id = :id")
    void markAttendanceFailed(long id);

    @Query("SELECT * FROM attendance WHERE sync_status != 'SYNCED'")
    List<Attendance> getPendingAttendance();
}
