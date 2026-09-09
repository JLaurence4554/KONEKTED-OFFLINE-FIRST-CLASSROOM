package com.malapay.classroom.ui.quiz.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.databinding.ItemQuestionBinding;

import java.util.ArrayList;
import java.util.List;

/** Displays a quiz's questions in order, with move up/down, edit (tap), and delete. */
public class QuestionAdapter extends RecyclerView.Adapter<QuestionAdapter.ViewHolder> {

    public interface Listener {
        void onQuestionClick(Question question);
        void onDeleteQuestion(Question question);
        void onMoveUp(int position);
        void onMoveDown(int position);
    }

    private final List<Question> items = new ArrayList<>();
    private final Listener listener;

    public QuestionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<Question> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQuestionBinding binding = ItemQuestionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemQuestionBinding binding;

        ViewHolder(ItemQuestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Question question, int position) {
            android.content.Context context = binding.getRoot().getContext();
            binding.tvQuestionNumber.setText("Q" + (position + 1));
            binding.tvQuestionText.setText(question.getQuestionText());

            // Show question type badge
            binding.tvQuestionType.setText(question.getTypeLabel());
            binding.tvQuestionType.setVisibility(View.VISIBLE);

            // Show answer based on type
            if (question.isMultipleChoice()) {
                binding.tvCorrectAnswer.setText(context.getString(R.string.label_correct_answer)
                        + ": " + question.getCorrectAnswer() + " - " + question.getCorrectChoiceText());
            } else if (question.isIdentification()) {
                binding.tvCorrectAnswer.setText(context.getString(R.string.label_correct_answer)
                        + ": " + (question.getAnswerText() != null ? question.getAnswerText() : ""));
            } else if (question.isEssay()) {
                binding.tvCorrectAnswer.setText(context.getString(R.string.label_needs_manual_grading));
            }

            binding.getRoot().setOnClickListener(v -> listener.onQuestionClick(question));
            binding.btnDeleteQuestion.setOnClickListener(v -> listener.onDeleteQuestion(question));

            binding.btnMoveUp.setEnabled(position > 0);
            binding.btnMoveUp.setAlpha(position > 0 ? 1f : 0.3f);
            binding.btnMoveUp.setOnClickListener(v -> listener.onMoveUp(position));

            binding.btnMoveDown.setEnabled(position < getItemCount() - 1);
            binding.btnMoveDown.setAlpha(position < getItemCount() - 1 ? 1f : 0.3f);
            binding.btnMoveDown.setOnClickListener(v -> listener.onMoveDown(position));
        }
    }
}
