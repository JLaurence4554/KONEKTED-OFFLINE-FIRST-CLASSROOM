package com.malapay.classroom.data.local.model;

import androidx.room.ColumnInfo;

/** Result of a query joining "class_members" with "users", for the roster screen. */
public class StudentInClass {

    public long id;

    @ColumnInfo(name = "fullName")
    public String fullName;

    @ColumnInfo(name = "email")
    public String email;
}
