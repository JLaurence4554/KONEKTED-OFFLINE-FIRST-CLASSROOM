package com.malapay.classroom.ui.lessons.student;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Lesson;
import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.data.local.model.QuizWithSubmissionStatus;
import com.malapay.classroom.data.repository.LessonRepository;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityLessonDetailBinding;
import com.malapay.classroom.ui.quiz.adapter.StudentQuizAdapter;
import com.malapay.classroom.ui.quiz.student.QuizResultActivity;
import com.malapay.classroom.ui.quiz.student.TakeQuizActivity;
import com.malapay.classroom.util.FileUtil;
import com.malapay.classroom.util.SessionManager;

import java.util.List;

/**
 * Read-only lesson view for students: title, subject, description, optional
 * image, optional video link (opened externally), an optional attachment
 * they can open, and a Quizzes section listing published quizzes for this
 * lesson (Take Quiz, or View My Score if already submitted).
 */
public class LessonDetailActivity extends AppCompatActivity implements StudentQuizAdapter.Listener {

    public static final String EXTRA_LESSON_ID = "extra_lesson_id";

    private ActivityLessonDetailBinding binding;
    private LessonRepository lessonRepository;
    private QuizRepository quizRepository;
    private SessionManager sessionManager;
    private StudentQuizAdapter quizAdapter;
    private Lesson lesson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLessonDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lessonRepository = new LessonRepository(this);
        quizRepository = new QuizRepository(this);
        sessionManager = new SessionManager(this);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(R.string.lessons_title);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        quizAdapter = new StudentQuizAdapter(this);
        binding.recyclerQuizzes.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerQuizzes.setAdapter(quizAdapter);

        long lessonId = getIntent().getLongExtra(EXTRA_LESSON_ID, -1L);
        loadLesson(lessonId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload quiz statuses in case the student just came back from taking one.
        if (lesson != null) {
            loadQuizzes(lesson.getId());
        }
    }

    private void loadLesson(long lessonId) {
        lessonRepository.getLessonById(lessonId, new RepositoryCallback<Lesson>() {
            @Override
            public void onSuccess(Lesson result) {
                lesson = result;
                binding.tvLessonTitle.setText(result.getTitle());
                binding.tvLessonBody.setText(result.getBody());

                if (!result.getSubject().trim().isEmpty()) {
                    binding.tvSubject.setVisibility(View.VISIBLE);
                    binding.tvSubject.setText(result.getSubject());
                } else {
                    binding.tvSubject.setVisibility(View.GONE);
                }

                if (result.hasImage()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(result.getImagePath());
                    if (bitmap != null) {
                        binding.ivLessonImage.setVisibility(View.VISIBLE);
                        binding.ivLessonImage.setImageBitmap(bitmap);
                    } else {
                        binding.ivLessonImage.setVisibility(View.GONE);
                    }
                } else {
                    binding.ivLessonImage.setVisibility(View.GONE);
                }

                if (result.hasVideoLink()) {
                    binding.btnOpenVideoLink.setVisibility(View.VISIBLE);
                    binding.btnOpenVideoLink.setOnClickListener(v -> openVideoLink(result.getVideoLink()));
                } else {
                    binding.btnOpenVideoLink.setVisibility(View.GONE);
                }

                if (result.hasAttachment()) {
                    binding.cardAttachment.setVisibility(View.VISIBLE);
                    binding.tvAttachmentName.setText(result.getAttachmentName());
                    binding.btnOpenAttachment.setOnClickListener(v ->
                            FileUtil.openFile(LessonDetailActivity.this, result.getAttachmentPath(), result.getAttachmentMimeType()));
                } else {
                    binding.cardAttachment.setVisibility(View.GONE);
                }

                loadQuizzes(result.getId());
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LessonDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadQuizzes(long lessonId) {
        long studentId = sessionManager.getUserId();

        // Pull quizzes from server first (merges into local DB), then display
        quizRepository.pullQuizzesFromServer(lessonId, new RepositoryCallback<List<Quiz>>() {
            @Override
            public void onSuccess(List<Quiz> result) {
                // After pull, get quizzes with submission status
                quizRepository.getPublishedQuizzesWithStatus(lessonId, studentId, new RepositoryCallback<List<QuizWithSubmissionStatus>>() {
                    @Override
                    public void onSuccess(List<QuizWithSubmissionStatus> result) {
                        quizAdapter.submitList(result);
                        boolean isEmpty = result == null || result.isEmpty();
                        binding.tvNoQuizzes.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                        binding.recyclerQuizzes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(LessonDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                // Fallback to local
                quizRepository.getPublishedQuizzesWithStatus(lessonId, studentId, new RepositoryCallback<List<QuizWithSubmissionStatus>>() {
                    @Override
                    public void onSuccess(List<QuizWithSubmissionStatus> result) {
                        quizAdapter.submitList(result);
                        boolean isEmpty = result == null || result.isEmpty();
                        binding.tvNoQuizzes.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                        binding.recyclerQuizzes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    }

                    @Override
                    public void onError(String message2) {
                        Toast.makeText(LessonDetailActivity.this, message2, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    public void onTakeQuiz(QuizWithSubmissionStatus item) {
        Intent intent = new Intent(this, TakeQuizActivity.class);
        intent.putExtra(TakeQuizActivity.EXTRA_QUIZ_ID, item.quiz.getId());
        startActivity(intent);
    }

    @Override
    public void onViewScore(QuizWithSubmissionStatus item) {
        Intent intent = new Intent(this, QuizResultActivity.class);
        intent.putExtra(QuizResultActivity.EXTRA_QUIZ_ID, item.quiz.getId());
        startActivity(intent);
    }

    private void openVideoLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open this link.", Toast.LENGTH_SHORT).show();
        }
    }
}
