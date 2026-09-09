package com.malapay.classroom.ui.quiz.student;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.malapay.classroom.R;
import com.malapay.classroom.databinding.ActivityJoinQuizBinding;
import com.malapay.classroom.util.ValidationUtil;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Student's screen for joining a live quiz session. The student enters the
 * teacher's IP address and their name, then connects to the teacher's
 * local server. After connecting, they wait for the teacher to start the quiz.
 *
 * Once the teacher starts, this activity launches LocalQuizActivity
 * with the quiz data received from the server.
 */
public class JoinQuizActivity extends AppCompatActivity {

    private ActivityJoinQuizBinding binding;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isConnecting = false;
    private String teacherHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityJoinQuizBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(R.string.join_quiz_title);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        binding.btnConnect.setOnClickListener(v -> attemptConnect());
    }

    private void attemptConnect() {
        binding.tilStudentName.setError(null);
        binding.tilIpAddress.setError(null);

        String name = textOf(binding.etStudentName);
        String ip = textOf(binding.etIpAddress);

        if (!ValidationUtil.isNotEmpty(name)) {
            binding.tilStudentName.setError(getString(R.string.join_quiz_name_required));
            return;
        }
        if (!ValidationUtil.isNotEmpty(ip)) {
            binding.tilIpAddress.setError(getString(R.string.join_quiz_ip_required));
            return;
        }

        // Normalize IP: add :8080 if no port specified
        String host = ip.trim();
        if (!host.contains(":")) {
            host = host + ":8080";
        }
        teacherHost = "http://" + host;

        setConnecting(true);
        connectAndJoin(name);
    }

    private void connectAndJoin(String studentName) {
        new Thread(() -> {
            try {
                // Step 1: Join the session
                JSONObject joinRequest = new JSONObject();
                joinRequest.put("name", studentName);

                JSONObject joinResponse = httpPost(teacherHost + "/join", joinRequest.toString());

                if (joinResponse == null) {
                    postError(getString(R.string.join_quiz_error));
                    return;
                }

                if (joinResponse.has("error")) {
                    postError(joinResponse.getString("error"));
                    return;
                }

                long studentId = joinResponse.getLong("studentId");
                String quizTitle = joinResponse.optString("quizTitle", "Live Quiz");
                String instructions = joinResponse.optString("instructions", "");
                String status = joinResponse.optString("status", "waiting");

                // Step 2: If quiz is already active, go straight to taking it
                if ("active".equals(status)) {
                    JSONObject quizData = httpGet(teacherHost + "/quiz");
                    if (quizData != null) {
                        launchQuiz(studentId, studentName, quizData);
                        return;
                    }
                }

                // Step 3: Wait for teacher to start (poll /status every 2 seconds)
                mainHandler.post(() -> {
                    setConnecting(false);
                    showWaiting(studentName, studentId, quizTitle, instructions);
                });

                pollForStart(studentId, studentName, quizTitle, instructions);

            } catch (Exception e) {
                postError(getString(R.string.join_quiz_error));
            }
        }).start();
    }

    private void pollForStart(long studentId, String studentName,
                               String quizTitle, String instructions) {
        new Thread(() -> {
            while (!isFinishing()) {
                try {
                    Thread.sleep(2000); // Poll every 2 seconds

                    JSONObject status = httpGet(teacherHost + "/status");
                    if (status == null) continue;

                    String sessionStatus = status.optString("status", "waiting");
                    if ("active".equals(sessionStatus)) {
                        // Quiz started! Fetch questions and launch
                        JSONObject quizData = httpGet(teacherHost + "/quiz");
                        if (quizData != null) {
                            launchQuiz(studentId, studentName, quizData);
                            return;
                        }
                    } else if ("closed".equals(sessionStatus)) {
                        postError("Session ended by teacher");
                        return;
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    // Keep polling
                }
            }
        }).start();
    }

    private void launchQuiz(long studentId, String studentName, JSONObject quizData) {
        mainHandler.post(() -> {
            Intent intent = new Intent(this, LocalQuizActivity.class);
            intent.putExtra(LocalQuizActivity.EXTRA_STUDENT_ID, studentId);
            intent.putExtra(LocalQuizActivity.EXTRA_STUDENT_NAME, studentName);
            intent.putExtra(LocalQuizActivity.EXTRA_HOST, teacherHost);
            intent.putExtra(LocalQuizActivity.EXTRA_QUIZ_JSON, quizData.toString());
            startActivity(intent);
            finish();
        });
    }

    private void showWaiting(String studentName, long studentId, String quizTitle, String instructions) {
        binding.progressBar.setVisibility(View.GONE);
        binding.btnConnect.setVisibility(View.GONE);
        binding.tilStudentName.setEnabled(false);
        binding.tilIpAddress.setEnabled(false);

        binding.tvStatusMessage.setVisibility(View.VISIBLE);
        binding.tvStatusMessage.setText(getString(R.string.join_quiz_waiting));
    }

    private void setConnecting(boolean connecting) {
        isConnecting = connecting;
        binding.progressBar.setVisibility(connecting ? View.VISIBLE : View.GONE);
        binding.btnConnect.setEnabled(!connecting);
        binding.btnConnect.setText(connecting ? R.string.btn_connecting : R.string.btn_connect);
    }

    private void postError(String message) {
        mainHandler.post(() -> {
            setConnecting(false);
            binding.tvStatusMessage.setVisibility(View.VISIBLE);
            binding.tvStatusMessage.setText(message);
            binding.tvStatusMessage.setTextColor(
                    getResources().getColor(R.color.status_offline, getTheme()));
        });
    }

    // ------------------------------------------------------------------
    // HTTP helpers
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

    private JSONObject httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

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

    private String textOf(com.google.android.material.textfield.TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
