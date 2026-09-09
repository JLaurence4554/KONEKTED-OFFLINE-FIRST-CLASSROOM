package com.malapay.classroom.util;

import android.text.TextUtils;
import android.util.Patterns;

/**
 * Reusable, stateless validation rules shared across forms
 * (Login now; Class creation and future forms later).
 */
public final class ValidationUtil {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private ValidationUtil() {
        // no instances
    }

    public static boolean isEmailValid(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    public static boolean isPasswordValid(String password) {
        return !TextUtils.isEmpty(password) && password.length() >= MIN_PASSWORD_LENGTH;
    }

    public static boolean isNotEmpty(String value) {
        return !TextUtils.isEmpty(value) && !value.trim().isEmpty();
    }
}
