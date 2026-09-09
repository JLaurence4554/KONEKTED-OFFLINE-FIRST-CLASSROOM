package com.malapay.classroom.data.local.model;

import androidx.room.ColumnInfo;

/**
 * Per-student performance projection from a Room query.
 * Fields match the column aliases in AnalyticsDao.getStudentPerformance().
 */
public class StudentPerformance {

    @ColumnInfo(name = "studentName")
    public String studentName;

    @ColumnInfo(name = "studentId")
    public long studentId;

    /** Average quiz score (0-100) across all quizzes this student took in the class. */
    @ColumnInfo(name = "avgScore")
    public double avgScore;

    /** Number of distinct quizzes this student has submitted for the class. */
    @ColumnInfo(name = "quizzesTaken")
    public int quizzesTaken;

    /** Attendance rate (0.0-1.0) for this student in the class. Set externally after loading. */
    private transient float attendanceRate;

    public String getStudentName() { return studentName; }
    public long getStudentId() { return studentId; }

    /** Returns avgScore rounded to an int (0-100). */
    public int getAvgScoreInt() { return (int) Math.round(avgScore); }

    /** Returns attendance as a percentage (0-100). */
    public int getAttendancePercent() { return Math.round(attendanceRate * 100); }

    public float getAttendanceRate() { return attendanceRate; }
    public void setAttendanceRate(float rate) { this.attendanceRate = rate; }
}
