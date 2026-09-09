package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.malapay.classroom.data.local.entity.Lesson;

import java.util.List;

@Dao
public interface LessonDao {

    @Insert
    long insert(Lesson lesson);

    @Update
    void update(Lesson lesson);

    @Delete
    void delete(Lesson lesson);

    @Query("SELECT * FROM lessons WHERE id = :id LIMIT 1")
    Lesson findById(long id);

    @Query("SELECT * FROM lessons WHERE class_id = :classId ORDER BY created_at DESC")
    List<Lesson> getLessonsForClass(long classId);

    @Query("SELECT * FROM lessons WHERE server_id = :serverId LIMIT 1")
    Lesson findByServerId(long serverId);

    @Query("SELECT * FROM lessons WHERE class_id = :classId AND (" +
            "title LIKE '%' || :query || '%' OR " +
            "body LIKE '%' || :query || '%' OR " +
            "subject LIKE '%' || :query || '%') " +
            "ORDER BY created_at DESC")
    List<Lesson> searchLessonsForClass(long classId, String query);

    // ---------------------------------------------------------------
    // PHASE: online sync
    // ---------------------------------------------------------------

    /** Lessons not yet synced to the server (server_id == 0). */
    @Query("SELECT * FROM lessons WHERE server_id = 0")
    List<Lesson> getUnsyncedLessons();

    @Query("UPDATE lessons SET server_id = :serverId WHERE id = :id")
    void markLessonSynced(long id, long serverId);
}
