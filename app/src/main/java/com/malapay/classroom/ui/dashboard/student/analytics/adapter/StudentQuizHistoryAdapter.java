package com.malapay.classroom.ui.dashboard.student.analytics.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.StudentQuizHistory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for individual quiz history rows in the student analytics view.
 * Shows quiz title, score badge, correct/total counts, and submission date.
 */
public class StudentQuizHistoryAdapter extends RecyclerView.Adapter<StudentQuizHistoryAdapter.ViewHolder> {

    private final List<StudentQuizHistory> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public void setData(List<StudentQuizHistory> newData) {
        items.clear();
        if (newData != null) {
            items.addAll(newData);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_quiz_history_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvQuizTitle;
        private final TextView tvQuizStats;
        private final TextView tvScore;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuizTitle = itemView.findViewById(R.id.tvQuizTitle);
            tvQuizStats = itemView.findViewById(R.id.tvQuizStats);
            tvScore = itemView.findViewById(R.id.tvScore);
        }

        void bind(StudentQuizHistory item) {
            tvQuizTitle.setText(item.getQuizTitle());

            String stats = item.getCorrectCount() + "/" + item.getTotalQuestions() + " correct";
            tvQuizStats.setText(stats);

            int score = item.getScore();
            tvScore.setText(score + "%");

            // Color the badge based on score
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
