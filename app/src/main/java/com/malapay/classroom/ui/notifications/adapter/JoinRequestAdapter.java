package com.malapay.classroom.ui.notifications.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.data.local.model.JoinRequestWithDetails;
import com.malapay.classroom.databinding.ItemJoinRequestBinding;
import com.malapay.classroom.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays pending join requests with student name, class name,
 * request time, and Approve/Deny buttons.
 */
public class JoinRequestAdapter extends RecyclerView.Adapter<JoinRequestAdapter.ViewHolder> {

    public interface Listener {
        void onApprove(JoinRequestWithDetails request);
        void onDeny(JoinRequestWithDetails request);
    }

    private final Listener listener;
    private final List<JoinRequestWithDetails> items = new ArrayList<>();

    public JoinRequestAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<JoinRequestWithDetails> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemJoinRequestBinding binding = ItemJoinRequestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemJoinRequestBinding binding;

        ViewHolder(ItemJoinRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(JoinRequestWithDetails request, Listener listener) {
            binding.tvStudentName.setText(request.studentName);
            binding.tvClassName.setText(request.className);
            binding.tvRequestedAt.setText(TimeUtil.getRelativeTime(request.requestedAt));

            // Show initials in avatar
            String initials = "";
            if (request.studentName != null && !request.studentName.trim().isEmpty()) {
                initials = String.valueOf(Character.toUpperCase(request.studentName.trim().charAt(0)));
            }
            binding.ivAvatar.setImageDrawable(null);

            binding.btnApprove.setOnClickListener(v -> listener.onApprove(request));
            binding.btnDeny.setOnClickListener(v -> listener.onDeny(request));
        }
    }
}
