package com.malapay.classroom.ui.dashboard.teacher.analytics.viewholder;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.model.StudentPerformance;
import com.malapay.classroom.ui.dashboard.teacher.analytics.adapter.StudentPerformanceAdapter;

import java.util.List;

/**
 * ViewHolder for the student performance list section. Contains a nested
 * RecyclerView that shows individual student rows.
 */
public class StudentListViewHolder extends RecyclerView.ViewHolder {

    private final RecyclerView recyclerStudents;
    private final TextView tvEmptyStudents;
    private StudentPerformanceAdapter adapter;

    public StudentListViewHolder(@NonNull View itemView) {
        super(itemView);
        recyclerStudents = itemView.findViewById(R.id.recyclerStudents);
        tvEmptyStudents = itemView.findViewById(R.id.tvEmptyStudents);

        recyclerStudents.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
        adapter = new StudentPerformanceAdapter();
        recyclerStudents.setAdapter(adapter);
    }

    public void bind(List<StudentPerformance> students) {
        if (students == null || students.isEmpty()) {
            recyclerStudents.setVisibility(View.GONE);
            tvEmptyStudents.setVisibility(View.VISIBLE);
        } else {
            recyclerStudents.setVisibility(View.VISIBLE);
            tvEmptyStudents.setVisibility(View.GONE);
            adapter.setData(students);
        }
    }
}
