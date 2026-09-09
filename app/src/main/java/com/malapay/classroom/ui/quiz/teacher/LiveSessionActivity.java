package com.malapay.classroom.ui.quiz.teacher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.QuestionDao;
import com.malapay.classroom.data.local.dao.QuizDao;
import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.databinding.ActivityLiveSessionBinding;
import com.malapay.classroom.local.LocalQuizServer;
import com.malapay.classroom.local.QuizSession;
import com.malapay.classroom.ui.quiz.adapter.LiveStudentResultAdapter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Teacher's live quiz session screen. Starts a local HTTP server on the
 * device, displays the IP address for students to connect, allows the
 * teacher to start the quiz, and shows a live scoreboard as students
 * submit their answers.
 */
public class LiveSessionActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ_ID = "extra_quiz_id";

    private ActivityLiveSessionBinding binding;
    private LocalQuizServer server;
    private QuizSession session;
    private LiveStudentResultAdapter adapter;

    private final ExecutorService dbExecutor = AppDatabase.DATABASE_WRITE_EXECUTOR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLiveSessionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(R.string.live_session_title);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> confirmClose());

        long quizId = getIntent().getLongExtra(EXTRA_QUIZ_ID, -1L);
        if (quizId == -1L) {
            Toast.makeText(this, "Invalid quiz", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set up results list
        adapter = new LiveStudentResultAdapter();
        binding.rvLiveResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLiveResults.setAdapter(adapter);

        // Button listeners
        binding.btnCopyIp.setOnClickListener(v -> copyIpAddress());
        binding.btnStartQuiz.setOnClickListener(v -> startQuiz());
        binding.btnEndSession.setOnClickListener(v -> confirmClose());

        // Load quiz and start server
        loadQuizAndStartServer(quizId);
    }

    private void loadQuizAndStartServer(long quizId) {
        dbExecutor.execute(() -> {
            try {
                QuizDao quizDao = AppDatabase.getInstance(this).quizDao();
                QuestionDao questionDao = AppDatabase.getInstance(this).questionDao();

                Quiz quiz = quizDao.findById(quizId);
                if (quiz == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                List<Question> questions = questionDao.getQuestionsForQuiz(quizId);
                if (questions.isEmpty()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.live_session_no_questions, Toast.LENGTH_LONG).show();
                        finish();
                    });
                    return;
                }

                // QuizSession constructor copies correct answers internally
                session = new QuizSession(quiz, questions);

                // Start the server
                server = new LocalQuizServer();
                server.setSession(session);
                server.setSessionListener(new LocalQuizServer.SessionListener() {
                    @Override
                    public void onStudentJoined(String studentName, int totalConnected) {
                        runOnUiThread(() -> updateUI());
                    }

                    @Override
                    public void onAnswerSubmitted(String studentName, int submitted, int totalStudents) {
                        runOnUiThread(() -> updateUI());
                    }

                    @Override
                    public void onSessionStateChanged(String status) {
                        runOnUiThread(() -> updateUI());
                    }
                });

                server.startServer();

                runOnUiThread(() -> {
                    String ip = getDeviceIpAddress();
                    binding.tvIpAddress.setText(ip + ":" + server.getPort());
                    binding.tvStatusText.setText(R.string.live_session_waiting);
                    updateUI();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.live_session_server_error, Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void startQuiz() {
        if (session == null) return;
        if (session.getConnectedCount() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("No students connected")
                    .setMessage("Are you sure you want to start? Students won't be able to join after the quiz begins.")
                    .setNegativeButton(R.string.btn_cancel, null)
                    .setPositiveButton("Start Anyway", (d, w) -> doStartQuiz())
                    .show();
        } else {
            doStartQuiz();
        }
    }

    private void doStartQuiz() {
        session.start();
        binding.btnStartQuiz.setVisibility(View.GONE);
        binding.btnEndSession.setVisibility(View.VISIBLE);
        updateUI();
    }

    private void confirmClose() {
        if (session != null && QuizSession.STATUS_ACTIVE.equals(session.getStatus())) {
            new AlertDialog.Builder(this)
                    .setTitle("End session?")
                    .setMessage("This will close the quiz. Students won't be able to submit after this.")
                    .setNegativeButton(R.string.btn_cancel, null)
                    .setPositiveButton(R.string.live_session_end_session, (d, w) -> closeAndFinish())
                    .show();
        } else {
            closeAndFinish();
        }
    }

    private void closeAndFinish() {
        if (session != null) {
            session.close();
        }
        if (server != null) {
            server.stopServer();
        }
        finish();
    }

    private void updateUI() {
        if (session == null || binding == null) return;

        // Student count
        int connected = session.getConnectedCount();
        binding.tvStudentCount.setText(connected + " connected");

        // Status text
        if (QuizSession.STATUS_WAITING.equals(session.getStatus())) {
            binding.tvStatusText.setText(R.string.live_session_waiting);
            binding.btnStartQuiz.setVisibility(View.VISIBLE);
            binding.btnEndSession.setVisibility(View.GONE);
            binding.resultsHeader.setVisibility(View.GONE);
            binding.rvLiveResults.setVisibility(View.GONE);
        } else if (QuizSession.STATUS_ACTIVE.equals(session.getStatus())) {
            binding.tvStatusText.setText(R.string.live_session_active);
            binding.btnStartQuiz.setVisibility(View.GONE);
            binding.btnEndSession.setVisibility(View.VISIBLE);
            binding.resultsHeader.setVisibility(View.VISIBLE);
            binding.rvLiveResults.setVisibility(View.VISIBLE);

            int submitted = session.getSubmittedCount();
            binding.tvSubmittedCount.setText(submitted + " / " + connected + " submitted");
        } else {
            binding.tvStatusText.setText(R.string.live_session_closed);
            binding.btnStartQuiz.setVisibility(View.GONE);
            binding.btnEndSession.setVisibility(View.GONE);
            binding.resultsHeader.setVisibility(View.VISIBLE);
            binding.rvLiveResults.setVisibility(View.VISIBLE);

            int submitted = session.getSubmittedCount();
            binding.tvSubmittedCount.setText(submitted + " / " + connected + " submitted");
        }

        // Update results list
        adapter.updateData(session.getAllStudents(), session.getAllResults());
    }

    private void copyIpAddress() {
        String ip = binding.tvIpAddress.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("quiz_ip", ip);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.live_session_ip_copied, Toast.LENGTH_SHORT).show();
    }

    /**
     * Detects the device's WiFi or hotspot IP address.
     * Returns a placeholder if detection fails.
     */
    private String getDeviceIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipInt = wifiInfo.getIpAddress();
            if (ipInt != 0) {
                return String.format("%d.%d.%d.%d",
                        ipInt & 0xff, (ipInt >> 8) & 0xff,
                        (ipInt >> 16) & 0xff, (ipInt >> 24) & 0xff);
            }
        } catch (Exception e) {
            // WiFi not available — try other interfaces
        }

        // Fallback: try enumerating network interfaces
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface intf = interfaces.nextElement();
                java.util.Enumeration<java.net.InetAddress> addrs = intf.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return "0.0.0.0";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stopServer();
        }
        if (session != null) {
            session.close();
        }
    }
}
