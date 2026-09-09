package com.malapay.classroom.ui.dashboard.teacher.analytics.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.StudentPerformance;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for individual student performance rows within the student list section.
 * Shows each student's name, initial, stats, and score badge.
 */
public class StudentPerformanceAdapter extends RecyclerView.Adapter<StudentPerformanceAdapter.ViewHolder> {

    private final List<StudentPerformance> students = new ArrayList<>();

    public void setData(List<StudentPerformance> newData) {
        students.clear();
        if (newData != null) {
            students.addAll(newData);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_performance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(students.get(position));
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvInitial;
        private final TextView tvStudentName;
        private final TextView tvStudentStats;
        private final TextView tvScore;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitial = itemView.findViewById(R.id.tvInitial);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvStudentStats = itemView.findViewById(R.id.tvStudentStats);
            tvScore = itemView.findViewById(R.id.tvScore);
        }

        void bind(StudentPerformance student) {
            String name = student.getStudentName() != null ? student.getStudentName() : "?";
            tvInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
            tvStudentName.setText(name);

            String stats = student.quizzesTaken + " quiz(zes) taken";
            if (student.getAttendanceRate() > 0) {
                stats += " \u00B7 " + student.getAttendancePercent() + "% attendance";
            }
            tvStudentStats.setText(stats);

            int score = student.getAvgScoreInt();
            tvScore.setText(score + "%");

            int bgColor;
            if (score >= 80) bgColor = R.color.stat_green_bg;
            else if (score >= 60) bgColor = R.color.stat_blue_bg;
            else if (score >= 40) bgColor = R.color.warning_light;
            else bgColor = R.color.error_red_light;
            tvScore.setBackgroundResource(bgColor);

            int textColor;
            if (score >= 80) textColor = 0xFF087A4A;
            else if (score >= 60) textColor = 0xFF263F91;
            else if (score >= 40) textColor = 0xFF9A7B00;
            else textColor = 0xFFD93636;
            tvScore.setTextColor(textColor);
        }
    }
}
