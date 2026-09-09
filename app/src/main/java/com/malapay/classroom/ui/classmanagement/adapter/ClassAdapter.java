package com.malapay.classroom.ui.classmanagement.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.ClassWithStudentCount;
import com.malapay.classroom.databinding.ItemClassTeacherBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the teacher's classes. Tapping a row opens that class's Lessons
 * (the primary teacher workflow); Edit / Delete / View Students are
 * available through a PopupMenu on the overflow button.
 */
public class ClassAdapter extends RecyclerView.Adapter<ClassAdapter.ClassViewHolder> {

    /** Callbacks for row actions, implemented by the hosting Fragment. */
    public interface Listener {
        void onEditClass(ClassWithStudentCount item);
        void onDeleteClass(ClassWithStudentCount item);
        void onViewStudents(ClassWithStudentCount item);
        void onManageLessons(ClassWithStudentCount item);
    }

    private final List<ClassWithStudentCount> items = new ArrayList<>();
    private final Listener listener;

    public ClassAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<ClassWithStudentCount> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClassViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemClassTeacherBinding binding = ItemClassTeacherBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ClassViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ClassViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ClassViewHolder extends RecyclerView.ViewHolder {

        private final ItemClassTeacherBinding binding;

        ClassViewHolder(ItemClassTeacherBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ClassWithStudentCount item) {
            binding.tvClassName.setText(item.schoolClass.getClassName());
            binding.tvSubjectSection.setText(
                    item.schoolClass.getSubject() + " • " + item.schoolClass.getSection());
            binding.tvClassCode.setText(item.schoolClass.getClassCode());
            binding.tvStudentCount.setText(
                    binding.getRoot().getContext().getString(R.string.class_students_count, item.studentCount));

            binding.getRoot().setOnClickListener(v -> listener.onManageLessons(item));
            binding.btnOptions.setOnClickListener(v -> showOptionsMenu(v, item));
        }

        private void showOptionsMenu(View anchor, ClassWithStudentCount item) {
            PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
            popup.getMenu().add(0, 1, 0, R.string.lessons_title);
            popup.getMenu().add(0, 2, 1, R.string.action_view_students);
            popup.getMenu().add(0, 3, 2, R.string.action_edit);
            popup.getMenu().add(0, 4, 3, R.string.action_delete);
            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == 1) {
                    listener.onManageLessons(item);
                    return true;
                } else if (menuItem.getItemId() == 2) {
                    listener.onViewStudents(item);
                    return true;
                } else if (menuItem.getItemId() == 3) {
                    listener.onEditClass(item);
                    return true;
                } else if (menuItem.getItemId() == 4) {
                    listener.onDeleteClass(item);
                    return true;
                }
                return false;
            });
            popup.show();
        }
    }
}
