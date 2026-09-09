package com.malapay.classroom.ui.quiz.student;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.malapay.classroom.R;
import com.malapay.classroom.databinding.ActivityLocalQuizBinding;
import com.malapay.classroom.databinding.ItemTakeQuizQuestionBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Student's quiz-taking screen for a live quiz served from the teacher's
 * local device. Displays questions, runs the countdown timer, and submits
 * answers to the teacher's server for immediate scoring.
 */
public class LocalQuizActivity extends AppCompatActivity {

    public static final String EXTRA_STUDENT_ID = "extra_student_id";
    public static final String EXTRA_STUDENT_NAME = "extra_student_name";
    public static final String EXTRA_HOST = "extra_host";
    public static final String EXTRA_QUIZ_JSON = "extra_quiz_json";

    private ActivityLocalQuizBinding binding;
    private long studentId;
    private String studentName;
    private String host;

    private String quizTitle;
    private String instructions;
    private Integer timeLimitMinutes;
    private JSONArray questionsArray;
    private final Map<Long, ItemTakeQuizQuestionBinding> questionBindings = new LinkedHashMap<>();
    private CountDownTimer countDownTimer;
    private boolean submitted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocalQuizBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        studentId = getIntent().getLongExtra(EXTRA_STUDENT_ID, -1L);
        studentName = getIntent().getStringExtra(EXTRA_STUDENT_NAME);
        host = getIntent().getStringExtra(EXTRA_HOST);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(R.string.local_quiz_title);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> {
            if (!submitted) {
                confirmSubmit();
            } else {
                finish();
            }
        });

        binding.btnSubmit.setOnClickListener(v -> confirmSubmit());

        parseQuizJson(getIntent().getStringExtra(EXTRA_QUIZ_JSON));
    }

    private void parseQuizJson(String jsonStr) {
        try {
            JSONObject quiz = new JSONObject(jsonStr);
            quizTitle = quiz.optString("title", "Live Quiz");
            instructions = quiz.optString("instructions", "");
            if (quiz.isNull("timeLimitMinutes")) {
                timeLimitMinutes = null;
            } else {
                timeLimitMinutes = quiz.getInt("timeLimitMinutes");
            }
            questionsArray = quiz.getJSONArray("questions");

            binding.appHeader.txtHeaderTitle.setText(quizTitle);
            binding.tvInstructions.setText(instructions);

            renderQuestions();
            startTimerIfNeeded();

            // Show the quiz, hide waiting/result
            binding.waitingContainer.setVisibility(View.GONE);
            binding.quizContainer.setVisibility(View.VISIBLE);
            binding.resultContainer.setVisibility(View.GONE);

        } catch (Exception e) {
            Toast.makeText(this, "Error loading quiz", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void renderQuestions() {
        binding.questionsContainer.removeAllViews();
        questionBindings.clear();

        try {
            for (int i = 0; i < questionsArray.length(); i++) {
                JSONObject q = questionsArray.getJSONObject(i);
                long qId = q.getLong("id");

                ItemTakeQuizQuestionBinding qBinding = ItemTakeQuizQuestionBinding.inflate(
                        LayoutInflater.from(this), binding.questionsContainer, false);

                qBinding.tvQuestionText.setText((i + 1) + ". " + q.getString("text"));
                qBinding.radioA.setText(q.getString("choiceA"));
                qBinding.radioB.setText(q.getString("choiceB"));
                qBinding.radioC.setText(q.getString("choiceC"));
                qBinding.radioD.setText(q.getString("choiceD"));

                binding.questionsContainer.addView(qBinding.getRoot());
                questionBindings.put(qId, qBinding);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error rendering questions", Toast.LENGTH_SHORT).show();
        }
    }

    private void startTimerIfNeeded() {
        if (timeLimitMinutes == null || timeLimitMinutes <= 0) {
            return;
        }
        long totalMillis = timeLimitMinutes * 60_000L;
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
                Toast.makeText(LocalQuizActivity.this,
                        R.string.time_up_message, Toast.LENGTH_SHORT).show();
                submitQuiz();
            }
        }.start();
    }

    private void confirmSubmit() {
        if (!allQuestionsAnswered()) {
            Toast.makeText(this, R.string.error_answer_all_questions,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.submit_quiz_confirm_title)
                .setMessage(R.string.submit_quiz_confirm_body)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_submit_quiz, (d, w) -> submitQuiz())
                .show();
    }

    private boolean allQuestionsAnswered() {
        for (ItemTakeQuizQuestionBinding qBinding : questionBindings.values()) {
            if (qBinding.radioGroupChoices.getCheckedRadioButtonId() == -1) {
                return false;
            }
        }
        return true;
    }

    private void submitQuiz() {
        if (submitted) return;
        submitted = true;
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        binding.btnSubmit.setEnabled(false);

        // Collect answers
        Map<Long, String> answers = new LinkedHashMap<>();
        for (Map.Entry<Long, ItemTakeQuizQuestionBinding> entry : questionBindings.entrySet()) {
            ItemTakeQuizQuestionBinding qBinding = entry.getValue();
            String answer = null;
            int checkedId = qBinding.radioGroupChoices.getCheckedRadioButtonId();
            if (checkedId == qBinding.radioA.getId()) answer = "A";
            else if (checkedId == qBinding.radioB.getId()) answer = "B";
            else if (checkedId == qBinding.radioC.getId()) answer = "C";
            else if (checkedId == qBinding.radioD.getId()) answer = "D";
            if (answer != null) {
                answers.put(entry.getKey(), answer);
            }
        }

        // Send to server
        new Thread(() -> {
            try {
                JSONObject submitBody = new JSONObject();
                submitBody.put("studentId", studentId);

                JSONObject answersJson = new JSONObject();
                for (Map.Entry<Long, String> entry : answers.entrySet()) {
                    answersJson.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                submitBody.put("answers", answersJson);

                JSONObject response = httpPost(host + "/submit", submitBody.toString());

                if (response != null && !response.has("error")) {
                    int score = response.getInt("score");
                    int correct = response.getInt("correct");
                    int total = response.getInt("total");
                    boolean passed = response.optBoolean("passed", score >= 70);

                    runOnUiThread(() -> showResult(score, correct, total, passed));
                } else {
                    String errorMsg = response != null ? response.optString("error", "Failed") : "No connection";
                    runOnUiThread(() -> {
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to submit. Please try again.",
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }).start();
    }

    private void showResult(int score, int correct, int total, boolean passed) {
        binding.quizContainer.setVisibility(View.GONE);
        binding.resultContainer.setVisibility(View.VISIBLE);
        binding.tvTimerBar.setVisibility(View.GONE);

        binding.tvScore.setText(getString(R.string.local_quiz_score_format, score));
        binding.tvScore.setTextColor(getResources().getColor(
                passed ? R.color.status_synced : R.color.status_offline, getTheme()));

        binding.tvPassStatus.setText(passed ? R.string.local_quiz_passed : R.string.local_quiz_failed);
        binding.tvPassStatus.setTextColor(getResources().getColor(
                passed ? R.color.status_synced : R.color.status_offline, getTheme()));

        binding.tvCorrectCount.setText(getString(R.string.local_quiz_correct_format, correct, total));
    }

    // ------------------------------------------------------------------
    // HTTP helper
    // ------------------------------------------------------------------

    private JSONObject httpPost(String urlStr, String jsonBody) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(code >= 200 && code < 300
                            ? conn.getInputStream() : conn.getErrorStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();

            return new JSONObject(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
