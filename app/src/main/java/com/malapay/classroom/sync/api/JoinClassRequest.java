package com.malapay.classroom.sync.api;

/** Request body for {@code POST /api/join_class.php} — records a membership on the backend. */
public class JoinClassRequest {
    public String classCode;
    public String studentName;
    public String studentEmail;

    public JoinClassRequest(String classCode, String studentName, String studentEmail) {
        this.classCode = classCode;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
    }
}
