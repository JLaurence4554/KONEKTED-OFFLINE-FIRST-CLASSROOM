package com.malapay.classroom.ui.quiz.teacher;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.content.Intent;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.QuizResultWithStudentName;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityQuizResultsBinding;
import com.malapay.classroom.ui.quiz.adapter.QuizResultAdapter;

import java.util.List;

/** Shows every student's submission (score, correct count, date) for a single quiz. */
public class QuizResultsActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ_ID = "extra_quiz_id";
    public static final String EXTRA_QUIZ_TITLE = "extra_quiz_title";

    private ActivityQuizResultsBinding binding;
    private QuizRepository quizRepository;
    private QuizResultAdapter adapter;
    private long quizId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        quizRepository = new QuizRepository(this);

        quizId = getIntent().getLongExtra(EXTRA_QUIZ_ID, -1L);
        String quizTitle = getIntent().getStringExtra(EXTRA_QUIZ_TITLE);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(quizTitle != null ? quizTitle : getString(R.string.results_title));
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        adapter = new QuizResultAdapter();
        adapter.setOnResultClickListener(item -> {
            Intent intent = new Intent(this, EssayGradingActivity.class);
            intent.putExtra(EssayGradingActivity.EXTRA_RESULT_ID, item.result.getId());
            intent.putExtra(EssayGradingActivity.EXTRA_QUIZ_ID, quizId);
            intent.putExtra(EssayGradingActivity.EXTRA_STUDENT_NAME, item.studentName);
            startActivity(intent);
        });
        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerResults.setAdapter(adapter);

        loadResults();
    }

    private void loadResults() {
        // Pull results from server first (merges into local DB), then display
        quizRepository.pullQuizResultsFromServer(quizId, new RepositoryCallback<List<QuizResultWithStudentName>>() {
            @Override
            public void onSuccess(List<QuizResultWithStudentName> result) {
                adapter.submitList(result);
                boolean isEmpty = result == null || result.isEmpty();
                binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerResults.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                // Fallback to local results
                quizRepository.getResultsForQuiz(quizId, new RepositoryCallback<List<QuizResultWithStudentName>>() {
                    @Override
                    public void onSuccess(List<QuizResultWithStudentName> result) {
                        adapter.submitList(result);
                        boolean isEmpty = result == null || result.isEmpty();
                        binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                        binding.recyclerResults.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    }
                    @Override
                    public void onError(String message2) {
                        Toast.makeText(QuizResultsActivity.this, message2, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
