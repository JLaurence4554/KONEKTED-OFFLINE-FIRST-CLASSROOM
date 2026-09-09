package com.malapay.classroom.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.ClassDao;
import com.malapay.classroom.data.local.dao.LessonDao;
import com.malapay.classroom.data.local.dao.QuestionDao;
import com.malapay.classroom.data.local.dao.QuizDao;
import com.malapay.classroom.data.local.dao.QuizResultDao;
import com.malapay.classroom.data.local.dao.UserDao;
import com.malapay.classroom.data.local.entity.Lesson;
import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.data.local.entity.QuizResult;
import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.data.local.entity.User;
import com.malapay.classroom.data.local.model.QuizResultWithStudentName;
import com.malapay.classroom.data.local.model.QuizWithQuestionCount;
import com.malapay.classroom.data.local.model.QuizWithSubmissionStatus;
import com.malapay.classroom.sync.api.ApiClient;
import com.malapay.classroom.sync.api.ApiEnvelope;
import com.malapay.classroom.sync.api.GetQuizResultsData;
import com.malapay.classroom.sync.api.GetQuizzesData;
import com.malapay.classroom.sync.api.SyncQuizData;
import com.malapay.classroom.sync.api.SyncQuizRequest;
import com.malapay.classroom.sync.api.SyncQuizResultData;
import com.malapay.classroom.sync.api.SyncQuizResultRequest;
import com.malapay.classroom.sync.api.SyncQuestionData;
import com.malapay.classroom.sync.api.SyncQuestionRequest;
import com.malapay.classroom.util.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single entry point for all quiz-related data operations: quiz CRUD and
 * publish status, question management (add/edit/delete/reorder), result
 * submission/scoring, and online sync. Supports Multiple Choice,
 * Identification, and Essay question types.
 */
public class QuizRepository {

    private final QuizDao quizDao;
    private final QuestionDao questionDao;
    private final QuizResultDao quizResultDao;
    private final LessonDao lessonDao;
    private final ClassDao classDao;
    private final UserDao userDao;
    private final Context appContext;
    private final SessionManager sessionManager;

    /** Exposes the QuizResultDao for direct DAO operations (e.g., grading). */
    public com.malapay.classroom.data.local.dao.QuizResultDao getQuizResultDao() { return quizResultDao; }
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public QuizRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.quizDao = db.quizDao();
        this.questionDao = db.questionDao();
        this.quizResultDao = db.quizResultDao();
        this.lessonDao = db.lessonDao();
        this.classDao = db.classDao();
        this.userDao = db.userDao();
        this.appContext = context.getApplicationContext();
        this.sessionManager = new SessionManager(context);
    }

    // ---------------------------------------------------------------
    // Quiz CRUD
    // ---------------------------------------------------------------

    public void createQuiz(long lessonId, String title, String instructions,
                            @Nullable Integer timeLimitMinutes, int passingScore,
                            RepositoryCallback<Quiz> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                Quiz quiz = new Quiz(lessonId, title.trim(), instructions.trim(), timeLimitMinutes,
                        passingScore, Quiz.STATUS_DRAFT, System.currentTimeMillis());
                long id = quizDao.insert(quiz);
                quiz.setId(id);
                postSuccess(callback, quiz);
            } catch (Exception e) {
                postError(callback, "Could not create the quiz. Please try again.");
            }
        });
    }

    public void updateQuiz(Quiz quiz, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                quizDao.update(quiz);
                postSuccess(callback, null);
            } catch (Exception e) {
                postError(callback, "Could not save changes. Please try again.");
            }
        });
    }

    public void deleteQuiz(Quiz quiz, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                quizDao.delete(quiz);
                postSuccess(callback, null);
            } catch (Exception e) {
                postError(callback, "Could not delete the quiz. Please try again.");
            }
        });
    }

    public void getQuizById(long quizId, RepositoryCallback<Quiz> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                Quiz result = quizDao.findById(quizId);
                if (result == null) {
                    postError(callback, "Quiz not found.");
                } else {
                    postSuccess(callback, result);
                }
            } catch (Exception e) {
                postError(callback, "Could not load the quiz. Please try again.");
            }
        });
    }

    public void getQuizzesForLesson(long lessonId, RepositoryCallback<List<QuizWithQuestionCount>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                postSuccess(callback, quizDao.getQuizzesForLesson(lessonId));
            } catch (Exception e) {
                postError(callback, "Could not load quizzes. Please try again.");
            }
        });
    }

    public void getPublishedQuizzesForLesson(long lessonId, RepositoryCallback<List<Quiz>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                postSuccess(callback, quizDao.getPublishedQuizzesForLesson(lessonId));
            } catch (Exception e) {
                postError(callback, "Could not load quizzes. Please try again.");
            }
        });
    }

    public void getPublishedQuizzesWithStatus(long lessonId, long studentId,
                                               RepositoryCallback<List<QuizWithSubmissionStatus>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                List<Quiz> quizzes = quizDao.getPublishedQuizzesForLesson(lessonId);
                List<QuizWithSubmissionStatus> result = new ArrayList<>();
                for (Quiz q : quizzes) {
                    boolean submitted = quizResultDao.hasStudentSubmitted(q.getId(), studentId);
                    result.add(new QuizWithSubmissionStatus(q, submitted));
                }
                postSuccess(callback, result);
            } catch (Exception e) {
                postError(callback, "Could not load quizzes. Please try again.");
            }
        });
    }

    // ---------------------------------------------------------------
    // Question management
    // ---------------------------------------------------------------

    public void addQuestion(long quizId, String questionText, String choiceA, String choiceB,
                             String choiceC, String choiceD, String correctAnswer,
                             RepositoryCallback<Question> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                int nextOrder = questionDao.getMaxOrderIndex(quizId) + 1;
                Question question = new Question(quizId, questionText.trim(), choiceA.trim(),
                        choiceB.trim(), choiceC.trim(), choiceD.trim(), correctAnswer, nextOrder);
                long id = questionDao.insert(question);
                question.setId(id);
                postSuccess(callback, question);
            } catch (Exception e) {
                postError(callback, "Could not add the question. Please try again.");
            }
        });
    }

    public void addIdentificationQuestion(long quizId, String questionText, String correctAnswerText,
                                           RepositoryCallback<Question> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                int nextOrder = questionDao.getMaxOrderIndex(quizId) + 1;
                Question question = new Question(quizId, questionText.trim(), correctAnswerText.trim(), nextOrder);
                long id = questionDao.insert(question);
                question.setId(id);
                postSuccess(callback, question);
            } catch (Exception e) {
                postError(callback, "Could not add the question. Please try again.");
            }
        });
    }

    public void addEssayQuestion(long quizId, String questionText, String modelAnswer,
                                  RepositoryCallback<Question> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                int nextOrder = questionDao.getMaxOrderIndex(quizId) + 1;
                Question question = Question.createEssay(quizId, questionText.trim(), modelAnswer != null ? modelAnswer.trim() : null, nextOrder);
                long id = questionDao.insert(question);
                question.setId(id);
                postSuccess(callback, question);
            } catch (Exception e) {
                postError(callback, "Could not add the question. Please try again.");
            }
        });
    }

    public void updateQuestion(Question question, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> { try { questionDao.update(question); postSuccess(callback, null); } catch (Exception e) { postError(callback, "Could not save changes."); } });
    }

    public void deleteQuestion(Question question, RepositoryCallback<Void> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> { try { questionDao.delete(question); postSuccess(callback, null); } catch (Exception e) { postError(callback, "Could not delete."); } });
    }

    public void getQuestionsForQuiz(long quizId, RepositoryCallback<List<Question>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> { try { postSuccess(callback, questionDao.getQuestionsForQuiz(quizId)); } catch (Exception e) { postError(callback, "Could not load questions."); } });
    }

    public void getQuestionById(long questionId, RepositoryCallback<Question> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> { try { Question r = questionDao.findById(questionId); if (r == null) postError(callback, "Question not found."); else postSuccess(callback, r); } catch (Exception e) { postError(callback, "Could not load the question."); } });
    }

    public void reorderQuestions(List<Question> q, RepositoryCallback<Void> c) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> { try { questionDao.reorder(q); postSuccess(c, null); } catch (Exception e) { postError(c, "Could not reorder."); } });
    }

    public void hasStudentSubmitted(long quizId, long studentId, RepositoryCallback<Boolean> cb) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> { try { postSuccess(cb, quizResultDao.hasStudentSubmitted(quizId, studentId)); } catch (Exception e) { postError(cb, "Error."); } });
    }

    public void getMyResult(long quizId, long studentId, RepositoryCallback<QuizResult> cb) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> { try { QuizResult r = quizResultDao.findByQuizAndStudent(quizId, studentId); if (r == null) postError(cb, "No result."); else postSuccess(cb, r); } catch (Exception e) { postError(cb, "Error."); } });
    }

    public void submitQuizResult(long quizId, long studentId, Map<Long, String> answers, RepositoryCallback<QuizResult> cb) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                if (quizResultDao.hasStudentSubmitted(quizId, studentId)) { postError(cb, "Already submitted."); return; }
                List<Question> qs = questionDao.getQuestionsForQuiz(quizId);
                int cc = 0, ac = 0;
                for (Question q : qs) {
                    String g = answers.get(q.getId());
                    if (q.isMultipleChoice()) { ac++; if (g != null && g.equalsIgnoreCase(q.getCorrectAnswer())) cc++; }
                    else if (q.isIdentification()) { ac++; if (g != null && q.getAnswerText() != null && g.trim().equalsIgnoreCase(q.getAnswerText().trim())) cc++; }
                }
                int sp = ac == 0 ? 0 : Math.round((cc * 100f) / ac);
                QuizResult r = new QuizResult(quizId, studentId, sp, cc, ac, System.currentTimeMillis());
                // Store essay/identification answers as JSON for teacher grading
                org.json.JSONObject essayJson = new org.json.JSONObject();
                for (Map.Entry<Long, String> entry : answers.entrySet()) {
                    Question q = null;
                    for (Question qq : qs) { if (qq.getId() == entry.getKey()) { q = qq; break; } }
                    if (q != null && !q.isMultipleChoice() && entry.getValue() != null) {
                        try { essayJson.put(String.valueOf(entry.getKey()), entry.getValue()); }
                        catch (org.json.JSONException ignored) { }
                    }
                }
                if (essayJson.length() > 0) r.setEssayAnswers(essayJson.toString());
                r.setId(quizResultDao.insert(r));
                postSuccess(cb, r);
            } catch (Exception e) { postError(cb, "Error."); }
        });
    }

    public void getResultsForQuiz(long qid, RepositoryCallback<List<QuizResultWithStudentName>> cb) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> { try { postSuccess(cb, quizResultDao.getResultsForQuiz(qid)); } catch (Exception e) { postError(cb, "Error."); } });
    }

    public static Map<Long, String> newAnswerMap() { return new LinkedHashMap<>(); }
    // ---------------------------------------------------------------
    // Online sync - push/pull quizzes, questions, and results
    // ---------------------------------------------------------------

    public void pushQuizToServer(Quiz quiz) {
        if (sessionManager == null || sessionManager.getEmail() == null) return;
        Lesson lesson = lessonDao.findById(quiz.getLessonId());
        if (lesson == null || lesson.getServerId() == 0) return;
        SyncQuizRequest request = new SyncQuizRequest(
                lesson.getServerId(), quiz.getTitle(), quiz.getInstructions(),
                quiz.getTimeLimitMinutes(), quiz.getPassingScore(),
                quiz.getStatus(), sessionManager.getEmail());
        ApiClient.getApiService().syncQuiz(request).enqueue(new Callback<ApiEnvelope<SyncQuizData>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SyncQuizData>> call, Response<ApiEnvelope<SyncQuizData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success && response.body().data != null) {
                    long serverId = response.body().data.serverId;
                    AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                        quiz.setServerId(serverId);
                        quiz.setSyncStatus("SYNCED");
                        quiz.setLastSyncedAt(System.currentTimeMillis());
                        quizDao.update(quiz);
                        pushQuestionsForQuiz(quiz);
                    });
                }
            }
            @Override
            public void onFailure(Call<ApiEnvelope<SyncQuizData>> call, Throwable t) { }
        });
    }
    private void pushQuestionsForQuiz(Quiz quiz) {
        if (sessionManager == null || sessionManager.getEmail() == null) return;
        List<Question> questions = questionDao.getUnsyncedQuestionsForQuiz(quiz.getId());
        for (Question q : questions) {
            SyncQuestionRequest request = new SyncQuestionRequest(
                    quiz.getServerId(), q.getQuestionText(), q.getQuestionType(),
                    q.getChoiceA(), q.getChoiceB(), q.getChoiceC(), q.getChoiceD(),
                    q.getCorrectAnswer(), q.getAnswerText(),
                    q.getOrderIndex(), sessionManager.getEmail());
            final Question questionRef = q;
            ApiClient.getApiService().syncQuestion(request).enqueue(new Callback<ApiEnvelope<SyncQuestionData>>() {
                @Override
                public void onResponse(Call<ApiEnvelope<SyncQuestionData>> call, Response<ApiEnvelope<SyncQuestionData>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().success && response.body().data != null) {
                        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                            questionRef.setServerId(response.body().data.serverId);
                            questionRef.setSyncStatus("SYNCED");
                            questionRef.setLastSyncedAt(System.currentTimeMillis());
                            questionDao.update(questionRef);
                        });
                    }
                }
                @Override
                public void onFailure(Call<ApiEnvelope<SyncQuestionData>> call, Throwable t) { }
            });
        }
    }

    public void pushUnsyncedQuizzes() {
        List<Quiz> unsynced = quizDao.getUnsyncedQuizzes();
        for (Quiz quiz : unsynced) {
            pushQuizToServer(quiz);
        }
    }
    public void pullQuizzesFromServer(long lessonId, RepositoryCallback<List<Quiz>> callback) {
        if (sessionManager == null || sessionManager.getEmail() == null) {
            postError(callback, "Not logged in.");
            return;
        }
        ApiClient.getApiService().getQuizzes(lessonId).enqueue(new Callback<ApiEnvelope<GetQuizzesData>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<GetQuizzesData>> call, Response<ApiEnvelope<GetQuizzesData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success && response.body().data != null) {
                    AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                        List<Quiz> localQuizzes = new ArrayList<>();
                        for (GetQuizzesData.ServerQuiz sq : response.body().data.quizzes) {
                            // Find local quiz by server_id or by title (dedup)
                            Quiz local = quizDao.findByServerId(sq.id);
                            if (local == null) {
                                List<QuizWithQuestionCount> byLesson = quizDao.getQuizzesForLesson(lessonId);
                                for (QuizWithQuestionCount ql : byLesson) {
                                    if (ql.quiz.getTitle().equals(sq.title)) { local = ql.quiz; break; }
                                }
                            }
                            if (local == null) {
                                Quiz newQuiz = new Quiz(lessonId, sq.title, sq.instructions,
                                        sq.time_limit_minutes > 0 ? sq.time_limit_minutes : null,
                                        sq.passing_score, sq.status, System.currentTimeMillis());
                                newQuiz.setServerId(sq.id);
                                newQuiz.setSyncStatus("SYNCED");
                                long localId = quizDao.insert(newQuiz);
                                newQuiz.setId(localId);
                                local = newQuiz;
                            } else {
                                local.setSyncStatus("SYNCED");
                                local.setLastSyncedAt(System.currentTimeMillis());
                                quizDao.update(local);
                            }
                            // Pull questions for this quiz
                            if (sq.questions != null) {
                                for (GetQuizzesData.ServerQuestion sqq : sq.questions) {
                                    Question existingQ = questionDao.findByServerId(sqq.id);
                                    if (existingQ == null) {
                                        Question q = new Question(local.getId(), sqq.question,
                                                sqq.choice_a != null ? sqq.choice_a : "",
                                                sqq.choice_b != null ? sqq.choice_b : "",
                                                sqq.choice_c != null ? sqq.choice_c : "",
                                                sqq.choice_d != null ? sqq.choice_d : "",
                                                sqq.correct_answer != null ? sqq.correct_answer : "",
                                                sqq.order_index);
                                        if (sqq.question_type != null) q.setQuestionType(sqq.question_type);
                                        if (sqq.answer_text != null) q.setAnswerText(sqq.answer_text);
                                        q.setServerId(sqq.id);
                                        q.setSyncStatus("SYNCED");
                                        q.setLastSyncedAt(System.currentTimeMillis());
                                        questionDao.insert(q);
                                    }
                                }
                            }
                            localQuizzes.add(local);
                        }
                        postSuccess(callback, localQuizzes);
                    });
                } else {
                    postError(callback, "Could not fetch quizzes from server.");
                }
            }
            @Override
            public void onFailure(Call<ApiEnvelope<GetQuizzesData>> call, Throwable t) {
                postError(callback, "Server unavailable.");
            }
        });
    }
    public void pushQuizResultToServer(QuizResult result) {
        if (sessionManager == null || sessionManager.getEmail() == null) return;
        SyncQuizResultRequest request = new SyncQuizResultRequest(
                result.getQuizId(), sessionManager.getEmail(),
                result.getScore(), result.getCorrectCount(), result.getTotalQuestions());
        ApiClient.getApiService().syncQuizResult(request).enqueue(new Callback<ApiEnvelope<SyncQuizResultData>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SyncQuizResultData>> call, Response<ApiEnvelope<SyncQuizResultData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success && response.body().data != null) {
                    AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                        quizResultDao.markResultSynced(result.getId(), response.body().data.serverId, System.currentTimeMillis());
                    });
                }
            }
            @Override
            public void onFailure(Call<ApiEnvelope<SyncQuizResultData>> call, Throwable t) { }
        });
    }

    public void pushUnsyncedQuizResults() {
        List<QuizResult> unsynced = quizResultDao.getUnsyncedResults();
        for (QuizResult result : unsynced) {
            pushQuizResultToServer(result);
        }
    }

    /**
     * Pull quiz results from the server for a given quiz and merge into local DB.
     * Deduplicates by quiz_id + student_id. Called by the teacher results screen.
     */
    public void pullQuizResultsFromServer(long quizId, RepositoryCallback<List<QuizResultWithStudentName>> callback) {
        ApiClient.getApiService().getQuizResults(quizId).enqueue(new retrofit2.Callback<ApiEnvelope<GetQuizResultsData>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiEnvelope<GetQuizResultsData>> call, retrofit2.Response<ApiEnvelope<GetQuizResultsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success && response.body().data != null) {
                    AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
                        for (GetQuizResultsData.ServerQuizResult sr : response.body().data.results) {
                            // Find local student by email or name (server sends student_id + student_name)
                            // Check if result already exists locally
                            List<QuizResultWithStudentName> existing = quizResultDao.getResultsForQuiz(quizId);
                            boolean found = false;
                            for (QuizResultWithStudentName existingResult : existing) {
                                if (existingResult.result.getScore() == sr.score
                                        && existingResult.result.getCorrectCount() == sr.correct_count
                                        && existingResult.result.getTotalQuestions() == sr.total_questions) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                // Create local placeholder user if needed, then insert result
                                // The student_name from server is used for display
                                try {
                                    QuizResult localResult = new QuizResult(quizId, sr.student_id, sr.score,
                                            sr.correct_count, sr.total_questions, System.currentTimeMillis());
                                    localResult.setSyncStatus("SYNCED");
                                    quizResultDao.insert(localResult);
                                } catch (Exception ignored) {
                                    // Duplicate constraint or other issue
                                }
                            }
                        }
                        // Return fresh results
                        postSuccess(callback, quizResultDao.getResultsForQuiz(quizId));
                    });
                } else {
                    // Fallback to local results
                    AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() ->
                            postSuccess(callback, quizResultDao.getResultsForQuiz(quizId)));
                }
            }
            @Override
            public void onFailure(retrofit2.Call<ApiEnvelope<GetQuizResultsData>> call, Throwable t) {
                // Fallback to local results
                AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() ->
                        postSuccess(callback, quizResultDao.getResultsForQuiz(quizId)));
            }
        });
    }

    private <T> void postSuccess(RepositoryCallback<T> callback, T result) {
        mainThreadHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(RepositoryCallback<T> callback, String message) {
        mainThreadHandler.post(() -> callback.onError(message));
    }
}