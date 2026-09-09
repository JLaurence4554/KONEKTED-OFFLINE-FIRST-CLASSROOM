package com.malapay.classroom.sync.api;

/**
 * Every backend endpoint (see backend/api/*.php) replies with the same
 * shape: {@code {"success": bool, "message": string, "data": ...}}. This
 * generic wrapper mirrors that so Retrofit/Gson can deserialize any
 * endpoint's response the same way, with {@code data}'s type varying per
 * call (see {@link ApiService}).
 */
public class ApiEnvelope<T> {
    public boolean success;
    public String message;
    public T data;
}
