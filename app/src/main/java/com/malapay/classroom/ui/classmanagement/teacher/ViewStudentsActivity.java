package com.malapay.classroom.ui.classmanagement.teacher;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.StudentInClass;
import com.malapay.classroom.data.repository.ClassRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityViewStudentsBinding;
import com.malapay.classroom.ui.classmanagement.adapter.StudentAdapter;

import java.util.List;

/**
 * Shows the roster of students enrolled in a single class.
 * Supports pull-to-refresh to manually trigger a data reload.
 */
public class ViewStudentsActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID = "extra_class_id";
    public static final String EXTRA_CLASS_NAME = "extra_class_name";

    private ActivityViewStudentsBinding binding;
    private ClassRepository classRepository;
    private StudentAdapter adapter;
    private long classId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityViewStudentsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        classRepository = new ClassRepository(this);

        classId = getIntent().getLongExtra(EXTRA_CLASS_ID, -1L);
        String className = getIntent().getStringExtra(EXTRA_CLASS_NAME);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(className != null ? className : getString(R.string.view_students_title));
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        adapter = new StudentAdapter();
        binding.recyclerStudents.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerStudents.setAdapter(adapter);

        // Pull-to-refresh
        binding.swipeRefresh.setColorSchemeResources(R.color.secondary);
        binding.swipeRefresh.setOnRefreshListener(this::loadStudents);

        loadStudents();
    }

    private void loadStudents() {
        classRepository.getStudentsInClass(classId, new RepositoryCallback<List<StudentInClass>>() {
            @Override
            public void onSuccess(List<StudentInClass> result) {
                adapter.submitList(result);
                boolean isEmpty = result == null || result.isEmpty();
                binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerStudents.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                binding.swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ViewStudentsActivity.this, message, Toast.LENGTH_SHORT).show();
                binding.swipeRefresh.setRefreshing(false);
            }
        });
    }
}
