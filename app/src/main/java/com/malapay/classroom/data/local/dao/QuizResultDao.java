package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.malapay.classroom.data.local.entity.QuizResult;
import com.malapay.classroom.data.local.model.QuizResultWithStudentName;

import java.util.List;

@Dao
public interface QuizResultDao {

    /** Aborts on unique-constraint conflict (student already submitted this quiz). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(QuizResult result);

    @Query("SELECT * FROM quiz_results WHERE quiz_id = :quizId AND student_id = :studentId LIMIT 1")
    QuizResult findByQuizAndStudent(long quizId, long studentId);

    @Query("SELECT EXISTS(SELECT 1 FROM quiz_results WHERE quiz_id = :quizId AND student_id = :studentId)")
    boolean hasStudentSubmitted(long quizId, long studentId);

    @Query("SELECT r.*, u.fullname AS studentName FROM quiz_results r " +
            "INNER JOIN users u ON r.student_id = u.id " +
            "WHERE r.quiz_id = :quizId ORDER BY r.submitted_at DESC")
    List<QuizResultWithStudentName> getResultsForQuiz(long quizId);

    @Query("SELECT COUNT(*) FROM quiz_results WHERE quiz_id = :quizId")
    int countResultsForQuiz(long quizId);

    // ---------------------------------------------------------------
    // PHASE: online sync
    // ---------------------------------------------------------------

    @Query("SELECT * FROM quiz_results WHERE server_id = 0")
    List<QuizResult> getUnsyncedResults();

    @Query("UPDATE quiz_results SET server_id = :serverId, sync_status = 'SYNCED', last_synced_at = :timestamp WHERE id = :id")
    void markResultSynced(long id, long serverId, long timestamp);

    @Query("UPDATE quiz_results SET sync_status = 'FAILED' WHERE id = :id")
    void markResultFailed(long id);

    @Query("SELECT * FROM quiz_results WHERE sync_status != 'SYNCED'")
    List<QuizResult> getPendingResults();

    @Query("UPDATE quiz_results SET graded_score = :gradedScore WHERE id = :id")
    void updateGradedScore(long id, int gradedScore);
}
