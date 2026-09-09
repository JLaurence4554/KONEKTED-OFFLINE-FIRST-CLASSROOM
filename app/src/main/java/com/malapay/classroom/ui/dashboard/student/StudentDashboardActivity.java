package com.malapay.classroom.ui.dashboard.student;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.malapay.classroom.R;
import com.malapay.classroom.databinding.ActivityStudentDashboardBinding;
import com.malapay.classroom.ui.dashboard.student.analytics.StudentAnalyticsFragment;
import com.malapay.classroom.util.HeaderHelper;

/**
 * Host Activity for the Student role. Owns the Top App Bar and the
 * BottomNavigationView, and swaps between two Fragments: Dashboard and
 * Profile. "My Classes" is a separate screen (not a bottom-nav tab),
 * launched from the Dashboard tab's button.
 */
public class StudentDashboardActivity extends AppCompatActivity {

    private ActivityStudentDashboardBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        HeaderHelper.setupDashboardHeader(this);

        binding.bottomNavigation.setOnItemSelectedListener(this::onNavItemSelected);

        if (savedInstanceState == null) {
            showFragment(new StudentDashboardFragment());
            HeaderHelper.setHeaderTitle(this, getString(R.string.nav_dashboard));
            binding.appBarLayout.setVisibility(View.VISIBLE);
        }
    }

    private boolean onNavItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_dashboard) {
            showFragment(new StudentDashboardFragment());
            HeaderHelper.setHeaderTitle(this, getString(R.string.nav_dashboard));
            binding.appBarLayout.setVisibility(View.VISIBLE);
            return true;
        } else if (id == R.id.nav_my_progress) {
            showFragment(new StudentAnalyticsFragment());
            HeaderHelper.setHeaderTitle(this, getString(R.string.nav_my_progress));
            binding.appBarLayout.setVisibility(View.VISIBLE);
            return true;
        } else if (id == R.id.nav_profile) {
            showFragment(new StudentProfileFragment());
            binding.appBarLayout.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
