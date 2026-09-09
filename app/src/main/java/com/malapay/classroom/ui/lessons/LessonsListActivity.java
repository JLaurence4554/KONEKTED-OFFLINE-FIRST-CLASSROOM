package com.malapay.classroom.ui.lessons;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Lesson;
import com.malapay.classroom.data.repository.LessonRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityLessonsListBinding;
import com.malapay.classroom.ui.lessons.adapter.LessonAdapter;
import com.malapay.classroom.ui.lessons.student.LessonDetailActivity;
import com.malapay.classroom.ui.lessons.teacher.CreateEditLessonActivity;
import com.malapay.classroom.ui.lessons.teacher.TeacherLessonDetailActivity;
import com.malapay.classroom.util.SessionManager;

import java.util.List;

/**
 * Lists every lesson posted for a class. This is now the default landing
 * page after selecting a class (the student roster is reached from a menu
 * instead — see ViewStudentsActivity). Behavior branches on role: teachers
 * get search, a FAB to add lessons, and tapping a row opens the Lesson
 * Details page; students get a read-only list where tapping a row opens
 * the (student-facing) lesson detail screen.
 * Supports pull-to-refresh to manually trigger a data reload.
 */
public class LessonsListActivity extends AppCompatActivity implements LessonAdapter.Listener {

    public static final String EXTRA_CLASS_ID = "extra_class_id";
    public static final String EXTRA_CLASS_NAME = "extra_class_name";

    private ActivityLessonsListBinding binding;
    private LessonRepository lessonRepository;
    private SessionManager sessionManager;
    private LessonAdapter adapter;
    private long classId;
    private boolean isTeacher;
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLessonsListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lessonRepository = new LessonRepository(this);
        sessionManager = new SessionManager(this);
        isTeacher = sessionManager.isTeacher();

        classId = getIntent().getLongExtra(EXTRA_CLASS_ID, -1L);
        String className = getIntent().getStringExtra(EXTRA_CLASS_NAME);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(className != null ? className : getString(R.string.lessons_title));
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        adapter = new LessonAdapter(this, isTeacher);
        binding.recyclerLessons.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerLessons.setAdapter(adapter);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                loadLessons();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (isTeacher) {
            binding.fabAddLesson.setVisibility(View.VISIBLE);
            binding.fabAddLesson.setOnClickListener(v -> {
                Intent intent = new Intent(this, CreateEditLessonActivity.class);
                intent.putExtra(CreateEditLessonActivity.EXTRA_CLASS_ID, classId);
                startActivity(intent);
            });
        } else {
            binding.fabAddLesson.setVisibility(View.GONE);
        }

        // Pull-to-refresh
        binding.swipeRefresh.setColorSchemeResources(R.color.secondary);
        binding.swipeRefresh.setOnRefreshListener(this::loadLessons);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLessons();
    }

    private void loadLessons() {
        RepositoryCallback<List<Lesson>> callback = new RepositoryCallback<List<Lesson>>() {
            @Override
            public void onSuccess(List<Lesson> result) {
                if (binding == null) return;
                adapter.submitList(result);
                boolean isEmpty = result == null || result.isEmpty();
                binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerLessons.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

                if (isEmpty && !currentQuery.trim().isEmpty()) {
                    binding.tvEmptyTitle.setText(R.string.empty_search_lessons);
                    binding.tvEmptyBody.setText("");
                } else {
                    binding.tvEmptyTitle.setText(isTeacher ? R.string.empty_lessons_title_teacher : R.string.empty_lessons_title_student);
                    binding.tvEmptyBody.setText(isTeacher ? R.string.empty_lessons_body_teacher : R.string.empty_lessons_body_student);
                }
                binding.swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                Toast.makeText(LessonsListActivity.this, message, Toast.LENGTH_SHORT).show();
                binding.swipeRefresh.setRefreshing(false);
            }
        };

        if (currentQuery.trim().isEmpty()) {
            if (isTeacher) {
                // Teachers see local lessons (they created them)
                lessonRepository.getLessonsForClass(classId, callback);
            } else {
                // Students pull from server first, then fall back to local
                lessonRepository.pullLessonsFromServer(classId, callback);
            }
        } else {
            lessonRepository.searchLessonsForClass(classId, currentQuery, callback);
        }
    }

    @Override
    public void onLessonClick(Lesson lesson) {
        if (isTeacher) {
            Intent intent = new Intent(this, TeacherLessonDetailActivity.class);
            intent.putExtra(TeacherLessonDetailActivity.EXTRA_LESSON_ID, lesson.getId());
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, LessonDetailActivity.class);
            intent.putExtra(LessonDetailActivity.EXTRA_LESSON_ID, lesson.getId());
            startActivity(intent);
        }
    }

    @Override
    public void onEditLesson(Lesson lesson) {
        openEditScreen(lesson);
    }

    private void openEditScreen(Lesson lesson) {
        Intent intent = new Intent(this, CreateEditLessonActivity.class);
        intent.putExtra(CreateEditLessonActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(CreateEditLessonActivity.EXTRA_LESSON_ID, lesson.getId());
        startActivity(intent);
    }

    @Override
    public void onDeleteLesson(Lesson lesson) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_lesson_title)
                .setMessage(getString(R.string.delete_lesson_body, lesson.getTitle()))
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> performDelete(lesson))
                .show();
    }

    private void performDelete(Lesson lesson) {
        lessonRepository.deleteLesson(lesson, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (binding != null) {
                    loadLessons();
                }
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    Toast.makeText(LessonsListActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
