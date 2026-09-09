package com.malapay.classroom.ui.dashboard.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.ClassWithStudentCount;
import com.malapay.classroom.data.repository.ClassRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.FragmentTeacherClassesBinding;
import com.malapay.classroom.ui.classmanagement.adapter.ClassAdapter;
import com.malapay.classroom.ui.classmanagement.teacher.CreateEditClassActivity;
import com.malapay.classroom.ui.classmanagement.teacher.ViewStudentsActivity;
import com.malapay.classroom.ui.lessons.LessonsListActivity;
import com.malapay.classroom.util.SessionManager;

import java.util.List;

/**
 * "Classes" tab: lists every class this teacher owns, with search, and
 * entry points into Create / Edit / Delete / View Students.
 * Supports pull-to-refresh to manually trigger a data reload.
 */
public class TeacherClassesFragment extends Fragment implements ClassAdapter.Listener {

    private FragmentTeacherClassesBinding binding;
    private SessionManager sessionManager;
    private ClassRepository classRepository;
    private ClassAdapter adapter;
    private String currentQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentTeacherClassesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        classRepository = new ClassRepository(requireContext());

        adapter = new ClassAdapter(this);
        binding.recyclerClasses.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerClasses.setAdapter(adapter);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                loadClasses();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.fabCreateClass.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CreateEditClassActivity.class)));

        // Pull-to-refresh
        binding.swipeRefresh.setColorSchemeResources(R.color.secondary);
        binding.swipeRefresh.setOnRefreshListener(this::loadClasses);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadClasses();
    }

    private void loadClasses() {
        long teacherId = sessionManager.getUserId();
        RepositoryCallback<List<ClassWithStudentCount>> callback = new RepositoryCallback<List<ClassWithStudentCount>>() {
            @Override
            public void onSuccess(List<ClassWithStudentCount> result) {
                if (binding == null) return;
                adapter.submitList(result);
                boolean isEmpty = result == null || result.isEmpty();
                binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerClasses.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                if (isEmpty && !currentQuery.trim().isEmpty()) {
                    binding.tvEmptyTitle.setText(R.string.empty_search_results);
                    binding.tvEmptyBody.setText("");
                } else {
                    binding.tvEmptyTitle.setText(R.string.empty_classes_title);
                    binding.tvEmptyBody.setText(R.string.empty_classes_body);
                }
                binding.swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                binding.swipeRefresh.setRefreshing(false);
            }
        };

        if (currentQuery.trim().isEmpty()) {
            classRepository.getClassesForTeacher(teacherId, callback);
        } else {
            classRepository.searchClassesForTeacher(teacherId, currentQuery, callback);
        }
    }

    @Override
    public void onEditClass(ClassWithStudentCount item) {
        Intent intent = new Intent(requireContext(), CreateEditClassActivity.class);
        intent.putExtra(CreateEditClassActivity.EXTRA_CLASS_ID, item.schoolClass.getId());
        startActivity(intent);
    }

    @Override
    public void onDeleteClass(ClassWithStudentCount item) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_class_title)
                .setMessage(getString(R.string.delete_class_body, item.schoolClass.getClassName()))
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> performDelete(item))
                .show();
    }

    private void performDelete(ClassWithStudentCount item) {
        classRepository.deleteClass(item.schoolClass, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (binding != null) {
                    loadClasses();
                }
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onViewStudents(ClassWithStudentCount item) {
        Intent intent = new Intent(requireContext(), ViewStudentsActivity.class);
        intent.putExtra(ViewStudentsActivity.EXTRA_CLASS_ID, item.schoolClass.getId());
        intent.putExtra(ViewStudentsActivity.EXTRA_CLASS_NAME, item.schoolClass.getClassName());
        startActivity(intent);
    }

    @Override
    public void onManageLessons(ClassWithStudentCount item) {
        Intent intent = new Intent(requireContext(), LessonsListActivity.class);
        intent.putExtra(LessonsListActivity.EXTRA_CLASS_ID, item.schoolClass.getId());
        intent.putExtra(LessonsListActivity.EXTRA_CLASS_NAME, item.schoolClass.getClassName());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
