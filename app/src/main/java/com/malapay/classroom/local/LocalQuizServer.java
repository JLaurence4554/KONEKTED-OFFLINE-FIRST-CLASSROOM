package com.malapay.classroom.local;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Lightweight embedded HTTP server that runs on the teacher's device during
 * a live quiz session. Students on the same WiFi network connect to this
 * server to fetch questions and submit answers.
 *
 * Endpoints:
 *   GET  /quiz    → Quiz metadata + questions (no correct answers)
 *   GET  /status  → Session status (waiting/active/closed), connected & submitted counts
 *   POST /join    → Student sends {"name": "..."} → receives {studentId, quiz}
 *   POST /submit  → Student sends {"studentId": N, "answers": {qId: "A"}} → receives {score}
 *
 * All responses are JSON with Content-Type: application/json.
 */
public class LocalQuizServer extends NanoHTTPD {

    private static final String TAG = "LocalQuizServer";
    private static final int PORT = 8080;

    private QuizSession session;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    /** Callback to notify the UI when something changes. */
    public interface SessionListener {
        void onStudentJoined(String studentName, int totalConnected);
        void onAnswerSubmitted(String studentName, int submitted, int totalStudents);
        void onSessionStateChanged(String status);
    }

    private SessionListener listener;

    public LocalQuizServer() {
        super(PORT);
    }

    public void setSessionListener(SessionListener listener) {
        this.listener = listener;
    }

    /**
     * Start serving a quiz session. Loads quiz data from the database
     * must be called before start().
     */
    public void setSession(QuizSession session) {
        this.session = session;
    }

    public QuizSession getSession() {
        return session;
    }

    public int getPort() {
        return PORT;
    }

    public void startServer() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        Log.i(TAG, "Server started on port " + PORT);
    }

    public void stopServer() {
        stop();
        Log.i(TAG, "Server stopped");
    }

    @Override
    public Response serve(IHTTPSession request) {
        String uri = request.getUri();
        Method method = request.getMethod();

        Log.d(TAG, method + " " + uri);

        try {
            // CORS headers for all responses
            if (Method.OPTIONS.equals(method)) {
                Response resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
                addCorsHeaders(resp);
                return resp;
            }

            if ("/quiz".equals(uri) && Method.GET.equals(method)) {
                return handleGetQuiz();
            } else if ("/status".equals(uri) && Method.GET.equals(method)) {
                return handleGetStatus();
            } else if ("/join".equals(uri) && Method.POST.equals(method)) {
                return handleJoin(request);
            } else if ("/submit".equals(uri) && Method.POST.equals(method)) {
                return handleSubmit(request);
            } else {
                return jsonResponse(Response.Status.NOT_FOUND,
                        new JSONObject().put("error", "Not found"));
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON error", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR,
                    errorJson("Server error"));
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR,
                    errorJson("Server error: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // GET /quiz — serve quiz questions (NO correct answers)
    // ------------------------------------------------------------------

    private Response handleGetQuiz() throws JSONException {
        if (session == null) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("No active session"));
        }

        JSONObject quiz = new JSONObject();
        quiz.put("quizId", session.getQuizId());
        quiz.put("title", session.getQuizTitle());
        quiz.put("instructions", session.getInstructions());
        quiz.put("timeLimitMinutes", session.getTimeLimitMinutes());
        quiz.put("status", session.getStatus());

        JSONArray questions = new JSONArray();
        for (QuizSession.QuestionData q : session.getQuestions()) {
            JSONObject qObj = new JSONObject();
            qObj.put("id", q.id);
            qObj.put("text", q.text);
            qObj.put("choiceA", q.choiceA);
            qObj.put("choiceB", q.choiceB);
            qObj.put("choiceC", q.choiceC);
            qObj.put("choiceD", q.choiceD);
            qObj.put("orderIndex", q.orderIndex);
            // NOTE: correctAnswer is NOT included
            questions.put(qObj);
        }
        quiz.put("questions", questions);

        Response resp = jsonResponse(Response.Status.OK, quiz);
        addCorsHeaders(resp);
        return resp;
    }

    // ------------------------------------------------------------------
    // GET /status — session status + counts
    // ------------------------------------------------------------------

    private Response handleGetStatus() throws JSONException {
        if (session == null) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("No active session"));
        }

        JSONObject status = new JSONObject();
        status.put("status", session.getStatus());
        status.put("quizTitle", session.getQuizTitle());
        status.put("connected", session.getConnectedCount());
        status.put("submitted", session.getSubmittedCount());
        status.put("totalStudents", session.getConnectedCount());
        status.put("serverTime", System.currentTimeMillis());

        Response resp = jsonResponse(Response.Status.OK, status);
        addCorsHeaders(resp);
        return resp;
    }

    // ------------------------------------------------------------------
    // POST /join — register a student
    // ------------------------------------------------------------------

    private Response handleJoin(IHTTPSession request) throws JSONException {
        if (session == null) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("No active session"));
        }
        if (QuizSession.STATUS_CLOSED.equals(session.getStatus())) {
            return jsonResponse(Response.Status.FORBIDDEN, errorJson("Session is closed"));
        }

        Map<String, String> body = parseJsonBody(request);
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("Name is required"));
        }

        long studentId = session.joinStudent(name.trim());
        Log.i(TAG, "Student joined: " + name.trim() + " (id=" + studentId + ")");

        JSONObject response = new JSONObject();
        response.put("studentId", studentId);
        response.put("quizTitle", session.getQuizTitle());
        response.put("instructions", session.getInstructions());
        response.put("status", session.getStatus());

        Response resp = jsonResponse(Response.Status.OK, response);
        addCorsHeaders(resp);

        // Notify UI
        notifyUI(() -> {
            if (listener != null) {
                listener.onStudentJoined(name.trim(), session.getConnectedCount());
            }
        });

        return resp;
    }

    // ------------------------------------------------------------------
    // POST /submit — receive student answers
    // ------------------------------------------------------------------

    private Response handleSubmit(IHTTPSession request) throws JSONException {
        if (session == null) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("No active session"));
        }
        if (!QuizSession.STATUS_ACTIVE.equals(session.getStatus())) {
            return jsonResponse(Response.Status.FORBIDDEN,
                    errorJson("Quiz is not active yet"));
        }

        Map<String, String> body = parseJsonBody(request);

        // Parse studentId
        String studentIdStr = body.get("studentId");
        if (studentIdStr == null) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("studentId required"));
        }
        long studentId;
        try {
            studentId = Long.parseLong(studentIdStr);
        } catch (NumberFormatException e) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("Invalid studentId"));
        }

        QuizSession.StudentEntry student = session.getStudent(studentId);
        if (student == null) {
            return jsonResponse(Response.Status.FORBIDDEN, errorJson("Student not found"));
        }
        if (session.hasStudentSubmitted(studentId)) {
            return jsonResponse(Response.Status.CONFLICT, errorJson("Already submitted"));
        }

        // Parse answers: {"1": "A", "2": "C", ...}
        String answersJson = body.get("answers");
        Map<Long, String> answers = new HashMap<>();
        if (answersJson != null) {
            JSONObject answersObj = new JSONObject(answersJson);
            for (java.util.Iterator<String> it = answersObj.keys(); it.hasNext(); ) {
                String key = it.next();
                try {
                    long qId = Long.parseLong(key);
                    String answer = answersObj.getString(key);
                    answers.put(qId, answer);
                } catch (NumberFormatException e) {
                    // Skip invalid keys
                }
            }
        }

        QuizSession.ResultEntry result = session.submitAnswers(studentId, answers);
        if (result == null) {
            return jsonResponse(Response.Status.INTERNAL_ERROR, errorJson("Failed to score"));
        }

        Log.i(TAG, "Submission from " + student.name + ": " + result.scorePercent + "%");

        JSONObject response = new JSONObject();
        response.put("score", result.scorePercent);
        response.put("correct", result.correctCount);
        response.put("total", result.totalQuestions);
        response.put("passed", result.scorePercent >= 70); // Simple threshold

        Response resp = jsonResponse(Response.Status.OK, response);
        addCorsHeaders(resp);

        // Notify UI
        notifyUI(() -> {
            if (listener != null) {
                listener.onAnswerSubmitted(
                        student.name,
                        session.getSubmittedCount(),
                        session.getConnectedCount());
            }
        });

        return resp;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Map<String, String> parseJsonBody(IHTTPSession request) {
        Map<String, String> bodyMap = new HashMap<>();
        try {
            Map<String, String> body = new HashMap<>();
            request.parseBody(body);
            // NanoHTTPD puts POST body in "postData" key
            String postData = body.get("postData");
            if (postData != null) {
                JSONObject json = new JSONObject(postData);
                for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String key = it.next();
                    bodyMap.put(key, json.get(key).toString());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse body", e);
        }
        return bodyMap;
    }

    private Response jsonResponse(Response.Status status, JSONObject json) {
        return newFixedLengthResponse(status, "application/json", json.toString());
    }

    private JSONObject errorJson(String message) {
        try {
            return new JSONObject().put("error", message);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    private void notifyUI(Runnable action) {
        mainThreadHandler.post(action);
    }
}
