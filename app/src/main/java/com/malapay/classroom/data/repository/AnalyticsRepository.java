package com.malapay.classroom.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.AnalyticsDao;
import com.malapay.classroom.data.local.model.ClassAnalyticsSummary;
import com.malapay.classroom.data.local.model.QuizScoreDistribution;
import com.malapay.classroom.data.local.model.StudentPerformance;
import com.malapay.classroom.data.local.model.StudentQuizHistory;

import java.util.ArrayList;
import java.util.List;

/**
 * Single entry point for all analytics data operations. Loads data off the
 * main thread and delivers results via {@link RepositoryCallback}.
 */
public class AnalyticsRepository {

    private final AppDatabase db;
    private final AnalyticsDao analyticsDao;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public AnalyticsRepository(Context context) {
        this.db = AppDatabase.getInstance(context);
        this.analyticsDao = db.analyticsDao();
    }

    /**
     * Loads the full class analytics summary: avg score, attendance rate,
     * total submissions, total quizzes, total lessons, total students.
     */
    public void getClassSummary(long classId, RepositoryCallback<ClassAnalyticsSummary> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                Double avgScore = analyticsDao.getClassAverageScore(classId);
                int totalSubmissions = analyticsDao.getClassTotalSubmissions(classId);
                int totalQuizzes = analyticsDao.getClassTotalQuizzes(classId);
                int totalLessons = analyticsDao.getClassTotalLessons(classId);
                int totalStudents = analyticsDao.getClassTotalStudents(classId);

                int presentCount = analyticsDao.getPresentCount(classId);
                int totalAttendance = analyticsDao.getTotalAttendanceRecords(classId);
                float attendanceRate = totalAttendance > 0
                        ? (float) presentCount / totalAttendance
                        : 0f;

                ClassAnalyticsSummary summary = new ClassAnalyticsSummary(
                        avgScore, attendanceRate, totalSubmissions,
                        totalQuizzes, totalLessons, totalStudents);

                postSuccess(callback, summary);
            } catch (Exception e) {
                postError(callback, "Could not load analytics data.");
            }
        });
    }

    /**
     * Loads the score distribution as a list of 4 buckets:
     * Excellent (80-100), Good (60-79), Fair (40-59), Needs Improvement (<40).
     */
    public void getScoreDistribution(long classId, RepositoryCallback<List<QuizScoreDistribution>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                int excellent = analyticsDao.getExcellentCount(classId);
                int good = analyticsDao.getGoodCount(classId);
                int fair = analyticsDao.getFairCount(classId);
                int needsImprovement = analyticsDao.getNeedsImprovementCount(classId);

                List<QuizScoreDistribution> distribution = new ArrayList<>();
                distribution.add(new QuizScoreDistribution(
                        "Excellent (80-100%)", excellent, R.color.quiz_correct));
                distribution.add(new QuizScoreDistribution(
                        "Good (60-79%)", good, R.color.tertiary_light));
                distribution.add(new QuizScoreDistribution(
                        "Fair (40-59%)", fair, R.color.warning));
                distribution.add(new QuizScoreDistribution(
                        "Needs Improvement (<40%)", needsImprovement, R.color.error));

                postSuccess(callback, distribution);
            } catch (Exception e) {
                postError(callback, "Could not load score distribution.");
            }
        });
    }

    /**
     * Loads per-student performance for the class, including attendance rates.
     * Results are sorted by average score descending.
     */
    public void getStudentPerformance(long classId, RepositoryCallback<List<StudentPerformance>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                List<StudentPerformance> students = analyticsDao.getStudentPerformance(classId);
                for (StudentPerformance sp : students) {
                    int present = analyticsDao.getStudentPresentCount(classId, sp.getStudentId());
                    int total = analyticsDao.getStudentTotalAttendance(classId, sp.getStudentId());
                    float attRate = total > 0 ? (float) present / total : 0f;
                    sp.setAttendanceRate(attRate);
                }
                postSuccess(callback, students);
            } catch (Exception e) {
                postError(callback, "Could not load student performance.");
            }
        });
    }

    // ---------------------------------------------------------------
    // Student self-analytics
    // ---------------------------------------------------------------

    /** Loads a student's quiz history for a specific class (score over time). */
    public void getStudentQuizHistory(long classId, long studentId,
                                       RepositoryCallback<List<StudentQuizHistory>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                List<StudentQuizHistory> history = analyticsDao.getStudentQuizHistory(classId, studentId);
                postSuccess(callback, history);
            } catch (Exception e) {
                postError(callback, "Could not load quiz history.");
            }
        });
    }

    /** Loads a student's summary stats for a specific class. */
    public void getStudentClassSummary(long classId, long studentId,
                                        RepositoryCallback<StudentClassSummary> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                Double avgScore = analyticsDao.getStudentClassAverageScore(classId, studentId);
                int quizzesTaken = analyticsDao.getStudentClassQuizzesTaken(classId, studentId);
                int quizzesAvailable = analyticsDao.getStudentClassQuizzesAvailable(classId);

                int present = analyticsDao.getStudentPresentCount(classId, studentId);
                int totalAtt = analyticsDao.getStudentTotalAttendance(classId, studentId);
                float attRate = totalAtt > 0 ? (float) present / totalAtt : 0f;

                StudentClassSummary summary = new StudentClassSummary(
                        avgScore, attRate, quizzesTaken, quizzesAvailable);
                postSuccess(callback, summary);
            } catch (Exception e) {
                postError(callback, "Could not load your analytics.");
            }
        });
    }

    /** Loads a student's overall stats across all classes. */
    public void getStudentOverallSummary(long studentId,
                                          RepositoryCallback<StudentClassSummary> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                Double avgScore = analyticsDao.getStudentOverallAverage(studentId);
                int quizzesTaken = analyticsDao.getStudentTotalQuizzesTaken(studentId);
                int totalClasses = analyticsDao.getStudentTotalClasses(studentId);

                int present = analyticsDao.getStudentOverallPresentCount(studentId);
                int totalAtt = analyticsDao.getStudentOverallTotalAttendance(studentId);
                float attRate = totalAtt > 0 ? (float) present / totalAtt : 0f;

                // For overall, quizzesAvailable is not per-class; use quizzesTaken as display
                StudentClassSummary summary = new StudentClassSummary(
                        avgScore, attRate, quizzesTaken, 0);
                summary.setTotalClasses(totalClasses);
                postSuccess(callback, summary);
            } catch (Exception e) {
                postError(callback, "Could not load your analytics.");
            }
        });
    }

    /** Loads a student's quiz history across ALL their joined classes. */
    public void getStudentAllQuizHistory(long studentId,
                                          RepositoryCallback<List<StudentQuizHistory>> callback) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                // Get all classes the student has joined
                List<com.malapay.classroom.data.local.entity.SchoolClass> classes =
                        db.classMemberDao().getJoinedClasses(studentId);
                List<StudentQuizHistory> allHistory = new ArrayList<>();
                for (com.malapay.classroom.data.local.entity.SchoolClass sc : classes) {
                    allHistory.addAll(analyticsDao.getStudentQuizHistory(sc.getId(), studentId));
                }
                // Sort by submittedAt ascending for the timeline chart
                allHistory.sort((a, b) -> Long.compare(a.submittedAt, b.submittedAt));
                postSuccess(callback, allHistory);
            } catch (Exception e) {
                postError(callback, "Could not load quiz history.");
            }
        });
    }

    /**
     * Summary stats for a student's performance in a single class
     * (or overall across all classes when viewed from the dashboard).
     */
    public static class StudentClassSummary {
        private final Double averageScore;
        private final float attendanceRate;
        private final int quizzesTaken;
        private final int quizzesAvailable;
        private int totalClasses;

        public StudentClassSummary(Double averageScore, float attendanceRate,
                                   int quizzesTaken, int quizzesAvailable) {
            this.averageScore = averageScore;
            this.attendanceRate = attendanceRate;
            this.quizzesTaken = quizzesTaken;
            this.quizzesAvailable = quizzesAvailable;
        }

        public int getAverageScoreInt() {
            return averageScore != null ? (int) Math.round(averageScore) : -1;
        }
        public int getAttendancePercent() { return Math.round(attendanceRate * 100); }
        public Double getAverageScore() { return averageScore; }
        public float getAttendanceRate() { return attendanceRate; }
        public int getQuizzesTaken() { return quizzesTaken; }
        public int getQuizzesAvailable() { return quizzesAvailable; }
        public int getTotalClasses() { return totalClasses; }
        public void setTotalClasses(int totalClasses) { this.totalClasses = totalClasses; }
    }

    private <T> void postSuccess(RepositoryCallback<T> callback, T result) {
        mainThreadHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(RepositoryCallback<T> callback, String message) {
        mainThreadHandler.post(() -> callback.onError(message));
    }
}
