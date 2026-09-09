package com.malapay.classroom.ui.dashboard.teacher.analytics.viewholder;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.ClassAnalyticsSummary;
import com.malapay.classroom.ui.dashboard.teacher.analytics.view.CircularProgressView;

/**
 * ViewHolder for the summary stat card section. Binds circular progress
 * views and stat numbers to the analytics data.
 */
public class SummaryViewHolder extends RecyclerView.ViewHolder {

    private final CircularProgressView cvAvgScore;
    private final CircularProgressView cvAttendance;
    private final TextView tvTotalQuizzes;
    private final TextView tvTotalStudents;
    private final TextView tvTotalLessons;
    private final TextView tvTotalSubmissions;

    public SummaryViewHolder(@NonNull View itemView) {
        super(itemView);
        cvAvgScore = itemView.findViewById(R.id.cvAvgScore);
        cvAttendance = itemView.findViewById(R.id.cvAttendance);
        tvTotalQuizzes = itemView.findViewById(R.id.tvTotalQuizzes);
        tvTotalStudents = itemView.findViewById(R.id.tvTotalStudents);
        tvTotalLessons = itemView.findViewById(R.id.tvTotalLessons);
        tvTotalSubmissions = itemView.findViewById(R.id.tvTotalSubmissions);
    }

    public void bind(ClassAnalyticsSummary summary) {
        // Average score circular progress
        int avgScore = summary.getAverageScoreInt();
        if (avgScore >= 0) {
            cvAvgScore.setProgress(avgScore / 100f);
            cvAvgScore.setProgressColor(getScoreColor(avgScore));
            cvAvgScore.setLabel("Avg Score");
        } else {
            cvAvgScore.setProgress(0f);
            cvAvgScore.setLabel("No data");
        }

        // Attendance circular progress
        cvAttendance.setProgress(summary.getAttendanceRate());
        cvAttendance.setProgressColor(0xFF087A4A); // primary green
        cvAttendance.setLabel("Attendance");

        // Stat numbers
        tvTotalQuizzes.setText(String.valueOf(summary.getTotalQuizzes()));
        tvTotalStudents.setText(String.valueOf(summary.getTotalStudents()));
        tvTotalLessons.setText(String.valueOf(summary.getTotalLessons()));
        tvTotalSubmissions.setText(String.valueOf(summary.getTotalSubmissions()));
    }

    /** Returns a color based on the score value. */
    private int getScoreColor(int score) {
        if (score >= 80) return 0xFF087A4A;       // Excellent - green
        else if (score >= 60) return 0xFF4FA8D8;   // Good - blue
        else if (score >= 40) return 0xFFE8C200;   // Fair - amber
        else return 0xFFD93636;                     // Needs improvement - red
    }
}
