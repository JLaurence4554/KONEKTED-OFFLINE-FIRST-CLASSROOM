package com.malapay.classroom.ui.dashboard.student;

import android.content.Intent;
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
import com.malapay.classroom.databinding.FragmentStudentDashboardBinding;
import com.malapay.classroom.sync.NetworkMonitor;
import com.malapay.classroom.sync.SyncManager;
import com.malapay.classroom.sync.SyncStatusPresenter;
import com.malapay.classroom.util.SessionManager;

/**
 * "Dashboard" tab: greeting, Joined Classes stat card, and the entry point
 * into "My Classes". The stat is reloaded in onResume so returning from
 * My Classes (after joining a class) shows an up-to-date count.
 * Supports pull-to-refresh to manually trigger a data reload.
 */
public class StudentDashboardFragment extends Fragment {

    private FragmentStudentDashboardBinding binding;
    private SessionManager sessionManager;
    private ClassRepository classRepository;
    private SyncManager syncManager;
    private SyncStatusPresenter syncStatusPresenter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentStudentDashboardBinding.inflate(inflater, container, false);
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

        binding.btnMyClasses.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MyClassesActivity.class)));

        binding.btnJoinQuiz.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.malapay.classroom.ui.quiz.student.JoinQuizActivity.class)));

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
        // PHASE: online sync. Opportunistic catch-up — pushes any membership
        // this student joined while offline now that the app is active again
        // and (maybe) has a connection. Silent; a no-op if nothing's pending.
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
        long studentId = sessionManager.getUserId();
        classRepository.getJoinedClassesCount(studentId, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                if (binding != null) {
                    binding.tvJoinedClasses.setText(String.valueOf(result));
                }
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    binding.tvJoinedClasses.setText("—");
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
