package com.malapay.classroom.ui.dashboard.teacher.analytics.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.ui.dashboard.teacher.analytics.model.AnalyticsItem;
import com.malapay.classroom.ui.dashboard.teacher.analytics.viewholder.BarChartViewHolder;
import com.malapay.classroom.ui.dashboard.teacher.analytics.viewholder.StudentListViewHolder;
import com.malapay.classroom.ui.dashboard.teacher.analytics.viewholder.SummaryViewHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Main adapter for the analytics RecyclerView. Supports three view types:
 * - Summary stat cards (circular progress + numbers)
 * - Score distribution bar chart
 * - Student performance list
 */
public class AnalyticsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<AnalyticsItem> items = new ArrayList<>();

    public void setItems(List<AnalyticsItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case AnalyticsItem.TYPE_SUMMARY:
                return new SummaryViewHolder(
                        inflater.inflate(R.layout.item_analytics_summary, parent, false));
            case AnalyticsItem.TYPE_BAR_CHART:
                return new BarChartViewHolder(
                        inflater.inflate(R.layout.item_analytics_bar_chart, parent, false));
            case AnalyticsItem.TYPE_STUDENT_LIST:
                return new StudentListViewHolder(
                        inflater.inflate(R.layout.item_analytics_student_list, parent, false));
            default:
                throw new IllegalArgumentException("Unknown view type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AnalyticsItem item = items.get(position);
        switch (item.getType()) {
            case AnalyticsItem.TYPE_SUMMARY:
                ((SummaryViewHolder) holder).bind(item.getSummary());
                break;
            case AnalyticsItem.TYPE_BAR_CHART:
                ((BarChartViewHolder) holder).bind(item.getDistribution());
                break;
            case AnalyticsItem.TYPE_STUDENT_LIST:
                ((StudentListViewHolder) holder).bind(item.getStudents());
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
