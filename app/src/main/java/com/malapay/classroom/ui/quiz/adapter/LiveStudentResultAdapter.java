package com.malapay.classroom.ui.quiz.adapter;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.databinding.ItemLiveStudentResultBinding;
import com.malapay.classroom.local.QuizSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RecyclerView adapter for the teacher's live quiz results list.
 * Shows each connected student's name, submission status, and score.
 */
public class LiveStudentResultAdapter extends RecyclerView.Adapter<LiveStudentResultAdapter.ViewHolder> {

    private List<QuizSession.StudentEntry> students = new ArrayList<>();
    private Map<Long, QuizSession.ResultEntry> results = new java.util.HashMap<>();

    public void updateData(List<QuizSession.StudentEntry> students,
                           Map<Long, QuizSession.ResultEntry> results) {
        this.students = new ArrayList<>(students);
        this.results = results;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLiveStudentResultBinding binding = ItemLiveStudentResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuizSession.StudentEntry student = students.get(position);
        QuizSession.ResultEntry result = results.get(student.id);

        holder.binding.tvStudentName.setText(student.name);
        holder.binding.tvStudentNumber.setText(String.valueOf(position + 1));

        if (result != null) {
            // Student has submitted
            holder.binding.tvStatus.setText(
                    String.format("%d%% (%d/%d)",
                            result.scorePercent, result.correctCount, result.totalQuestions));
            holder.binding.tvStatus.setBackgroundResource(R.drawable.bg_badge_synced);

            // Color based on pass/fail
            int color = result.scorePercent >= 70
                    ? ContextCompat.getColor(holder.itemView.getContext(), R.color.status_synced)
                    : ContextCompat.getColor(holder.itemView.getContext(), R.color.status_offline);
            holder.binding.tvStatus.setTextColor(color);
        } else {
            // Waiting
            holder.binding.tvStatus.setText(R.string.live_session_student_waiting);
            holder.binding.tvStatus.setBackgroundResource(R.drawable.bg_badge_info);
            holder.binding.tvStatus.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
        }
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemLiveStudentResultBinding binding;

        ViewHolder(ItemLiveStudentResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
