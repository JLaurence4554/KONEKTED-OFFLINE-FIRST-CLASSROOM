package com.malapay.classroom.ui.dashboard.teacher.analytics.viewholder;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.QuizScoreDistribution;
import com.malapay.classroom.ui.dashboard.teacher.analytics.view.HorizontalBarChartView;

import java.util.List;

/**
 * ViewHolder for the score distribution bar chart section.
 */
public class BarChartViewHolder extends RecyclerView.ViewHolder {

    private final HorizontalBarChartView barChart;

    public BarChartViewHolder(@NonNull View itemView) {
        super(itemView);
        barChart = itemView.findViewById(R.id.barChart);
    }

    public void bind(List<QuizScoreDistribution> distribution) {
        barChart.setData(distribution);
    }
}
