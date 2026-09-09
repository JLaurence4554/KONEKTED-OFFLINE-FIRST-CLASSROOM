package com.malapay.classroom.ui.quiz.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.data.local.model.QuizWithQuestionCount;
import com.malapay.classroom.databinding.ItemQuizTeacherBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Displays the teacher's quizzes for a lesson, with status, question count, and row actions. */
public class QuizAdapter extends RecyclerView.Adapter<QuizAdapter.ViewHolder> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public interface Listener {
        void onQuizClick(QuizWithQuestionCount item);
        void onEditQuiz(QuizWithQuestionCount item);
        void onDeleteQuiz(QuizWithQuestionCount item);
        void onViewResults(QuizWithQuestionCount item);
        void onTogglePublish(QuizWithQuestionCount item);
    }

    private final List<QuizWithQuestionCount> items = new ArrayList<>();
    private final Listener listener;

    public QuizAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<QuizWithQuestionCount> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQuizTeacherBinding binding = ItemQuizTeacherBinding.inflate(
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

        private final ItemQuizTeacherBinding binding;

        ViewHolder(ItemQuizTeacherBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(QuizWithQuestionCount item) {
            android.content.Context context = binding.getRoot().getContext();
            binding.tvQuizTitle.setText(item.quiz.getTitle());
            binding.tvQuizStatus.setText(item.quiz.isPublished()
                    ? context.getString(R.string.status_published)
                    : context.getString(R.string.status_draft));
            binding.tvQuizStatus.setBackgroundColor(context.getResources().getColor(
                    item.quiz.isPublished() ? R.color.secondary : R.color.text_secondary));
            binding.tvQuizMeta.setText(context.getString(R.string.quiz_question_count, item.questionCount)
                    + "  •  " + DATE_FORMAT.format(new Date(item.quiz.getCreatedAt())));

            binding.getRoot().setOnClickListener(v -> listener.onQuizClick(item));
            binding.btnOptions.setOnClickListener(v -> showOptionsMenu(v, item));
        }

        private void showOptionsMenu(View anchor, QuizWithQuestionCount item) {
            PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
            popup.getMenu().add(0, 1, 0, item.quiz.isPublished() ? R.string.action_unpublish : R.string.action_publish);
            popup.getMenu().add(0, 2, 1, R.string.action_view_results);
            popup.getMenu().add(0, 3, 2, R.string.action_edit);
            popup.getMenu().add(0, 4, 3, R.string.action_delete);
            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == 1) {
                    listener.onTogglePublish(item);
                    return true;
                } else if (menuItem.getItemId() == 2) {
                    listener.onViewResults(item);
                    return true;
                } else if (menuItem.getItemId() == 3) {
                    listener.onEditQuiz(item);
                    return true;
                } else if (menuItem.getItemId() == 4) {
                    listener.onDeleteQuiz(item);
                    return true;
                }
                return false;
            });
            popup.show();
        }
    }
}
