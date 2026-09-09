package com.malapay.classroom.sync.api;

/** Request body for POST sync_join_request.php — student asks to join a class. */
public class SyncJoinRequest {
    public final String classCode;
    public final String studentName;
    public final String studentEmail;

    public SyncJoinRequest(String classCode, String studentName, String studentEmail) {
        this.classCode = classCode;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
    }
}
