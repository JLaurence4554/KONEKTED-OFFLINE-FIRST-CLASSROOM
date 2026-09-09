package com.malapay.classroom.sync.api;

/** Request body for POST /api/sync_question.php - uploads a question to the server. */
public class SyncQuestionRequest {
    public long quizId;
    public String question;
    public String questionType;
    public String choiceA;
    public String choiceB;
    public String choiceC;
    public String choiceD;
    public String correctAnswer;
    public String answerText;
    public int orderIndex;
    public String teacherEmail;

    public SyncQuestionRequest(long quizId, String question, String questionType,
                                String choiceA, String choiceB, String choiceC, String choiceD,
                                String correctAnswer, String answerText,
                                int orderIndex, String teacherEmail) {
        this.quizId = quizId;
        this.question = question;
        this.questionType = questionType;
        this.choiceA = choiceA;
        this.choiceB = choiceB;
        this.choiceC = choiceC;
        this.choiceD = choiceD;
        this.correctAnswer = correctAnswer;
        this.answerText = answerText;
        this.orderIndex = orderIndex;
        this.teacherEmail = teacherEmail;
    }
}
