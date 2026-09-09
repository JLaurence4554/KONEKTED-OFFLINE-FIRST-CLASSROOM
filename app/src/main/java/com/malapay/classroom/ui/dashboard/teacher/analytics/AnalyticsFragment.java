package com.malapay.classroom.ui.dashboard.teacher.analytics;

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
import com.malapay.classroom.data.local.model.ClassAnalyticsSummary;
import com.malapay.classroom.data.local.model.QuizScoreDistribution;
import com.malapay.classroom.data.local.model.StudentPerformance;
import com.malapay.classroom.data.repository.AnalyticsRepository;
import com.malapay.classroom.data.repository.ClassRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.FragmentTeacherAnalyticsBinding;
import com.malapay.classroom.ui.dashboard.teacher.analytics.adapter.AnalyticsAdapter;
import com.malapay.classroom.ui.dashboard.teacher.analytics.model.AnalyticsItem;
import com.malapay.classroom.util.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Analytics tab fragment for the teacher dashboard. Displays class-level
 * performance insights including summary stats, score distribution chart,
 * and per-student performance. The teacher selects a class from the spinner
 * at the top to filter analytics to that class.
 * Supports pull-to-refresh to manually trigger a data reload.
 */
public class AnalyticsFragment extends Fragment {

    private FragmentTeacherAnalyticsBinding binding;
    private SessionManager sessionManager;
    private AnalyticsRepository analyticsRepository;
    private ClassRepository classRepository;
    private AnalyticsAdapter adapter;

    /** Selected class ID. -1 means no class selected. */
    private long selectedClassId = -1;

    /** List of class IDs corresponding to the spinner items. */
    private final List<Long> classIds = new ArrayList<>();

    /** List of class names for the spinner. */
    private final List<String> classNames = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentTeacherAnalyticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        analyticsRepository = new AnalyticsRepository(requireContext());
        classRepository = new ClassRepository(requireContext());

        adapter = new AnalyticsAdapter();
        binding.recyclerAnalytics.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerAnalytics.setAdapter(adapter);

        setupClassSpinner();

        // Pull-to-refresh
        binding.swipeRefresh.setColorSchemeResources(R.color.secondary);
        binding.swipeRefresh.setOnRefreshListener(this::setupClassSpinner);
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

    /** Populates the class spinner with the teacher's classes. */
    private void setupClassSpinner() {
        long teacherId = sessionManager.getUserId();

        classRepository.getClassesForTeacher(teacherId, new RepositoryCallback<List<com.malapay.classroom.data.local.model.ClassWithStudentCount>>() {
            @Override
            public void onSuccess(List<com.malapay.classroom.data.local.model.ClassWithStudentCount> result) {
                if (binding == null) return;

                classNames.clear();
                classIds.clear();

                // Add a placeholder option
                classNames.add(getString(R.string.analytics_select_class));
                classIds.add(-1L);

                if (result != null) {
                    for (com.malapay.classroom.data.local.model.ClassWithStudentCount csc : result) {
                        classNames.add(csc.schoolClass.getClassName() + " (" + csc.schoolClass.getSubject() + ")");
                        classIds.add(csc.schoolClass.getId());
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
                        if (selectedClassId > 0) {
                            loadAnalytics(selectedClassId);
                        } else {
                            showEmptyState();
                        }
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

    /** Loads all analytics data for the selected class. */
    private void loadAnalytics(long classId) {
        binding.recyclerAnalytics.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);

        final ClassAnalytics analytics = new ClassAnalytics();

        // Load summary
        analyticsRepository.getClassSummary(classId, new RepositoryCallback<ClassAnalyticsSummary>() {
            @Override
            public void onSuccess(ClassAnalyticsSummary result) {
                if (binding == null) return;
                analytics.summary = result;
                tryBuildList(analytics);
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Load score distribution
        analyticsRepository.getScoreDistribution(classId, new RepositoryCallback<List<QuizScoreDistribution>>() {
            @Override
            public void onSuccess(List<QuizScoreDistribution> result) {
                if (binding == null) return;
                analytics.distribution = result;
                tryBuildList(analytics);
            }

            @Override
            public void onError(String message) {
                // Non-critical; chart will just be empty
            }
        });

        // Load student performance
        analyticsRepository.getStudentPerformance(classId, new RepositoryCallback<List<StudentPerformance>>() {
            @Override
            public void onSuccess(List<StudentPerformance> result) {
                if (binding == null) return;
                analytics.students = result;
                tryBuildList(analytics);
            }

            @Override
            public void onError(String message) {
                // Non-critical; list will just be empty
            }
        });
    }

    /** Attempts to build the analytics list once summary data is available. */
    private void tryBuildList(ClassAnalytics analytics) {
        if (analytics == null || analytics.summary == null) return;

        List<AnalyticsItem> items = new ArrayList<>();
        items.add(AnalyticsItem.summary(analytics.summary));

        if (analytics.distribution != null) {
            items.add(AnalyticsItem.barChart(analytics.distribution));
        }

        if (analytics.students != null) {
            items.add(AnalyticsItem.studentList(analytics.students));
        }

        adapter.setItems(items);
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

    /** Simple holder for async-loaded analytics data pieces. */
    private static class ClassAnalytics {
        ClassAnalyticsSummary summary;
        List<QuizScoreDistribution> distribution;
        List<StudentPerformance> students;
    }
}
