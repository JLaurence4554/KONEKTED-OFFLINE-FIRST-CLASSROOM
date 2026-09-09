package com.malapay.classroom.data.local.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.malapay.classroom.data.local.model.StudentPerformance;
import com.malapay.classroom.data.local.model.StudentQuizHistory;

import java.util.List;

/**
 * Room DAO for analytics aggregation queries. All queries are read-only and
 * computed on the fly from existing quiz_results, attendance, lessons, quizzes,
 * class_members, and users tables — no new tables needed.
 */
@Dao
public interface AnalyticsDao {

    // ---------------------------------------------------------------
    // Class-level aggregates
    // ---------------------------------------------------------------

    /** Average quiz score (0-100) across all quizzes in a class. Null if no results exist. */
    @Query("SELECT AVG(qr.score) FROM quiz_results qr " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId")
    Double getClassAverageScore(long classId);

    /** Total number of quiz results submitted for a class. */
    @Query("SELECT COUNT(*) FROM quiz_results qr " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId")
    int getClassTotalSubmissions(long classId);

    /** Number of quizzes that belong to a class (across all its lessons). */
    @Query("SELECT COUNT(*) FROM quizzes q " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId")
    int getClassTotalQuizzes(long classId);

    /** Number of lessons in a class. */
    @Query("SELECT COUNT(*) FROM lessons WHERE class_id = :classId")
    int getClassTotalLessons(long classId);

    /** Number of students in a class. */
    @Query("SELECT COUNT(*) FROM class_members WHERE class_id = :classId")
    int getClassTotalStudents(long classId);

    // ---------------------------------------------------------------
    // Attendance
    // ---------------------------------------------------------------

    /**
     * Attendance breakdown for a class: returns rows of (status, count).
     * Use getAttendanceRate() for a simpler percentage.
     */
    @Query("SELECT status, COUNT(*) AS count FROM attendance " +
            "WHERE class_id = :classId GROUP BY status")
    List<AttendanceCount> getAttendanceBreakdown(long classId);

    /** Number of Present attendance records for a class. */
    @Query("SELECT COUNT(*) FROM attendance " +
            "WHERE class_id = :classId AND status = 'Present'")
    int getPresentCount(long classId);

    /** Total attendance records for a class. */
    @Query("SELECT COUNT(*) FROM attendance WHERE class_id = :classId")
    int getTotalAttendanceRecords(long classId);

    // ---------------------------------------------------------------
    // Score distribution (for bar chart)
    // ---------------------------------------------------------------

    /** Count of results in the "Excellent" range (80-100). */
    @Query("SELECT COUNT(*) FROM quiz_results qr " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId AND qr.score >= 80")
    int getExcellentCount(long classId);

    /** Count of results in the "Good" range (60-79). */
    @Query("SELECT COUNT(*) FROM quiz_results qr " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId AND qr.score >= 60 AND qr.score < 80")
    int getGoodCount(long classId);

    /** Count of results in the "Fair" range (40-59). */
    @Query("SELECT COUNT(*) FROM quiz_results qr " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId AND qr.score >= 40 AND qr.score < 60")
    int getFairCount(long classId);

    /** Count of results in the "Needs Improvement" range (<40). */
    @Query("SELECT COUNT(*) FROM quiz_results qr " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId AND qr.score < 40")
    int getNeedsImprovementCount(long classId);

    // ---------------------------------------------------------------
    // Per-student performance
    // ---------------------------------------------------------------

    /**
     * Returns a list of students with their average score and quiz count
     * for a given class, sorted by average score descending.
     */
    @Query("SELECT u.fullname AS studentName, u.id AS studentId, " +
            "AVG(qr.score) AS avgScore, " +
            "COUNT(DISTINCT qr.quiz_id) AS quizzesTaken " +
            "FROM quiz_results qr " +
            "INNER JOIN users u ON qr.student_id = u.id " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId " +
            "GROUP BY u.id " +
            "ORDER BY avgScore DESC")
    List<StudentPerformance> getStudentPerformance(long classId);

    /** Present count for a specific student in a class. */
    @Query("SELECT COUNT(*) FROM attendance WHERE class_id = :classId AND student_id = :studentId AND status = 'Present'")
    int getStudentPresentCount(long classId, long studentId);

    /** Total attendance records for a specific student in a class. */
    @Query("SELECT COUNT(*) FROM attendance WHERE class_id = :classId AND student_id = :studentId")
    int getStudentTotalAttendance(long classId, long studentId);

    // ---------------------------------------------------------------
    // Student self-analytics
    // ---------------------------------------------------------------

    /**
     * Returns the student's average score across all quizzes in a specific class.
     */
    @Query("SELECT AVG(qr.score) FROM quiz_results qr " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId AND qr.student_id = :studentId")
    Double getStudentClassAverageScore(long classId, long studentId);

    /** Total quizzes taken by a student in a class. */
    @Query("SELECT COUNT(DISTINCT qr.quiz_id) FROM quiz_results qr " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId AND qr.student_id = :studentId")
    int getStudentClassQuizzesTaken(long classId, long studentId);

    /** Total quizzes available in a class. */
    @Query("SELECT COUNT(*) FROM quizzes q " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId AND q.status = 'Published'")
    int getStudentClassQuizzesAvailable(long classId);

    /**
     * Returns the student's quiz history for a class, ordered by submission time.
     * Each row contains quiz title, score, correct/total counts, and submitted_at.
     */
    @Query("SELECT q.title AS quizTitle, qr.score, qr.correct_count AS correctCount, " +
            "qr.total_questions AS totalQuestions, qr.submitted_at AS submittedAt " +
            "FROM quiz_results qr " +
            "INNER JOIN quizzes q ON qr.quiz_id = q.id " +
            "INNER JOIN lessons l ON q.lesson_id = l.id " +
            "WHERE l.class_id = :classId AND qr.student_id = :studentId " +
            "ORDER BY qr.submitted_at ASC")
    List<StudentQuizHistory> getStudentQuizHistory(long classId, long studentId);

    /**
     * Student's overall average across ALL classes they've joined.
     */
    @Query("SELECT AVG(qr.score) FROM quiz_results qr " +
            "WHERE qr.student_id = :studentId")
    Double getStudentOverallAverage(long studentId);

    /** Total quizzes taken across all classes. */
    @Query("SELECT COUNT(DISTINCT qr.quiz_id) FROM quiz_results qr " +
            "WHERE qr.student_id = :studentId")
    int getStudentTotalQuizzesTaken(long studentId);

    /** Total classes a student has joined. */
    @Query("SELECT COUNT(*) FROM class_members WHERE student_id = :studentId")
    int getStudentTotalClasses(long studentId);

    /** Overall present count for a student across all classes. */
    @Query("SELECT COUNT(*) FROM attendance WHERE student_id = :studentId AND status = 'Present'")
    int getStudentOverallPresentCount(long studentId);

    /** Overall total attendance records for a student. */
    @Query("SELECT COUNT(*) FROM attendance WHERE student_id = :studentId")
    int getStudentOverallTotalAttendance(long studentId);

    // ---------------------------------------------------------------
    // Helper POJO for attendance breakdown
    // ---------------------------------------------------------------

    /** Simple projection for the attendance GROUP BY query. */
    class AttendanceCount {
        public String status;
        public int count;
    }
}
