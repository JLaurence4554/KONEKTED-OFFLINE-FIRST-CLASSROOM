package com.malapay.classroom.data.local.model;

import androidx.room.ColumnInfo;

/**
 * Per-quiz score history for a student. Each row represents one quiz submission,
 * containing the quiz title, score, and submission time. Used for the student's
 * score-over-time line chart and quiz history list.
 */
public class StudentQuizHistory {

    @ColumnInfo(name = "quizTitle")
    public String quizTitle;

    @ColumnInfo(name = "score")
    public int score;

    @ColumnInfo(name = "correctCount")
    public int correctCount;

    @ColumnInfo(name = "totalQuestions")
    public int totalQuestions;

    @ColumnInfo(name = "submittedAt")
    public long submittedAt;

    public String getQuizTitle() { return quizTitle; }
    public int getScore() { return score; }
    public int getCorrectCount() { return correctCount; }
    public int getTotalQuestions() { return totalQuestions; }
    public long getSubmittedAt() { return submittedAt; }
}
