package com.malapay.classroom.ui.dashboard.teacher;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.malapay.classroom.R;
import com.malapay.classroom.databinding.ActivityTeacherDashboardBinding;
import com.malapay.classroom.ui.dashboard.teacher.analytics.AnalyticsFragment;
import com.malapay.classroom.util.HeaderHelper;

/**
 * Host Activity for the Teacher role. Owns the Top App Bar and the
 * BottomNavigationView, and swaps between three Fragments:
 * Dashboard, Classes, and Profile.
 */
public class TeacherDashboardActivity extends AppCompatActivity {

    private ActivityTeacherDashboardBinding binding;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private static final long NOTIFICATION_POLL_INTERVAL_MS = 15_000; // 15 seconds

    private final Runnable notificationPollRunnable = new Runnable() {
        @Override
        public void run() {
            HeaderHelper.updateNotificationBadge(TeacherDashboardActivity.this, R.id.txtNotificationBadge);
            pollHandler.postDelayed(this, NOTIFICATION_POLL_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTeacherDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        HeaderHelper.setupDashboardHeader(this);

        binding.bottomNavigation.setOnItemSelectedListener(this::onNavItemSelected);

        if (savedInstanceState == null) {
            showFragment(new TeacherDashboardFragment());
            HeaderHelper.setHeaderTitle(this, getString(R.string.nav_dashboard));
            binding.appBarLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        HeaderHelper.updateNotificationBadge(this, R.id.txtNotificationBadge);
        startNotificationPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopNotificationPolling();
    }

    private void startNotificationPolling() {
        pollHandler.removeCallbacks(notificationPollRunnable);
        pollHandler.postDelayed(notificationPollRunnable, NOTIFICATION_POLL_INTERVAL_MS);
    }

    private void stopNotificationPolling() {
        pollHandler.removeCallbacks(notificationPollRunnable);
    }

    private boolean onNavItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_dashboard) {
            showFragment(new TeacherDashboardFragment());
            HeaderHelper.setHeaderTitle(this, getString(R.string.nav_dashboard));
            binding.appBarLayout.setVisibility(View.VISIBLE);
            return true;
        } else if (id == R.id.nav_classes) {
            showFragment(new TeacherClassesFragment());
            HeaderHelper.setHeaderTitle(this, getString(R.string.nav_classes));
            binding.appBarLayout.setVisibility(View.VISIBLE);
            return true;
        } else if (id == R.id.nav_analytics) {
            showFragment(new AnalyticsFragment());
            HeaderHelper.setHeaderTitle(this, getString(R.string.nav_analytics));
            binding.appBarLayout.setVisibility(View.VISIBLE);
            return true;
        } else if (id == R.id.nav_profile) {
            showFragment(new TeacherProfileFragment());
            binding.appBarLayout.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    /** Lets DashboardFragment's "Manage Classes" button jump straight to the Classes tab. */
    public void navigateToClassesTab() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_classes);
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
