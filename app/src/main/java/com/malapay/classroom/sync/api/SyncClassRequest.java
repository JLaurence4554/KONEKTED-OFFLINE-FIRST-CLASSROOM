package com.malapay.classroom.sync.api;

/** Request body for {@code POST /api/sync_class.php} — uploads a locally-created class. */
public class SyncClassRequest {
    public String className;
    public String subject;
    public String section;
    public String schoolYear;
    public String classCode;
    public String teacherName;
    public String teacherEmail;

    public SyncClassRequest(String className, String subject, String section, String schoolYear,
                             String classCode, String teacherName, String teacherEmail) {
        this.className = className;
        this.subject = subject;
        this.section = section;
        this.schoolYear = schoolYear;
        this.classCode = classCode;
        this.teacherName = teacherName;
        this.teacherEmail = teacherEmail;
    }
}
