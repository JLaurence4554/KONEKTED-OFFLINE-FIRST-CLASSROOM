package com.malapay.classroom.sync.api;

/** Request body for POST /api/sync_quiz.php - uploads a locally-created quiz to the server. */
public class SyncQuizRequest {
    public long lessonId;
    public String title;
    public String instructions;
    public Integer timeLimitMinutes;
    public int passingScore;
    public String status;
    public String teacherEmail;

    public SyncQuizRequest(long lessonId, String title, String instructions,
                            Integer timeLimitMinutes, int passingScore, String status,
                            String teacherEmail) {
        this.lessonId = lessonId;
        this.title = title;
        this.instructions = instructions;
        this.timeLimitMinutes = timeLimitMinutes;
        this.passingScore = passingScore;
        this.status = status;
        this.teacherEmail = teacherEmail;
    }
}
