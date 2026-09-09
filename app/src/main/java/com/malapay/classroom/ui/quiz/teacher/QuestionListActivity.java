package com.malapay.classroom.ui.quiz.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityQuestionListBinding;
import com.malapay.classroom.ui.quiz.adapter.QuestionAdapter;

import java.util.Collections;
import java.util.List;

/** Manages the questions belonging to one quiz: add, edit (tap a row), delete, and reorder. */
public class QuestionListActivity extends AppCompatActivity implements QuestionAdapter.Listener {

    public static final String EXTRA_QUIZ_ID = "extra_quiz_id";
    public static final String EXTRA_QUIZ_TITLE = "extra_quiz_title";

    private ActivityQuestionListBinding binding;
    private QuizRepository quizRepository;
    private QuestionAdapter adapter;
    private long quizId;
    private List<Question> currentQuestions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuestionListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        quizRepository = new QuizRepository(this);

        quizId = getIntent().getLongExtra(EXTRA_QUIZ_ID, -1L);
        String quizTitle = getIntent().getStringExtra(EXTRA_QUIZ_TITLE);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(quizTitle != null ? quizTitle : getString(R.string.questions_title));
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        adapter = new QuestionAdapter(this);
        binding.recyclerQuestions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerQuestions.setAdapter(adapter);

        binding.fabAddQuestion.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateEditQuestionActivity.class);
            intent.putExtra(CreateEditQuestionActivity.EXTRA_QUIZ_ID, quizId);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadQuestions();
    }

    private void loadQuestions() {
        quizRepository.getQuestionsForQuiz(quizId, new RepositoryCallback<List<Question>>() {
            @Override
            public void onSuccess(List<Question> result) {
                currentQuestions = result;
                adapter.submitList(result);
                boolean isEmpty = result == null || result.isEmpty();
                binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerQuestions.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(QuestionListActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onQuestionClick(Question question) {
        Intent intent = new Intent(this, CreateEditQuestionActivity.class);
        intent.putExtra(CreateEditQuestionActivity.EXTRA_QUIZ_ID, quizId);
        intent.putExtra(CreateEditQuestionActivity.EXTRA_QUESTION_ID, question.getId());
        startActivity(intent);
    }

    @Override
    public void onDeleteQuestion(Question question) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_question_title)
                .setMessage(R.string.delete_question_body)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        quizRepository.deleteQuestion(question, new RepositoryCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                loadQuestions();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(QuestionListActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        }))
                .show();
    }

    @Override
    public void onMoveUp(int position) {
        if (currentQuestions == null || position <= 0) return;
        Collections.swap(currentQuestions, position, position - 1);
        persistReorder();
    }

    @Override
    public void onMoveDown(int position) {
        if (currentQuestions == null || position >= currentQuestions.size() - 1) return;
        Collections.swap(currentQuestions, position, position + 1);
        persistReorder();
    }

    private void persistReorder() {
        adapter.submitList(currentQuestions);
        quizRepository.reorderQuestions(currentQuestions, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadQuestions();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(QuestionListActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
