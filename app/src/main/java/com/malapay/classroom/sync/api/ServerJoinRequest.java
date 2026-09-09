package com.malapay.classroom.sync.api;

/** A join request returned from GET get_join_requests.php. */
public class ServerJoinRequest {
    public long requestId;
    public long classId;
    public long studentId;
    public String studentName;
    public String studentEmail;
    public String className;
    public String classCode;
    public String status;
    public long requestedAt;
}
