package com.malapay.classroom.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.malapay.classroom.data.local.entity.User;

/**
 * Persists the currently logged-in user's identity so the app can
 * auto-login on relaunch and so screens can look up "who am I" without
 * re-querying the database on every navigation.
 */
public class SessionManager {

    private static final String PREF_NAME = "classroom_session";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Saves the session after a successful login. */
    public void createSession(User user) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putLong(KEY_USER_ID, user.getId());
        editor.putString(KEY_FULL_NAME, user.getFullName());
        editor.putString(KEY_EMAIL, user.getEmail());
        editor.putString(KEY_ROLE, user.getRole());
        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public String getFullName() {
        return prefs.getString(KEY_FULL_NAME, "");
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, "");
    }

    public boolean isTeacher() {
        return User.ROLE_TEACHER.equalsIgnoreCase(getRole());
    }

    public boolean isStudent() {
        return User.ROLE_STUDENT.equalsIgnoreCase(getRole());
    }

    /** Clears the session on logout. */
    public void logout() {
        prefs.edit().clear().apply();
    }
}
