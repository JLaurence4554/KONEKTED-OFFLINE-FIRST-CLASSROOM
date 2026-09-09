package com.malapay.classroom.data.local.model;

import androidx.room.Embedded;

import com.malapay.classroom.data.local.entity.Quiz;

/** Result of a query joining "quizzes" with a per-quiz count from "questions". */
public class QuizWithQuestionCount {

    @Embedded
    public Quiz quiz;

    public int questionCount;
}
