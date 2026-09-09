package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.malapay.classroom.data.local.entity.Question;

import java.util.List;

@Dao
public interface QuestionDao {

    @Insert
    long insert(Question question);

    @Update
    void update(Question question);

    @Delete
    void delete(Question question);

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    Question findById(long id);

    @Query("SELECT * FROM questions WHERE quiz_id = :quizId ORDER BY order_index ASC")
    List<Question> getQuestionsForQuiz(long quizId);

    @Query("SELECT COUNT(*) FROM questions WHERE quiz_id = :quizId")
    int countQuestionsForQuiz(long quizId);

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM questions WHERE quiz_id = :quizId")
    int getMaxOrderIndex(long quizId);

    /** Updates every question's order_index in one transaction, for drag/move reordering. */
    @Transaction
    default void reorder(List<Question> questionsInNewOrder) {
        for (int i = 0; i < questionsInNewOrder.size(); i++) {
            Question q = questionsInNewOrder.get(i);
            q.setOrderIndex(i);
            update(q);
        }
    }

    // ---------------------------------------------------------------
    // PHASE: online sync
    // ---------------------------------------------------------------

    @Query("SELECT * FROM questions WHERE server_id = :serverId LIMIT 1")
    Question findByServerId(long serverId);

    @Query("SELECT * FROM questions WHERE quiz_id = :quizId AND server_id = 0")
    List<Question> getUnsyncedQuestionsForQuiz(long quizId);

    @Query("SELECT * FROM questions WHERE server_id = 0")
    List<Question> getUnsyncedQuestions();

    @Query("UPDATE questions SET server_id = :serverId, sync_status = 'SYNCED', last_synced_at = :timestamp WHERE id = :id")
    void markQuestionSynced(long id, long serverId, long timestamp);

    @Query("UPDATE questions SET sync_status = 'FAILED' WHERE id = :id")
    void markQuestionFailed(long id);

    @Query("SELECT * FROM questions WHERE sync_status != 'SYNCED'")
    List<Question> getPendingQuestions();
}
