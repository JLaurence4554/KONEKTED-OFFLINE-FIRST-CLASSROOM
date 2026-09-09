package com.malapay.classroom.sync;

/**
 * The three states a locally-created row (a class, a membership) can be in
 * with respect to the backend. Kept as plain String constants — same
 * pattern as {@link com.malapay.classroom.data.local.entity.User}'s
 * ROLE_TEACHER/ROLE_STUDENT — rather than a Room TypeConverter-backed enum,
 * to keep the migration a simple TEXT column with no converter wiring.
 *
 * PENDING — created locally, not yet uploaded (or a previous upload attempt
 *           hasn't been retried yet). This is the default for every new row.
 * SYNCED  — the backend has this row and local + server agree.
 * FAILED  — an upload was attempted and the server rejected it or the
 *           request errored. Still eligible for retry (SyncManager treats
 *           PENDING and FAILED the same way: "needs sync").
 */
public final class SyncStatus {

    public static final String PENDING = "PENDING";
    public static final String SYNCED = "SYNCED";
    public static final String FAILED = "FAILED";

    private SyncStatus() {
        // no instances
    }
}
