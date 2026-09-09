package com.malapay.classroom.data.local.model;

import androidx.room.Embedded;

import com.malapay.classroom.data.local.entity.SchoolClass;

/**
 * Result of a query that joins "classes" with a per-class count from
 * "class_members". Used to show the student count on each row of the
 * teacher's class list without a separate query per row.
 */
public class ClassWithStudentCount {

    @Embedded
    public SchoolClass schoolClass;

    public int studentCount;
}
