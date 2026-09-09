package com.malapay.classroom.util;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.JoinRequestDao;
import com.malapay.classroom.ui.notifications.JoinRequestsActivity;
import com.malapay.classroom.sync.SyncManager;

/**
 * Central helper for configuring the shared app header across all screens.
 *
 * Two variants exist:
 * <ul>
 *   <li>{@link #setupDashboardHeader(Activity)} — rich header showing the
 *       user's avatar/initials, a page title, their name, and a
 *       notification bell. Used on the Teacher/Student dashboard hosts.</li>
 *   <li>{@link #setupSubHeader(Activity, String)} — compact header with a back
 *       button, the screen title, and a notification bell. Used on sub-screens.</li>
 * </ul>
 */
public final class HeaderHelper {

    private HeaderHelper() {
    }

    /**
     * Configures the dashboard header (avatar/initials + page title + user name +
     * notification bell) from the logged-in user.
     */
    public static void setupDashboardHeader(@NonNull Activity activity) {
        SessionManager session = new SessionManager(activity);
        String fullName = session.getFullName();

        TextView subtitle = activity.findViewById(R.id.txtHeaderSubtitle);
        if (subtitle != null) {
            subtitle.setText(fullName != null ? fullName : "");
        }

        TextView initials = activity.findViewById(R.id.txtHeaderInitials);
        if (initials != null) {
            initials.setText(getInitials(fullName));
        }

        wireNotifications(activity, R.id.btnNotifications, R.id.txtNotificationBadge);
    }

    /**
     * Updates the dashboard header title (e.g. "Home", "Classes", "Analytics", "Profile").
     */
    public static void setHeaderTitle(@NonNull Activity activity, @NonNull String title) {
        TextView greeting = activity.findViewById(R.id.txtHeaderGreeting);
        if (greeting != null) {
            greeting.setText(title);
        }
    }

    /**
     * Configures the compact sub-screen header (back button + title +
     * notification bell).
     *
     * @param showBack when true, the back button finishes the activity on click.
     */
    public static void setupSubHeader(@NonNull Activity activity, @Nullable String title,
                                      boolean showBack) {
        TextView headerTitle = activity.findViewById(R.id.txtHeaderTitle);
        if (headerTitle != null && title != null) {
            headerTitle.setText(title);
        }

        View backBtn = activity.findViewById(R.id.btnHeaderBack);
        if (backBtn != null) {
            if (showBack) {
                backBtn.setVisibility(View.VISIBLE);
                backBtn.setOnClickListener(v -> {
                    if (activity != null) {
                        activity.finish();
                    }
                });
            } else {
                backBtn.setVisibility(View.GONE);
            }
        }

        wireNotifications(activity, R.id.btnNotificationsSub, R.id.txtNotificationBadgeSub);
    }

    /**
     * Wires the notification bell to open JoinRequestsActivity and shows
     * a badge count of pending join requests (teacher role only).
     */
    private static void wireNotifications(@NonNull Activity activity,
                                          int bellResId,
                                          int badgeResId) {
        View bell = activity.findViewById(bellResId);
        if (bell != null) {
            bell.setOnClickListener(v -> {
                Intent intent = new Intent(activity, JoinRequestsActivity.class);
                activity.startActivity(intent);
            });
        }

        updateNotificationBadge(activity, badgeResId);
    }

    /**
     * Queries the pending join request count and shows/hides the badge.
     * Only applies to teacher accounts.
     */
    public static void updateNotificationBadge(@NonNull Activity activity, int badgeResId) {
        SessionManager session = new SessionManager(activity);
        if (!session.isTeacher()) {
            TextView badge = activity.findViewById(badgeResId);
            if (badge != null) badge.setVisibility(View.GONE);
            return;
        }

        // Show local count immediately so the badge appears without waiting for the network
        refreshBadgeCount(activity, badgeResId);

        // Then fetch fresh data from the server in the background
        SyncManager syncManager = new SyncManager(activity);
        String teacherEmail = session.getEmail();
        syncManager.fetchJoinRequestsFromServer(teacherEmail, new com.malapay.classroom.data.repository.RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                refreshBadgeCount(activity, badgeResId);
            }

            @Override
            public void onError(String message) {
                refreshBadgeCount(activity, badgeResId);
            }
        });
    }

    private static void refreshBadgeCount(@NonNull Activity activity, int badgeResId) {
        SessionManager session = new SessionManager(activity);
        long teacherId = session.getUserId();
        JoinRequestDao dao = AppDatabase.getInstance(activity).joinRequestDao();
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            int count = dao.countPendingForTeacher(teacherId);
            if (activity.isFinishing()) return;
            activity.runOnUiThread(() -> {
                TextView badge = activity.findViewById(badgeResId);
                if (badge != null) {
                    if (count > 0) {
                        badge.setVisibility(View.VISIBLE);
                        badge.setText(count > 99 ? "99+" : String.valueOf(count));
                    } else {
                        badge.setVisibility(View.GONE);
                    }
                }
            });
        });
    }

    /** Capitalized first initial, or empty string when there's no name. */
    private static String getInitials(@Nullable String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }
        return String.valueOf(Character.toUpperCase(fullName.trim().charAt(0)));
    }
}
