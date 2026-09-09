package com.malapay.classroom.data.repository;

/**
 * Generic result callback for asynchronous repository operations.
 * {@code onError} carries a user-displayable message rather than an
 * exception, so calling UI code can show it directly.
 */
public interface RepositoryCallback<T> {
    void onSuccess(T result);
    void onError(String message);
}
