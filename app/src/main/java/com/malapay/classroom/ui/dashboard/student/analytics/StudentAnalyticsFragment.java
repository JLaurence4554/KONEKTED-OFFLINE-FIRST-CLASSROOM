package com.malapay.classroom.ui.dashboard.student.analytics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.StudentQuizHistory;
import com.malapay.classroom.data.repository.AnalyticsRepository;
import com.malapay.classroom.data.repository.ClassRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.FragmentStudentAnalyticsBinding;
import com.malapay.classroom.ui.dashboard.student.analytics.adapter.StudentAnalyticsAdapter;
import com.malapay.classroom.util.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Student analytics tab fragment. Shows the student's own performance:
 * - Summary stats (avg score, attendance, quizzes taken)
 * - Score trend line chart over time
 * - Quiz history list with scores
 *
 * The student can select a specific class or view overall stats across all classes.
 * Supports pull-to-refresh to manually trigger a data reload.
 */
public class StudentAnalyticsFragment extends Fragment {

    private FragmentStudentAnalyticsBinding binding;
    private SessionManager sessionManager;
    private AnalyticsRepository analyticsRepository;
    private ClassRepository classRepository;
    private StudentAnalyticsAdapter adapter;

    private long selectedClassId = -1;
    private final List<Long> classIds = new ArrayList<>();
    private final List<String> classNames = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentStudentAnalyticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        analyticsRepository = new AnalyticsRepository(requireContext());
        classRepository = new ClassRepository(requireContext());

        adapter = new StudentAnalyticsAdapter();
        binding.recyclerAnalytics.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerAnalytics.setAdapter(adapter);

        setupClassSpinner();

        // Pull-to-refresh
        binding.swipeRefresh.setColorSchemeResources(R.color.secondary);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            setupClassSpinner();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadClasses();
    }

    /** Reloads the class spinner data (called on resume to pick up new classes). */
    private void loadClasses() {
        if (sessionManager == null) return;
        setupClassSpinner();
    }

    private void setupClassSpinner() {
        long studentId = sessionManager.getUserId();

        classRepository.getJoinedClasses(studentId, new RepositoryCallback<List<com.malapay.classroom.data.local.entity.SchoolClass>>() {
            @Override
            public void onSuccess(List<com.malapay.classroom.data.local.entity.SchoolClass> result) {
                if (binding == null) return;

                classNames.clear();
                classIds.clear();

                // Add "Overall" option first
                classNames.add(getString(R.string.my_progress_overall));
                classIds.add(-1L);

                if (result != null) {
                    for (com.malapay.classroom.data.local.entity.SchoolClass sc : result) {
                        classNames.add(sc.getClassName() + " (" + sc.getSubject() + ")");
                        classIds.add(sc.getId());
                    }
                }

                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        classNames);
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                binding.spinnerClass.setAdapter(spinnerAdapter);

                binding.spinnerClass.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        selectedClassId = classIds.get(position);
                        loadAnalytics(selectedClassId);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        showEmptyState();
                    }
                });

                // Stop refresh spinner
                binding.swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    Toast.makeText(requireContext(), "Could not load classes", Toast.LENGTH_SHORT).show();
                    binding.swipeRefresh.setRefreshing(false);
                }
            }
        });
    }

    private void loadAnalytics(long classId) {
        long studentId = sessionManager.getUserId();
        binding.recyclerAnalytics.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);

        adapter.setShowClassesJoined(classId < 0); // Show classes count only in "Overall" mode

        // Load summary
        if (classId > 0) {
            analyticsRepository.getStudentClassSummary(classId, studentId, new RepositoryCallback<AnalyticsRepository.StudentClassSummary>() {
                @Override
                public void onSuccess(AnalyticsRepository.StudentClassSummary result) {
                    if (binding == null) return;
                    adapter.setSummary(result);
                }

                @Override
                public void onError(String message) {
                    if (binding != null) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            analyticsRepository.getStudentOverallSummary(studentId, new RepositoryCallback<AnalyticsRepository.StudentClassSummary>() {
                @Override
                public void onSuccess(AnalyticsRepository.StudentClassSummary result) {
                    if (binding == null) return;
                    adapter.setSummary(result);
                }

                @Override
                public void onError(String message) {
                    if (binding != null) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // Load quiz history
        if (classId > 0) {
            analyticsRepository.getStudentQuizHistory(classId, studentId, new RepositoryCallback<List<StudentQuizHistory>>() {
                @Override
                public void onSuccess(List<StudentQuizHistory> result) {
                    if (binding == null) return;
                    adapter.setQuizHistory(result);
                }

                @Override
                public void onError(String message) {
                    // Non-critical
                }
            });
        } else {
            analyticsRepository.getStudentAllQuizHistory(studentId, new RepositoryCallback<List<StudentQuizHistory>>() {
                @Override
                public void onSuccess(List<StudentQuizHistory> result) {
                    if (binding == null) return;
                    adapter.setQuizHistory(result);
                }

                @Override
                public void onError(String message) {
                    // Non-critical
                }
            });
        }
    }

    private void showEmptyState() {
        binding.recyclerAnalytics.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
