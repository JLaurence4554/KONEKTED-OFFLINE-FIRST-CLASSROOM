package com.malapay.classroom.ui.quiz.student;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.data.local.entity.QuizResult;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityTakeQuizBinding;
import com.malapay.classroom.databinding.ItemTakeQuizQuestionBinding;
import com.malapay.classroom.util.SessionManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a quiz's questions dynamically, supporting Multiple Choice,
 * Identification, and Essay question types.
 */
public class TakeQuizActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ_ID = "extra_quiz_id";

    private ActivityTakeQuizBinding binding;
    private QuizRepository quizRepository;
    private SessionManager sessionManager;

    private Quiz quiz;
    private List<Question> questions;
    private final Map<Long, ItemTakeQuizQuestionBinding> questionBindings = new LinkedHashMap<>();
    private CountDownTimer countDownTimer;
    private boolean submitted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTakeQuizBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        quizRepository = new QuizRepository(this);
        sessionManager = new SessionManager(this);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());
        binding.btnSubmit.setOnClickListener(v -> confirmSubmit());

        long quizId = getIntent().getLongExtra(EXTRA_QUIZ_ID, -1L);
        loadQuiz(quizId);
    }

    private void loadQuiz(long quizId) {
        quizRepository.getQuizById(quizId, new RepositoryCallback<Quiz>() {
            @Override
            public void onSuccess(Quiz result) {
                quiz = result;
                binding.appHeader.txtHeaderTitle.setText(quiz.getTitle());
                binding.tvInstructions.setText(quiz.getInstructions());
                loadQuestions(quiz.getId());
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TakeQuizActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadQuestions(long quizId) {
        quizRepository.getQuestionsForQuiz(quizId, new RepositoryCallback<List<Question>>() {
            @Override
            public void onSuccess(List<Question> result) {
                questions = result;
                renderQuestions();
                startTimerIfNeeded();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TakeQuizActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void renderQuestions() {
        binding.questionsContainer.removeAllViews();
        questionBindings.clear();

        int number = 1;
        for (Question question : questions) {
            ItemTakeQuizQuestionBinding qBinding = ItemTakeQuizQuestionBinding.inflate(
                    LayoutInflater.from(this), binding.questionsContainer, false);

            qBinding.tvQuestionText.setText(number + ". " + question.getQuestionText());

            if (question.isMultipleChoice()) {
                // Show radio buttons, hide text inputs
                qBinding.radioGroupChoices.setVisibility(View.VISIBLE);
                qBinding.tilIdentificationAnswer.setVisibility(View.GONE);
                qBinding.tilEssayAnswer.setVisibility(View.GONE);
                qBinding.tvQuestionTypeBadge.setVisibility(View.GONE);

                qBinding.radioA.setText(question.getChoiceA());
                qBinding.radioB.setText(question.getChoiceB());
                qBinding.radioC.setText(question.getChoiceC());
                qBinding.radioD.setText(question.getChoiceD());
            } else if (question.isIdentification()) {
                // Show text input, hide radio buttons
                qBinding.radioGroupChoices.setVisibility(View.GONE);
                qBinding.tilIdentificationAnswer.setVisibility(View.VISIBLE);
                qBinding.tilEssayAnswer.setVisibility(View.GONE);
                qBinding.tvQuestionTypeBadge.setVisibility(View.VISIBLE);
                qBinding.tvQuestionTypeBadge.setText("Identification");
            } else if (question.isEssay()) {
                // Show multi-line text input, hide radio buttons
                qBinding.radioGroupChoices.setVisibility(View.GONE);
                qBinding.tilIdentificationAnswer.setVisibility(View.GONE);
                qBinding.tilEssayAnswer.setVisibility(View.VISIBLE);
                qBinding.tvQuestionTypeBadge.setVisibility(View.VISIBLE);
                qBinding.tvQuestionTypeBadge.setText("Essay");
            }

            binding.questionsContainer.addView(qBinding.getRoot());
            questionBindings.put(question.getId(), qBinding);
            number++;
        }
    }

    private void startTimerIfNeeded() {
        if (quiz.getTimeLimitMinutes() == null) {
            return;
        }
        long totalMillis = quiz.getTimeLimitMinutes() * 60_000L;
        binding.tvTimerBar.setVisibility(View.VISIBLE);

        countDownTimer = new CountDownTimer(totalMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = millisUntilFinished / 60_000;
                long seconds = (millisUntilFinished / 1000) % 60;
                binding.tvTimerBar.setText(getString(R.string.time_remaining_label,
                        String.format("%02d:%02d", minutes, seconds)));
            }

            @Override
            public void onFinish() {
                Toast.makeText(TakeQuizActivity.this, R.string.time_up_message, Toast.LENGTH_SHORT).show();
                submitQuiz();
            }
        }.start();
    }

    private void confirmSubmit() {
        if (!allQuestionsAnswered()) {
            Toast.makeText(this, R.string.error_answer_all_questions, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.submit_quiz_confirm_title)
                .setMessage(R.string.submit_quiz_confirm_body)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_submit_quiz, (dialog, which) -> submitQuiz())
                .show();
    }

    /**
     * Checks that every question has been answered. MCQ requires a radio selection,
     * identification requires non-empty text, essay requires non-empty text.
     */
    private boolean allQuestionsAnswered() {
        for (Map.Entry<Long, ItemTakeQuizQuestionBinding> entry : questionBindings.entrySet()) {
            long questionId = entry.getKey();
            ItemTakeQuizQuestionBinding qBinding = entry.getValue();

            // Find the question to check type
            Question question = findQuestionById(questionId);
            if (question == null) return false;

            if (question.isMultipleChoice()) {
                if (qBinding.radioGroupChoices.getCheckedRadioButtonId() == -1) {
                    return false;
                }
            } else if (question.isIdentification()) {
                String answer = qBinding.etIdentificationAnswer.getText() != null
                        ? qBinding.etIdentificationAnswer.getText().toString().trim() : "";
                if (answer.isEmpty()) {
                    return false;
                }
            } else if (question.isEssay()) {
                String answer = qBinding.etEssayAnswer.getText() != null ? qBinding.etEssayAnswer.getText().toString().trim() : "";
                if (answer.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private Question findQuestionById(long id) {
        for (Question q : questions) { if (q.getId() == id) return q; }
        return null;
    }

    private void submitQuiz() {
        if (submitted) return;
        submitted = true;
        if (countDownTimer != null) countDownTimer.cancel();
        binding.btnSubmit.setEnabled(false);
        Map<Long, String> answers = new LinkedHashMap<>();
        for (Map.Entry<Long, ItemTakeQuizQuestionBinding> entry : questionBindings.entrySet()) {
            long qid = entry.getKey();
            ItemTakeQuizQuestionBinding qb = entry.getValue();
            Question question = findQuestionById(qid);
            if (question == null) continue;
            String answer = null;
            if (question.isMultipleChoice()) {
                int checkedId = qb.radioGroupChoices.getCheckedRadioButtonId();
                if (checkedId == qb.radioA.getId()) answer = "A";
                else if (checkedId == qb.radioB.getId()) answer = "B";
                else if (checkedId == qb.radioC.getId()) answer = "C";
                else if (checkedId == qb.radioD.getId()) answer = "D";
            } else if (question.isIdentification()) {
                answer = qb.etIdentificationAnswer.getText() != null ? qb.etIdentificationAnswer.getText().toString().trim() : "";
            } else if (question.isEssay()) {
                answer = qb.etEssayAnswer.getText() != null ? qb.etEssayAnswer.getText().toString().trim() : "";
            }
            if (answer != null) answers.put(qid, answer);
        }
        long studentId = sessionManager.getUserId();
        quizRepository.submitQuizResult(quiz.getId(), studentId, answers, new RepositoryCallback<QuizResult>() {
            public void onSuccess(QuizResult result) {
                quizRepository.pushQuizResultToServer(result);
                Intent intent = new Intent(TakeQuizActivity.this, QuizResultActivity.class);
                intent.putExtra(QuizResultActivity.EXTRA_QUIZ_ID, quiz.getId());
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
            public void onError(String message) { Toast.makeText(TakeQuizActivity.this, message, Toast.LENGTH_SHORT).show(); finish(); }
        });
    }

    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
