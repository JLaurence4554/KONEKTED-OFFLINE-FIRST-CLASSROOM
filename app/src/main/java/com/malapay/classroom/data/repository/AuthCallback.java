package com.malapay.classroom.data.repository;

import com.malapay.classroom.data.local.entity.User;

/**
 * Delivers the result of an authentication attempt back to the UI thread.
 * Exactly one of the two methods is invoked.
 */
public interface AuthCallback {

    /** Credentials matched an existing user. */
    void onSuccess(User user);

    /** Login failed. {@code reason} distinguishes bad credentials from a system error. */
    void onFailure(AuthFailureReason reason);

    enum AuthFailureReason {
        INVALID_CREDENTIALS,
        DATABASE_ERROR
    }
}
