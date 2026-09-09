package com.malapay.classroom.ui.lessons.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Lesson;
import com.malapay.classroom.databinding.ItemLessonBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Displays lessons for a class. In teacher mode each row shows an overflow
 * menu (Edit / Delete); in student mode the row is read-only and just opens
 * the lesson detail screen on tap.
 */
public class LessonAdapter extends RecyclerView.Adapter<LessonAdapter.ViewHolder> {

    private static final int SNIPPET_MAX_LENGTH = 120;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public interface Listener {
        void onLessonClick(Lesson lesson);
        void onEditLesson(Lesson lesson);
        void onDeleteLesson(Lesson lesson);
    }

    private final List<Lesson> items = new ArrayList<>();
    private final Listener listener;
    private final boolean showTeacherControls;

    public LessonAdapter(Listener listener, boolean showTeacherControls) {
        this.listener = listener;
        this.showTeacherControls = showTeacherControls;
    }

    public void submitList(List<Lesson> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLessonBinding binding = ItemLessonBinding.inflate(
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

        private final ItemLessonBinding binding;

        ViewHolder(ItemLessonBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Lesson lesson) {
            binding.tvLessonTitle.setText(lesson.getTitle());
            binding.tvLessonDate.setText(DATE_FORMAT.format(new java.util.Date(lesson.getCreatedAt())));
            binding.tvLessonSnippet.setText(snippet(lesson.getBody()));

            android.content.Context context = binding.getRoot().getContext();

            // Stats badges
            int attachCount = lesson.attachmentCount();
            binding.tvAttachmentCount.setText(context.getString(R.string.total_attachments_label, attachCount));
            binding.tvAttachmentCount.setVisibility(attachCount > 0 ? View.VISIBLE : View.GONE);

            binding.tvQuizCount.setText(context.getString(R.string.total_quizzes_label, 0));
            binding.tvQuizCount.setVisibility(View.GONE); // Show when quizzes exist

            // Updated date
            binding.tvUpdatedDate.setText(context.getString(R.string.last_updated_label, DATE_FORMAT.format(new java.util.Date(lesson.getUpdatedAt()))));

            binding.getRoot().setOnClickListener(v -> listener.onLessonClick(lesson));

            if (showTeacherControls) {
                binding.btnOptions.setVisibility(View.VISIBLE);
                binding.btnOptions.setOnClickListener(v -> showOptionsMenu(v, lesson));
            } else {
                binding.btnOptions.setVisibility(View.GONE);
                binding.btnOptions.setOnClickListener(null);
            }
        }

        private String snippet(String body) {
            if (body.length() <= SNIPPET_MAX_LENGTH) {
                return body;
            }
            return body.substring(0, SNIPPET_MAX_LENGTH).trim() + "…";
        }

        private void showOptionsMenu(View anchor, Lesson lesson) {
            PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
            popup.getMenu().add(0, 1, 0, R.string.action_edit);
            popup.getMenu().add(0, 2, 1, R.string.action_delete);
            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == 1) {
                    listener.onEditLesson(lesson);
                    return true;
                } else if (menuItem.getItemId() == 2) {
                    listener.onDeleteLesson(lesson);
                    return true;
                }
                return false;
            });
            popup.show();
        }
    }
}
