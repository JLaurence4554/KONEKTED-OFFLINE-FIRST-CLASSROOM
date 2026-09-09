package com.malapay.classroom.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.ClassDao;
import com.malapay.classroom.data.local.dao.ClassMemberDao;
import com.malapay.classroom.data.local.entity.ClassMember;
import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.data.repository.RepositoryCallback;

import java.util.List;

/**
 * UI-facing summary of the app's sync state, for the small status badge on
 * each dashboard. This class is read-only: it never writes to Room and
 * never talks to the API, it only asks {@link NetworkMonitor} whether we're
 * online and asks the existing DAOs which rows SyncManager still considers
 * PENDING or FAILED. SyncManager's own upload logic is untouched.
 *
 * Aggregation rule (simplest thing that matches the four states in the
 * design spec):
 *   - any FAILED row                       -> ERROR
 *   - no pending rows                      -> SYNCED
 *   - pending rows exist and we're offline -> OFFLINE
 *   - pending rows exist and we're online  -> PENDING (SyncManager will
 *                                              catch up on the next
 *                                              opportunistic sync call)
 */
public final class SyncStatusPresenter {

    /** One of the four dashboard sync states. Carries its own display text/color resource ids. */
    public enum State {
        SYNCED(R.string.sync_status_synced, R.color.status_synced, R.drawable.bg_badge_synced),
        PENDING(R.string.sync_status_pending, R.color.status_pending, R.drawable.bg_badge_pending),
        OFFLINE(R.string.sync_status_offline, R.color.status_offline, R.drawable.bg_badge_offline),
        ERROR(R.string.sync_status_error, R.color.status_error, R.drawable.bg_badge_error_sync);

        public final int textRes;
        public final int colorRes;
        public final int backgroundRes;

        State(int textRes, int colorRes, int backgroundRes) {
            this.textRes = textRes;
            this.colorRes = colorRes;
            this.backgroundRes = backgroundRes;
        }
    }

    private final Context appContext;
    private final ClassDao classDao;
    private final ClassMemberDao classMemberDao;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public SyncStatusPresenter(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        this.classDao = db.classDao();
        this.classMemberDao = db.classMemberDao();
    }

    /** Computes the current state off the main thread and delivers it back on the main thread. */
    public void getState(@NonNull RepositoryCallback<State> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            State state;
            try {
                List<SchoolClass> pendingClasses = classDao.getPendingClasses();
                List<ClassMember> pendingMemberships = classMemberDao.getPendingMemberships();

                boolean anyFailed = false;
                for (SchoolClass c : pendingClasses) {
                    if (SyncStatus.FAILED.equals(c.getSyncStatus())) {
                        anyFailed = true;
                        break;
                    }
                }
                if (!anyFailed) {
                    for (ClassMember m : pendingMemberships) {
                        if (SyncStatus.FAILED.equals(m.getSyncStatus())) {
                            anyFailed = true;
                            break;
                        }
                    }
                }

                boolean hasPending = !pendingClasses.isEmpty() || !pendingMemberships.isEmpty();

                if (anyFailed) {
                    state = State.ERROR;
                } else if (!hasPending) {
                    state = State.SYNCED;
                } else if (!NetworkMonitor.isOnline(appContext)) {
                    state = State.OFFLINE;
                } else {
                    state = State.PENDING;
                }
            } catch (Exception e) {
                // Reading local sync state failed; surface as a generic error
                // badge rather than crashing the dashboard.
                state = State.ERROR;
            }

            final State result = state;
            mainThreadHandler.post(() -> callback.onSuccess(result));
        });
    }
}
