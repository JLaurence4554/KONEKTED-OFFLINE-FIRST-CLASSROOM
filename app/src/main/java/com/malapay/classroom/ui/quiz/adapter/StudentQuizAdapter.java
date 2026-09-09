package com.malapay.classroom.ui.quiz.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.QuizWithSubmissionStatus;
import com.malapay.classroom.databinding.ItemQuizStudentBinding;

import java.util.ArrayList;
import java.util.List;

/** Displays published quizzes for a lesson from the student's side: Take Quiz, or View My Score if already submitted. */
public class StudentQuizAdapter extends RecyclerView.Adapter<StudentQuizAdapter.ViewHolder> {

    public interface Listener {
        void onTakeQuiz(QuizWithSubmissionStatus item);
        void onViewScore(QuizWithSubmissionStatus item);
    }

    private final List<QuizWithSubmissionStatus> items = new ArrayList<>();
    private final Listener listener;

    public StudentQuizAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<QuizWithSubmissionStatus> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQuizStudentBinding binding = ItemQuizStudentBinding.inflate(
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

        private final ItemQuizStudentBinding binding;

        ViewHolder(ItemQuizStudentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(QuizWithSubmissionStatus item) {
            binding.tvQuizTitle.setText(item.quiz.getTitle());
            if (item.submitted) {
                binding.btnQuizAction.setText(R.string.btn_view_my_score);
                binding.btnQuizAction.setOnClickListener(v -> listener.onViewScore(item));
            } else {
                binding.btnQuizAction.setText(R.string.btn_take_quiz);
                binding.btnQuizAction.setOnClickListener(v -> listener.onTakeQuiz(item));
            }
        }
    }
}
