package com.malapay.classroom.data.local.model;

import com.malapay.classroom.data.local.entity.Quiz;

/** Pairs a published quiz with whether the current student has already submitted it. */
public class QuizWithSubmissionStatus {

    public final Quiz quiz;
    public final boolean submitted;

    public QuizWithSubmissionStatus(Quiz quiz, boolean submitted) {
        this.quiz = quiz;
        this.submitted = submitted;
    }
}
