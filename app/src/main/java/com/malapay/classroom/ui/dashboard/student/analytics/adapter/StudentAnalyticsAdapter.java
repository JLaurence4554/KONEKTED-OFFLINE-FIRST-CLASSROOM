package com.malapay.classroom.ui.dashboard.student.analytics.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.StudentQuizHistory;
import com.malapay.classroom.data.repository.AnalyticsRepository;
import com.malapay.classroom.ui.dashboard.teacher.analytics.view.CircularProgressView;
import com.malapay.classroom.ui.dashboard.teacher.analytics.view.ScoreLineChartView;

import java.util.ArrayList;
import java.util.List;

/**
 * Main adapter for the student analytics RecyclerView. Supports three view types:
 * - Summary stat cards (circular progress + numbers)
 * - Score trend line chart
 * - Quiz history list
 */
public class StudentAnalyticsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SUMMARY = 0;
    private static final int TYPE_SCORE_CHART = 1;
    private static final int TYPE_QUIZ_HISTORY = 2;

    private AnalyticsRepository.StudentClassSummary summary;
    private List<StudentQuizHistory> quizHistory;
    private boolean showClassesJoined = false;

    public void setSummary(AnalyticsRepository.StudentClassSummary summary) {
        this.summary = summary;
        notifyItemChanged(0, TYPE_SUMMARY);
    }

    public void setQuizHistory(List<StudentQuizHistory> history) {
        this.quizHistory = history;
        notifyDataSetChanged();
    }

    public void setShowClassesJoined(boolean show) {
        this.showClassesJoined = show;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return TYPE_SUMMARY;
        if (position == 1) return TYPE_SCORE_CHART;
        return TYPE_QUIZ_HISTORY;
    }

    @Override
    public int getItemCount() {
        return summary != null ? 3 : 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_SUMMARY:
                return new SummaryVH(inflater.inflate(R.layout.item_student_analytics_summary, parent, false));
            case TYPE_SCORE_CHART:
                return new ChartVH(inflater.inflate(R.layout.item_student_score_chart, parent, false));
            case TYPE_QUIZ_HISTORY:
                return new HistoryVH(inflater.inflate(R.layout.item_student_quiz_history, parent, false));
            default:
                throw new IllegalArgumentException("Unknown view type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case TYPE_SUMMARY:
                ((SummaryVH) holder).bind(summary, showClassesJoined);
                break;
            case TYPE_SCORE_CHART:
                ((ChartVH) holder).bind(quizHistory);
                break;
            case TYPE_QUIZ_HISTORY:
                ((HistoryVH) holder).bind(quizHistory);
                break;
        }
    }

    static class SummaryVH extends RecyclerView.ViewHolder {
        private final CircularProgressView cvAvgScore;
        private final CircularProgressView cvAttendance;
        private final TextView tvQuizzesTaken;
        private final LinearLayout layoutClassesJoined;
        private final TextView tvClassesJoined;

        SummaryVH(@NonNull View itemView) {
            super(itemView);
            cvAvgScore = itemView.findViewById(R.id.cvAvgScore);
            cvAttendance = itemView.findViewById(R.id.cvAttendance);
            tvQuizzesTaken = itemView.findViewById(R.id.tvQuizzesTaken);
            layoutClassesJoined = itemView.findViewById(R.id.layoutClassesJoined);
            tvClassesJoined = itemView.findViewById(R.id.tvClassesJoined);
        }

        void bind(AnalyticsRepository.StudentClassSummary s, boolean showClasses) {
            if (s == null) return;

            int avgScore = s.getAverageScoreInt();
            if (avgScore >= 0) {
                cvAvgScore.setProgress(avgScore / 100f);
                cvAvgScore.setProgressColor(getScoreColor(avgScore));
                cvAvgScore.setLabel("Avg Score");
            } else {
                cvAvgScore.setProgress(0f);
                cvAvgScore.setLabel("No data");
            }

            cvAttendance.setProgress(s.getAttendanceRate());
            cvAttendance.setProgressColor(0xFF087A4A);
            cvAttendance.setLabel("Attendance");

            tvQuizzesTaken.setText(String.valueOf(s.getQuizzesTaken()));

            if (showClasses && s.getTotalClasses() > 0) {
                layoutClassesJoined.setVisibility(View.VISIBLE);
                tvClassesJoined.setText(String.valueOf(s.getTotalClasses()));
            }
        }

        private int getScoreColor(int score) {
            if (score >= 80) return 0xFF087A4A;
            else if (score >= 60) return 0xFF4FA8D8;
            else if (score >= 40) return 0xFFE8C200;
            else return 0xFFD93636;
        }
    }

    static class ChartVH extends RecyclerView.ViewHolder {
        private final ScoreLineChartView scoreLineChart;

        ChartVH(@NonNull View itemView) {
            super(itemView);
            scoreLineChart = itemView.findViewById(R.id.scoreLineChart);
        }

        void bind(List<StudentQuizHistory> history) {
            scoreLineChart.setData(history);
        }
    }

    static class HistoryVH extends RecyclerView.ViewHolder {
        private final RecyclerView recyclerQuizHistory;
        private final View emptyQuizzes;

        HistoryVH(@NonNull View itemView) {
            super(itemView);
            recyclerQuizHistory = itemView.findViewById(R.id.recyclerQuizHistory);
            emptyQuizzes = itemView.findViewById(R.id.emptyQuizzes);
        }

        void bind(List<StudentQuizHistory> history) {
            if (history == null || history.isEmpty()) {
                recyclerQuizHistory.setVisibility(View.GONE);
                emptyQuizzes.setVisibility(View.VISIBLE);
            } else {
                recyclerQuizHistory.setVisibility(View.VISIBLE);
                emptyQuizzes.setVisibility(View.GONE);

                StudentQuizHistoryAdapter adapter = new StudentQuizHistoryAdapter();
                recyclerQuizHistory.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
                recyclerQuizHistory.setAdapter(adapter);
                adapter.setData(history);
            }
        }
    }
}
