package com.malapay.classroom.ui.quiz.student;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.data.local.entity.QuizResult;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityQuizResultBinding;
import com.malapay.classroom.util.SessionManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Shows the logged-in student's own result for a quiz they've already submitted. */
public class QuizResultActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ_ID = "extra_quiz_id";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());

    private ActivityQuizResultBinding binding;
    private QuizRepository quizRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        quizRepository = new QuizRepository(this);
        sessionManager = new SessionManager(this);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(R.string.quiz_result_title);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());
        binding.btnBackToLesson.setOnClickListener(v -> finish());

        long quizId = getIntent().getLongExtra(EXTRA_QUIZ_ID, -1L);
        loadResult(quizId);
    }

    private void loadResult(long quizId) {
        long studentId = sessionManager.getUserId();

        quizRepository.getQuizById(quizId, new RepositoryCallback<Quiz>() {
            @Override
            public void onSuccess(Quiz quiz) {
                quizRepository.getMyResult(quizId, studentId, new RepositoryCallback<QuizResult>() {
                    @Override
                    public void onSuccess(QuizResult result) {
                        bind(quiz, result);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(QuizResultActivity.this, message, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(QuizResultActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void bind(Quiz quiz, QuizResult result) {
        binding.tvScore.setText(getString(R.string.score_label, result.getScore()));
        binding.tvCorrectCount.setText(getString(R.string.correct_count_label,
                result.getCorrectCount(), result.getTotalQuestions()));
        binding.tvDateTaken.setText(getString(R.string.result_date_taken, DATE_FORMAT.format(new Date(result.getSubmittedAt()))));

        boolean passed = result.getScore() >= quiz.getPassingScore();
        binding.tvStatus.setText(passed ? R.string.result_passed : R.string.result_failed);
        binding.tvStatus.setTextColor(getResources().getColor(passed ? R.color.secondary : R.color.error));
    }
}
