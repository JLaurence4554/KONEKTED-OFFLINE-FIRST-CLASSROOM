package com.malapay.classroom.ui.dashboard.teacher;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.malapay.classroom.R;
import com.malapay.classroom.data.repository.ClassRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.FragmentTeacherDashboardBinding;
import com.malapay.classroom.sync.NetworkMonitor;
import com.malapay.classroom.sync.SyncManager;
import com.malapay.classroom.sync.SyncStatusPresenter;
import com.malapay.classroom.util.SessionManager;

/**
 * "Dashboard" tab: greeting, Total Classes / Total Students stat cards,
 * and the entry point into class management. Stats are reloaded in
 * onResume so returning from the Classes tab (after creating/deleting a
 * class) shows up-to-date numbers.
 * Supports pull-to-refresh to manually trigger a data reload.
 */
public class TeacherDashboardFragment extends Fragment {

    private FragmentTeacherDashboardBinding binding;
    private SessionManager sessionManager;
    private ClassRepository classRepository;
    private SyncManager syncManager;
    private SyncStatusPresenter syncStatusPresenter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentTeacherDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        classRepository = new ClassRepository(requireContext());
        syncManager = new SyncManager(requireContext());
        syncStatusPresenter = new SyncStatusPresenter(requireContext());

        binding.tvGreeting.setText(getString(R.string.dashboard_greeting, sessionManager.getFullName()));

        // Pull-to-refresh: sync data then reload stats
        binding.swipeRefresh.setColorSchemeResources(R.color.secondary);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            syncManager.syncAllSilently();
            loadStats();
            refreshSyncStatus();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStats();
        // PHASE: online sync. Opportunistic catch-up — pushes any class this
        // teacher created while offline now that the app is active again and
        // (maybe) has a connection. Silent; a no-op if nothing's pending.
        syncManager.syncAllSilently();
        refreshSyncStatus();
    }

    /** Updates the small sync badge under the welcome banner from the real sync state. */
    private void refreshSyncStatus() {
        syncStatusPresenter.getState(new RepositoryCallback<SyncStatusPresenter.State>() {
            @Override
            public void onSuccess(SyncStatusPresenter.State state) {
                if (binding == null) {
                    return;
                }
                binding.tvSyncStatus.setText(state.textRes);
                binding.tvSyncStatus.setTextColor(
                        androidx.core.content.ContextCompat.getColor(requireContext(), state.colorRes));
                binding.tvSyncStatus.setBackgroundResource(state.backgroundRes);
                // Stop the refresh spinner once sync status is known
                stopRefreshing();
            }

            @Override
            public void onError(String message) {
                // Leave the last-known badge state on screen rather than
                // showing an alarming state from a failed read.
                stopRefreshing();
            }
        });
    }

    private void loadStats() {
        long teacherId = sessionManager.getUserId();

        classRepository.getTotalClasses(teacherId, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                if (binding != null) {
                    binding.tvTotalClasses.setText(String.valueOf(result));
                }
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    binding.tvTotalClasses.setText("—");
                }
            }
        });

        classRepository.getTotalStudents(teacherId, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                if (binding != null) {
                    binding.tvTotalStudents.setText(String.valueOf(result));
                }
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    binding.tvTotalStudents.setText("—");
                }
            }
        });
    }

    private void stopRefreshing() {
        if (binding != null) {
            binding.swipeRefresh.setRefreshing(false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
