package com.malapay.classroom.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.UserDao;
import com.malapay.classroom.data.local.entity.User;
import com.malapay.classroom.util.PasswordUtil;

/**
 * Single entry point for all user-related data operations.
 * Runs SQLite work on {@link AppDatabase#DATABASE_WRITE_EXECUTOR} and always
 * delivers results back on the main thread via {@link Handler}, so callers
 * (Activities) can safely touch views in the callback.
 */
public class UserRepository {

    private final UserDao userDao;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public UserRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.userDao = db.userDao();
    }

    /**
     * Attempts to authenticate a user by email + plaintext password.
     * The plaintext password is hashed-and-compared inside this method and
     * never persisted or logged.
     */
    public void login(String email, String plainPassword, AuthCallback callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                User user = userDao.findByEmail(email.trim().toLowerCase());
                if (user == null || !PasswordUtil.verifyPassword(plainPassword, user.getPasswordHash())) {
                    postFailure(callback, AuthCallback.AuthFailureReason.INVALID_CREDENTIALS);
                    return;
                }
                postSuccess(callback, user);
            } catch (Exception e) {
                postFailure(callback, AuthCallback.AuthFailureReason.DATABASE_ERROR);
            }
        });
    }

    /**
     * Creates a new account. Emails are normalized to lowercase before both
     * the uniqueness check and the insert, so lookups at login time (which
     * also lowercase) always match regardless of how the person typed it.
     * Field validation (non-empty, password strength, role) is expected to
     * have already happened in the UI.
     */
    public void register(String fullName, String email, String plainPassword, String role,
                          RepositoryCallback<User> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                String normalizedEmail = email.trim().toLowerCase();
                if (userDao.emailExists(normalizedEmail)) {
                    postError(callback, "An account with this email already exists.");
                    return;
                }
                String hash = PasswordUtil.hashPassword(plainPassword);
                User user = new User(fullName.trim(), normalizedEmail, hash, role, System.currentTimeMillis());
                long id = userDao.insert(user);
                user.setId(id);
                postSuccess(callback, user);
            } catch (Exception e) {
                postError(callback, "Could not create the account. Please try again.");
            }
        });
    }

    private void postSuccess(AuthCallback callback, User user) {
        mainThreadHandler.post(() -> callback.onSuccess(user));
    }

    private void postFailure(AuthCallback callback, AuthCallback.AuthFailureReason reason) {
        mainThreadHandler.post(() -> callback.onFailure(reason));
    }

    private <T> void postSuccess(RepositoryCallback<T> callback, T result) {
        mainThreadHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(RepositoryCallback<T> callback, String message) {
        mainThreadHandler.post(() -> callback.onError(message));
    }
}
