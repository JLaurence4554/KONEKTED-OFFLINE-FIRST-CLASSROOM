package com.malapay.classroom.ui.quiz.teacher;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.data.local.entity.QuizResult;
import com.malapay.classroom.data.local.model.QuizResultWithStudentName;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityEssayGradingBinding;
import com.malapay.classroom.databinding.ItemEssayGradingBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class EssayGradingActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT_ID = "extra_result_id";
    public static final String EXTRA_QUIZ_ID = "extra_quiz_id";
    public static final String EXTRA_STUDENT_NAME = "extra_student_name";

    private ActivityEssayGradingBinding binding;
    private QuizRepository quizRepository;
    private long quizId;
    private long resultId;
    private String studentName;
    private QuizResult currentResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEssayGradingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        quizRepository = new QuizRepository(this);

        resultId = getIntent().getLongExtra(EXTRA_RESULT_ID, -1L);
        quizId = getIntent().getLongExtra(EXTRA_QUIZ_ID, -1L);
        studentName = getIntent().getStringExtra(EXTRA_STUDENT_NAME);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText("Grade: " + (studentName != null ? studentName : "Student"));
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());
        binding.tvStudentName.setText(studentName);
        binding.btnSaveGrade.setOnClickListener(v -> saveGrade());
        loadResult();
    }

    private void loadResult() {
        quizRepository.getResultsForQuiz(quizId, new RepositoryCallback<List<QuizResultWithStudentName>>() {
            @Override
            public void onSuccess(List<QuizResultWithStudentName> result) {
                for (QuizResultWithStudentName item : result) {
                    if (item.result.getId() == resultId) {
                        currentResult = item.result;
                        displayResult(item);
                        return;
                    }
                }
                Toast.makeText(EssayGradingActivity.this, "Result not found.", Toast.LENGTH_SHORT).show();
                finish();
            }
            @Override
            public void onError(String message) {
                Toast.makeText(EssayGradingActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void displayResult(QuizResultWithStudentName item) {
        binding.tvAutoScore.setText("Auto-score: " + item.result.getScore() + "% (" + item.result.getCorrectCount() + "/" + item.result.getTotalQuestions() + ")");
        if (item.result.getGradedScore() != null) {
            binding.etGradedScore.setText(String.valueOf(item.result.getGradedScore()));
        }
        String essayAnswersJson = item.result.getEssayAnswers();
        if (essayAnswersJson == null || essayAnswersJson.isEmpty()) {
            Toast.makeText(this, "No essay answers to grade.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject answers = new JSONObject(essayAnswersJson);
            binding.answersContainer.removeAllViews();
            quizRepository.getQuestionsForQuiz(quizId, new RepositoryCallback<List<Question>>() {
                @Override
                public void onSuccess(List<Question> questions) {
                    int questionNum = 1;
                    for (Question q : questions) {
                        if (!q.isMultipleChoice()) {
                            String studentAnswer = answers.optString(String.valueOf(q.getId()), "");
                            addAnswerView(questionNum, q, studentAnswer);
                            questionNum++;
                        }
                    }
                }
                @Override
                public void onError(String message) { }
            });
        } catch (JSONException e) {
            Toast.makeText(this, "Could not parse essay answers.", Toast.LENGTH_SHORT).show();
        }
    }

    private void addAnswerView(int questionNum, Question question, String studentAnswer) {
        ItemEssayGradingBinding itemBinding = ItemEssayGradingBinding.inflate(
                LayoutInflater.from(this), binding.answersContainer, false);
        itemBinding.tvQuestionLabel.setText("Q" + questionNum + " (" + question.getTypeLabel() + ")");
        itemBinding.tvStudentAnswer.setText(studentAnswer.isEmpty() ? "(No answer)" : studentAnswer);
        if (question.getAnswerText() != null && !question.getAnswerText().isEmpty()) {
            itemBinding.tvModelAnswer.setText(question.getAnswerText());
        } else {
            itemBinding.tvModelAnswer.setText("(No model answer provided)");
        }
        binding.answersContainer.addView(itemBinding.getRoot());
    }

    private void saveGrade() {
        String scoreText = binding.etGradedScore.getText() != null ? binding.etGradedScore.getText().toString().trim() : "";
        if (scoreText.isEmpty()) {
            binding.tilGradedScore.setError(getString(R.string.error_field_required));
            return;
        }
        int gradedScore;
        try {
            gradedScore = Integer.parseInt(scoreText);
            if (gradedScore < 0 || gradedScore > 100) {
                binding.tilGradedScore.setError("Score must be 0-100");
                return;
            }
        } catch (NumberFormatException e) {
            binding.tilGradedScore.setError("Enter a valid number");
            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSaveGrade.setEnabled(false);
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            quizRepository.getQuizResultDao().updateGradedScore(currentResult.getId(), gradedScore);
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Grade saved!", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}