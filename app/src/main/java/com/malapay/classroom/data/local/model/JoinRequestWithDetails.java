package com.malapay.classroom.data.local.model;

/**
 * Flat projection for displaying join requests with the student's name
 * and the class name, avoiding N+1 lookups in the adapter.
 */
public class JoinRequestWithDetails {
    public long requestId;
    public long classId;
    public long studentId;
    public String studentName;
    public String className;
    public String classCode;
    public String studentEmail;
    public String status;
    public long requestedAt;
}
