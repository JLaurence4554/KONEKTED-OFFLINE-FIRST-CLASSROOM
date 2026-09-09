package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.data.local.model.QuizWithQuestionCount;

import java.util.List;

@Dao
public interface QuizDao {

    @Insert
    long insert(Quiz quiz);

    @Update
    void update(Quiz quiz);

    @Delete
    void delete(Quiz quiz);

    @Query("SELECT * FROM quizzes WHERE id = :id LIMIT 1")
    Quiz findById(long id);

    @Query("SELECT q.*, (SELECT COUNT(*) FROM questions ques WHERE ques.quiz_id = q.id) AS questionCount " +
            "FROM quizzes q WHERE q.lesson_id = :lessonId ORDER BY q.created_at DESC")
    List<QuizWithQuestionCount> getQuizzesForLesson(long lessonId);

    @Query("SELECT * FROM quizzes WHERE lesson_id = :lessonId AND status = 'Published' ORDER BY created_at DESC")
    List<Quiz> getPublishedQuizzesForLesson(long lessonId);

    @Query("SELECT COUNT(*) FROM quizzes WHERE lesson_id = :lessonId")
    int countQuizzesForLesson(long lessonId);

    // ---------------------------------------------------------------
    // PHASE: online sync
    // ---------------------------------------------------------------

    @Query("SELECT * FROM quizzes WHERE server_id = :serverId LIMIT 1")
    Quiz findByServerId(long serverId);

    @Query("SELECT * FROM quizzes WHERE server_id = 0")
    List<Quiz> getUnsyncedQuizzes();

    @Query("UPDATE quizzes SET server_id = :serverId, sync_status = 'SYNCED', last_synced_at = :timestamp WHERE id = :id")
    void markQuizSynced(long id, long serverId, long timestamp);

    @Query("UPDATE quizzes SET sync_status = 'FAILED' WHERE id = :id")
    void markQuizFailed(long id);

    @Query("SELECT * FROM quizzes WHERE sync_status != 'SYNCED'")
    List<Quiz> getPendingQuizzes();
}
