package com.malapay.classroom.sync.api;

/** Request body for POST /api/sync_quiz_result.php - uploads a quiz result to the server. */
public class SyncQuizResultRequest {
    public long quizId;
    public String studentEmail;
    public int score;
    public int correctCount;
    public int totalQuestions;

    public SyncQuizResultRequest(long quizId, String studentEmail, int score,
                                   int correctCount, int totalQuestions) {
        this.quizId = quizId;
        this.studentEmail = studentEmail;
        this.score = score;
        this.correctCount = correctCount;
        this.totalQuestions = totalQuestions;
    }
}
