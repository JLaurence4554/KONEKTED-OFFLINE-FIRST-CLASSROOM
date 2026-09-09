package com.malapay.classroom.sync.api;

/** A class returned from GET get_student_classes.php. */
public class ServerClassData {
    public long serverId;
    public String className;
    public String subject;
    public String section;
    public String schoolYear;
    public String classCode;
    public String teacherName;
    public String teacherEmail;
}
