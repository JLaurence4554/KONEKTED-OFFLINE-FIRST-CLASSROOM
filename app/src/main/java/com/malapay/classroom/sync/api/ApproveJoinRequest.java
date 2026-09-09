package com.malapay.classroom.sync.api;

import com.google.gson.annotations.SerializedName;

public class ApproveJoinRequest {
    @SerializedName("classCode")
    private final String classCode;

    @SerializedName("studentEmail")
    private final String studentEmail;

    public ApproveJoinRequest(String classCode, String studentEmail) {
        this.classCode = classCode;
        this.studentEmail = studentEmail;
    }
}
