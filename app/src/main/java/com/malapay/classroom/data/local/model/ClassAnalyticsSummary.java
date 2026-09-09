package com.malapay.classroom.data.local.model;

/**
 * Aggregated analytics summary for a single class.
 * Computed from quiz_results, attendance, lessons, quizzes, and class_members.
 */
public class ClassAnalyticsSummary {

    /** Class average quiz score (0-100), or null if no submissions exist. */
    private final Double averageScore;

    /** Attendance rate (0.0 - 1.0), computed as present days / total days. */
    private final float attendanceRate;

    /** Total number of quiz results submitted by students in this class. */
    private final int totalSubmissions;

    /** Total number of quizzes created across all lessons in this class. */
    private final int totalQuizzes;

    /** Total number of lessons in this class. */
    private final int totalLessons;

    /** Number of students enrolled in this class. */
    private final int totalStudents;

    public ClassAnalyticsSummary(Double averageScore, float attendanceRate,
                                  int totalSubmissions, int totalQuizzes,
                                  int totalLessons, int totalStudents) {
        this.averageScore = averageScore;
        this.attendanceRate = attendanceRate;
        this.totalSubmissions = totalSubmissions;
        this.totalQuizzes = totalQuizzes;
        this.totalLessons = totalLessons;
        this.totalStudents = totalStudents;
    }

    /** Returns the average score as an int (0-100), or -1 if no data. */
    public int getAverageScoreInt() {
        return averageScore != null ? (int) Math.round(averageScore) : -1;
    }

    /** Returns the attendance rate as a percentage (0-100). */
    public int getAttendancePercent() {
        return Math.round(attendanceRate * 100);
    }

    public Double getAverageScore() { return averageScore; }
    public float getAttendanceRate() { return attendanceRate; }
    public int getTotalSubmissions() { return totalSubmissions; }
    public int getTotalQuizzes() { return totalQuizzes; }
    public int getTotalLessons() { return totalLessons; }
    public int getTotalStudents() { return totalStudents; }
}
