package com.malapay.classroom.sync.api;

/** Request body for {@code POST /api/leave_class.php} — removes a membership on the backend. */
public class LeaveClassRequest {
    public String classCode;
    public String studentEmail;

    public LeaveClassRequest(String classCode, String studentEmail) {
        this.classCode = classCode;
        this.studentEmail = studentEmail;
    }
}
