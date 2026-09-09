package com.malapay.classroom.data.local.model;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

import com.malapay.classroom.data.local.entity.QuizResult;

/** Result of a query joining "quiz_results" with "users", for the teacher's results screen. */
public class QuizResultWithStudentName {

    @Embedded
    public QuizResult result;

    @ColumnInfo(name = "studentName")
    public String studentName;
}
