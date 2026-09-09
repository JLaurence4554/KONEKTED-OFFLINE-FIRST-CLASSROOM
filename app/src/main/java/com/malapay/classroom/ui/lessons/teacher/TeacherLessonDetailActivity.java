package com.malapay.classroom.ui.lessons.teacher;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Lesson;
import com.malapay.classroom.data.repository.LessonRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityTeacherLessonDetailBinding;
import com.malapay.classroom.ui.quiz.teacher.CreateEditQuizActivity;
import com.malapay.classroom.ui.quiz.teacher.QuizListActivity;
import com.malapay.classroom.util.FileUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Teacher-facing Lesson Details screen — the destination when a teacher taps
 * a lesson from the Lessons list. Reorganizes everything about a lesson in
 * one place: its content, Edit/Delete, and entry points into Quiz/Attendance/
 * Scores. Those last three don't have a module to open yet (Stage 2 of this
 * feature), so they show a "coming soon" message rather than a broken screen.
 */
public class TeacherLessonDetailActivity extends AppCompatActivity {

    public static final String EXTRA_LESSON_ID = "extra_lesson_id";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    private ActivityTeacherLessonDetailBinding binding;
    private LessonRepository lessonRepository;
    private Lesson lesson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTeacherLessonDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lessonRepository = new LessonRepository(this);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(R.string.lesson_details_title);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        binding.btnCreateQuiz.setOnClickListener(v -> {
            if (lesson == null) return;
            Intent intent = new Intent(this, CreateEditQuizActivity.class);
            intent.putExtra(CreateEditQuizActivity.EXTRA_LESSON_ID, lesson.getId());
            startActivity(intent);
        });
        binding.btnViewQuizzes.setOnClickListener(v -> {
            if (lesson == null) return;
            Intent intent = new Intent(this, QuizListActivity.class);
            intent.putExtra(QuizListActivity.EXTRA_LESSON_ID, lesson.getId());
            intent.putExtra(QuizListActivity.EXTRA_LESSON_TITLE, lesson.getTitle());
            startActivity(intent);
        });
        binding.btnAttendance.setOnClickListener(v -> showComingSoon());
        binding.btnStudentScores.setOnClickListener(v -> showComingSoon());

        binding.btnEditLesson.setOnClickListener(v -> {
            if (lesson == null) return;
            Intent intent = new Intent(this, CreateEditLessonActivity.class);
            intent.putExtra(CreateEditLessonActivity.EXTRA_CLASS_ID, lesson.getClassId());
            intent.putExtra(CreateEditLessonActivity.EXTRA_LESSON_ID, lesson.getId());
            startActivity(intent);
        });

        binding.btnDeleteLesson.setOnClickListener(v -> {
            if (lesson == null) return;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_lesson_title)
                    .setMessage(getString(R.string.delete_lesson_body, lesson.getTitle()))
                    .setNegativeButton(R.string.btn_cancel, null)
                    .setPositiveButton(R.string.action_delete, (dialog, which) -> performDelete())
                    .show();
        });

        long lessonId = getIntent().getLongExtra(EXTRA_LESSON_ID, -1L);
        loadLesson(lessonId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload in case the person just came back from editing.
        if (lesson != null) {
            loadLesson(lesson.getId());
        }
    }

    private void loadLesson(long lessonId) {
        lessonRepository.getLessonById(lessonId, new RepositoryCallback<Lesson>() {
            @Override
            public void onSuccess(Lesson result) {
                lesson = result;
                bindLesson(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TeacherLessonDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void bindLesson(Lesson lesson) {
        binding.tvLessonTitle.setText(lesson.getTitle());
        binding.tvLessonBody.setText(lesson.getBody());
        binding.tvLessonDates.setText(getString(R.string.last_updated_label, DATE_FORMAT.format(new Date(lesson.getUpdatedAt()))));

        if (!lesson.getSubject().trim().isEmpty()) {
            binding.tvSubject.setVisibility(View.VISIBLE);
            binding.tvSubject.setText(lesson.getSubject());
        } else {
            binding.tvSubject.setVisibility(View.GONE);
        }

        if (lesson.hasImage()) {
            Bitmap bitmap = BitmapFactory.decodeFile(lesson.getImagePath());
            if (bitmap != null) {
                binding.ivLessonImage.setVisibility(View.VISIBLE);
                binding.ivLessonImage.setImageBitmap(bitmap);
            } else {
                binding.ivLessonImage.setVisibility(View.GONE);
            }
        } else {
            binding.ivLessonImage.setVisibility(View.GONE);
        }

        if (lesson.hasAttachment()) {
            binding.cardAttachment.setVisibility(View.VISIBLE);
            binding.tvAttachmentName.setText(lesson.getAttachmentName());
            binding.btnOpenAttachment.setOnClickListener(v ->
                    FileUtil.openFile(this, lesson.getAttachmentPath(), lesson.getAttachmentMimeType()));
        } else {
            binding.cardAttachment.setVisibility(View.GONE);
        }

        if (lesson.hasVideoLink()) {
            binding.btnOpenVideoLink.setVisibility(View.VISIBLE);
            binding.btnOpenVideoLink.setOnClickListener(v -> openVideoLink(lesson.getVideoLink()));
        } else {
            binding.btnOpenVideoLink.setVisibility(View.GONE);
        }
    }

    private void openVideoLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void performDelete() {
        lessonRepository.deleteLesson(lesson, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TeacherLessonDetailActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showComingSoon() {
        Snackbar.make(binding.getRoot(), R.string.coming_soon_message, Snackbar.LENGTH_SHORT).show();
    }
}
