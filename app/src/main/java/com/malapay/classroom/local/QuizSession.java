package com.malapay.classroom.local;

import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.data.local.entity.Quiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state for a live quiz session hosted by the teacher's device.
 * Thread-safe: the NanoHTTPD server handles requests on multiple threads,
 * so all mutable access goes through synchronized blocks or concurrent maps.
 */
public class QuizSession {

    public static final String STATUS_WAITING = "waiting";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CLOSED = "closed";

    private final long quizId;
    private final String quizTitle;
    private final String instructions;
    private final Integer timeLimitMinutes;
    private final List<QuestionData> questions;
    private final long serverStartTimeMillis;

    private volatile String status = STATUS_WAITING;

    /** Connected students: studentId → StudentEntry. */
    private final ConcurrentHashMap<Long, StudentEntry> students = new ConcurrentHashMap<>();
    private long nextStudentId = 1;

    /** Submitted answers: studentId → {questionId → "A"/"B"/"C"/"D"}. */
    private final ConcurrentHashMap<Long, Map<Long, String>> submissions = new ConcurrentHashMap<>();

    /** Committed results: studentId → ResultEntry (score computed on submit). */
    private final ConcurrentHashMap<Long, ResultEntry> results = new ConcurrentHashMap<>();

    private final long createdAtMillis;

    public QuizSession(Quiz quiz, List<Question> questionEntities) {
        this.quizId = quiz.getId();
        this.quizTitle = quiz.getTitle();
        this.instructions = quiz.getInstructions();
        this.timeLimitMinutes = quiz.getTimeLimitMinutes();
        this.serverStartTimeMillis = System.currentTimeMillis();
        this.createdAtMillis = System.currentTimeMillis();

        // Copy questions (correct answer kept server-side only, never sent to students)
        List<QuestionData> safeQuestions = new ArrayList<>();
        for (Question q : questionEntities) {
            QuestionData qd = new QuestionData(
                    q.getId(), q.getQuestionText(),
                    q.getChoiceA(), q.getChoiceB(), q.getChoiceC(), q.getChoiceD(),
                    q.getOrderIndex());
            qd.correctAnswer = q.getCorrectAnswer();
            safeQuestions.add(qd);
        }
        Collections.sort(safeQuestions, (a, b) -> Integer.compare(a.orderIndex, b.orderIndex));
        this.questions = Collections.unmodifiableList(safeQuestions);
    }

    // ------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------

    public void start() {
        this.status = STATUS_ACTIVE;
    }

    public void close() {
        this.status = STATUS_CLOSED;
    }

    public String getStatus() { return status; }
    public long getQuizId() { return quizId; }
    public String getQuizTitle() { return quizTitle; }
    public String getInstructions() { return instructions; }
    public Integer getTimeLimitMinutes() { return timeLimitMinutes; }
    public List<QuestionData> getQuestions() { return questions; }
    public long getServerStartTimeMillis() { return serverStartTimeMillis; }
    public long getCreatedAtMillis() { return createdAtMillis; }

    // ------------------------------------------------------------------
    // Student management
    // ------------------------------------------------------------------

    /** Register a new student. Returns their assigned ID. */
    public synchronized long joinStudent(String name) {
        long id = nextStudentId++;
        students.put(id, new StudentEntry(id, name));
        return id;
    }

    public int getConnectedCount() { return students.size(); }

    public StudentEntry getStudent(long studentId) { return students.get(studentId); }

    public List<StudentEntry> getAllStudents() {
        return new ArrayList<>(students.values());
    }

    // ------------------------------------------------------------------
    // Submission
    // ------------------------------------------------------------------

    /** Submit answers for a student. Returns null if student not found or session not active. */
    public synchronized ResultEntry submitAnswers(long studentId, Map<Long, String> answers) {
        if (!students.containsKey(studentId)) return null;

        // Store raw answers
        submissions.put(studentId, new HashMap<>(answers));

        // Score immediately
        int correctCount = 0;
        for (QuestionData q : questions) {
            String given = answers.get(q.id);
            if (given != null && given.equalsIgnoreCase(q.correctAnswer)) {
                correctCount++;
            }
        }
        int total = questions.size();
        int scorePercent = total == 0 ? 0 : Math.round((correctCount * 100f) / total);

        ResultEntry result = new ResultEntry(studentId, scorePercent, correctCount, total);
        results.put(studentId, result);
        return result;
    }

    public boolean hasStudentSubmitted(long studentId) {
        return results.containsKey(studentId);
    }

    public ResultEntry getResult(long studentId) {
        return results.get(studentId);
    }

    public int getSubmittedCount() { return results.size(); }

    public Map<Long, ResultEntry> getAllResults() {
        return new HashMap<>(results);
    }

    // ------------------------------------------------------------------
    // Inner classes
    // ------------------------------------------------------------------

    /** A question sent to students — NO correct answer included. */
    public static class QuestionData {
        public final long id;
        public final String text;
        public final String choiceA, choiceB, choiceC, choiceD;
        public final int orderIndex;

        /** Correct answer kept server-side only — never serialized to students. */
        String correctAnswer;

        public QuestionData(long id, String text, String choiceA, String choiceB,
                            String choiceC, String choiceD, int orderIndex) {
            this.id = id;
            this.text = text;
            this.choiceA = choiceA;
            this.choiceB = choiceB;
            this.choiceC = choiceC;
            this.choiceD = choiceD;
            this.orderIndex = orderIndex;
        }
    }

    public static class StudentEntry {
        public final long id;
        public final String name;
        public final long connectedAtMillis;

        public StudentEntry(long id, String name) {
            this.id = id;
            this.name = name;
            this.connectedAtMillis = System.currentTimeMillis();
        }
    }

    public static class ResultEntry {
        public final long studentId;
        public final int scorePercent;
        public final int correctCount;
        public final int totalQuestions;
        public final long submittedAtMillis;

        public ResultEntry(long studentId, int scorePercent, int correctCount, int totalQuestions) {
            this.studentId = studentId;
            this.scorePercent = scorePercent;
            this.correctCount = correctCount;
            this.totalQuestions = totalQuestions;
            this.submittedAtMillis = System.currentTimeMillis();
        }
    }
}
