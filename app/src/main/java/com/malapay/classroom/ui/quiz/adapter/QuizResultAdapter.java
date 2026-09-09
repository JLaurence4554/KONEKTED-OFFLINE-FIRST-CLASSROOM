package com.malapay.classroom.ui.quiz.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.QuizResultWithStudentName;
import com.malapay.classroom.databinding.ItemQuizResultBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Displays every student's submission for a single quiz. */
public class QuizResultAdapter extends RecyclerView.Adapter<QuizResultAdapter.ViewHolder> {

    public interface OnResultClickListener {
        void onResultClick(QuizResultWithStudentName item);
    }

    private OnResultClickListener clickListener;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());

    private final List<QuizResultWithStudentName> items = new ArrayList<>();

    public void setOnResultClickListener(OnResultClickListener listener) {
        this.clickListener = listener;
    }

    public void submitList(List<QuizResultWithStudentName> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQuizResultBinding binding = ItemQuizResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
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

        private final ItemQuizResultBinding binding;

        ViewHolder(ItemQuizResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(QuizResultWithStudentName item) {
            android.content.Context context = binding.getRoot().getContext();
            binding.tvStudentName.setText(item.studentName);
            binding.tvSubmittedDate.setText(DATE_FORMAT.format(new Date(item.result.getSubmittedAt())));
            binding.tvScore.setText(context.getString(R.string.score_label, item.result.getScore()));
            binding.tvCorrectCount.setText(context.getString(R.string.correct_count_label,
                    item.result.getCorrectCount(), item.result.getTotalQuestions()));
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) clickListener.onResultClick(item);
            });
        }
    }
}
