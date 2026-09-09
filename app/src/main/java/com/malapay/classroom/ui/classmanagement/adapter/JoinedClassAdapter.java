package com.malapay.classroom.ui.classmanagement.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.databinding.ItemClassStudentBinding;

import java.util.ArrayList;
import java.util.List;

/** Displays the classes a student has joined. Tapping a row opens its lessons. */
public class JoinedClassAdapter extends RecyclerView.Adapter<JoinedClassAdapter.ViewHolder> {

    public interface Listener {
        void onClassClick(SchoolClass schoolClass);
        void onLeaveClass(SchoolClass schoolClass);
    }

    private final List<SchoolClass> items = new ArrayList<>();
    private final Listener listener;

    public JoinedClassAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<SchoolClass> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemClassStudentBinding binding = ItemClassStudentBinding.inflate(
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

        private final ItemClassStudentBinding binding;

        ViewHolder(ItemClassStudentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SchoolClass schoolClass) {
            binding.tvClassName.setText(schoolClass.getClassName());
            binding.tvSubjectSection.setText(
                    schoolClass.getSubject() + " • " + schoolClass.getSection());
            binding.tvSchoolYear.setText(schoolClass.getSchoolYear());
            binding.getRoot().setOnClickListener(v -> listener.onClassClick(schoolClass));
            binding.btnLeaveClass.setOnClickListener(v -> listener.onLeaveClass(schoolClass));
        }
    }
}
