package com.malapay.classroom.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a row in the local "quiz_results" table: one student's
 * completed attempt at one quiz. A unique index on (quiz_id, student_id)
 * enforces one attempt per student per quiz at the database level.
 */
@Entity(
        tableName = "quiz_results",
        indices = {@Index(value = {"quiz_id", "student_id"}, unique = true)},
        foreignKeys = {
                @ForeignKey(entity = Quiz.class, parentColumns = "id",
                        childColumns = "quiz_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = User.class, parentColumns = "id",
                        childColumns = "student_id", onDelete = ForeignKey.CASCADE)
        }
)
public class QuizResult {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "quiz_id")
    private long quizId;

    @ColumnInfo(name = "student_id")
    private long studentId;

    /** Percentage score, 0-100. */
    @ColumnInfo(name = "score")
    private int score;

    @ColumnInfo(name = "correct_count")
    private int correctCount;

    @ColumnInfo(name = "total_questions")
    private int totalQuestions;

    @ColumnInfo(name = "submitted_at")
    private long submittedAt;

    /** Server-side ID for syncing with the backend. 0 = not yet synced. */
    @ColumnInfo(name = "server_id", defaultValue = "0")
    private long serverId;

    @NonNull
    @ColumnInfo(name = "sync_status", defaultValue = "PENDING")
    private String syncStatus = com.malapay.classroom.sync.SyncStatus.PENDING;

    @ColumnInfo(name = "last_synced_at")
    private Long lastSyncedAt;

    /**
     * JSON string storing essay/identification answers for this submission.
     * Format: {"questionId":"answer text", ...}
     * Only populated when the quiz contains essay or identification questions.
     */
    @Nullable
    @ColumnInfo(name = "essay_answers")
    private String essayAnswers;

    /**
     * Teacher-graded score override (0-100). Null means not yet graded by teacher.
     * Only relevant for quizzes containing essay questions.
     */
    @Nullable
    @ColumnInfo(name = "graded_score")
    private Integer gradedScore;

    public QuizResult(long quizId, long studentId, int score, int correctCount,
                       int totalQuestions, long submittedAt) {
        this.quizId = quizId;
        this.studentId = studentId;
        this.score = score;
        this.correctCount = correctCount;
        this.totalQuestions = totalQuestions;
        this.submittedAt = submittedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getQuizId() {
        return quizId;
    }

    public void setQuizId(long quizId) {
        this.quizId = quizId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(long submittedAt) {
        this.submittedAt = submittedAt;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    @NonNull
    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(@NonNull String syncStatus) {
        this.syncStatus = syncStatus;
    }

    @Nullable
    public Long getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(@Nullable Long lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    @Nullable
    public String getEssayAnswers() { return essayAnswers; }
    public void setEssayAnswers(@Nullable String essayAnswers) { this.essayAnswers = essayAnswers; }

    @Nullable
    public Integer getGradedScore() { return gradedScore; }
    public void setGradedScore(@Nullable Integer gradedScore) { this.gradedScore = gradedScore; }
}
