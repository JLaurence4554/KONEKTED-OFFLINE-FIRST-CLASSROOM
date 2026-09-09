package com.malapay.classroom.ui.quiz.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.data.local.model.QuizWithQuestionCount;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityQuizListBinding;
import com.malapay.classroom.ui.quiz.adapter.QuizAdapter;

import java.util.List;

/** Lists every quiz for a lesson. Reached from the Lesson Details page's "View Quizzes" button. */
public class QuizListActivity extends AppCompatActivity implements QuizAdapter.Listener {

    public static final String EXTRA_LESSON_ID = "extra_lesson_id";
    public static final String EXTRA_LESSON_TITLE = "extra_lesson_title";

    private ActivityQuizListBinding binding;
    private QuizRepository quizRepository;
    private QuizAdapter adapter;
    private long lessonId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        quizRepository = new QuizRepository(this);

        lessonId = getIntent().getLongExtra(EXTRA_LESSON_ID, -1L);
        String lessonTitle = getIntent().getStringExtra(EXTRA_LESSON_TITLE);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(lessonTitle != null ? lessonTitle : getString(R.string.quiz_list_title));
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        adapter = new QuizAdapter(this);
        binding.recyclerQuizzes.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerQuizzes.setAdapter(adapter);

        binding.fabAddQuiz.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateEditQuizActivity.class);
            intent.putExtra(CreateEditQuizActivity.EXTRA_LESSON_ID, lessonId);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadQuizzes();
    }

    private void loadQuizzes() {
        quizRepository.getQuizzesForLesson(lessonId, new RepositoryCallback<List<QuizWithQuestionCount>>() {
            @Override
            public void onSuccess(List<QuizWithQuestionCount> result) {
                adapter.submitList(result);
                boolean isEmpty = result == null || result.isEmpty();
                binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerQuizzes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(QuizListActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onQuizClick(QuizWithQuestionCount item) {
        openEditScreen(item);
    }

    @Override
    public void onEditQuiz(QuizWithQuestionCount item) {
        openEditScreen(item);
    }

    private void openEditScreen(QuizWithQuestionCount item) {
        Intent intent = new Intent(this, CreateEditQuizActivity.class);
        intent.putExtra(CreateEditQuizActivity.EXTRA_LESSON_ID, lessonId);
        intent.putExtra(CreateEditQuizActivity.EXTRA_QUIZ_ID, item.quiz.getId());
        startActivity(intent);
    }

    @Override
    public void onDeleteQuiz(QuizWithQuestionCount item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_quiz_title)
                .setMessage(getString(R.string.delete_quiz_body, item.quiz.getTitle()))
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> performDelete(item))
                .show();
    }

    private void performDelete(QuizWithQuestionCount item) {
        quizRepository.deleteQuiz(item.quiz, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadQuizzes();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(QuizListActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onViewResults(QuizWithQuestionCount item) {
        Intent intent = new Intent(this, QuizResultsActivity.class);
        intent.putExtra(QuizResultsActivity.EXTRA_QUIZ_ID, item.quiz.getId());
        intent.putExtra(QuizResultsActivity.EXTRA_QUIZ_TITLE, item.quiz.getTitle());
        startActivity(intent);
    }

    @Override
    public void onTogglePublish(QuizWithQuestionCount item) {
        if (!item.quiz.isPublished() && item.questionCount == 0) {
            Toast.makeText(this, R.string.publish_needs_question, Toast.LENGTH_SHORT).show();
            return;
        }
        Quiz quiz = item.quiz;
        quiz.setStatus(quiz.isPublished() ? Quiz.STATUS_DRAFT : Quiz.STATUS_PUBLISHED);
        quizRepository.updateQuiz(quiz, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadQuizzes();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(QuizListActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
