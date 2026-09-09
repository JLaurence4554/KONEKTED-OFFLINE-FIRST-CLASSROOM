package com.malapay.classroom.ui.dashboard.student;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.textfield.TextInputEditText;
import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.data.repository.ClassRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityMyClassesBinding;
import com.malapay.classroom.databinding.DialogJoinClassBinding;
import com.malapay.classroom.sync.SyncManager;
import com.malapay.classroom.ui.classmanagement.adapter.JoinedClassAdapter;
import com.malapay.classroom.ui.lessons.LessonsListActivity;
import com.malapay.classroom.util.SessionManager;
import com.malapay.classroom.util.ValidationUtil;

import java.util.List;

/**
 * "My Classes" screen: shows every class the student has joined, and
 * exposes the Join Class flow via a FAB + AlertDialog.
 *
 * PHASE: online sync. Joining is no longer a purely local DB lookup — the
 * Student's device may never have been on the same network as the Teacher's,
 * so the class code has to be resolved through the backend (see
 * {@link SyncManager#joinClassOnline}). The UI here stays exactly the same
 * "enter a code, tap Join" flow the spec requires; all the online/offline
 * branching happens behind that one method call.
 * Supports pull-to-refresh and leaving a class.
 */
public class MyClassesActivity extends AppCompatActivity implements JoinedClassAdapter.Listener {

    private ActivityMyClassesBinding binding;
    private ClassRepository classRepository;
    private SessionManager sessionManager;
    private SyncManager syncManager;
    private JoinedClassAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyClassesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        classRepository = new ClassRepository(this);
        sessionManager = new SessionManager(this);
        syncManager = new SyncManager(this);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.txtHeaderTitle.setText(R.string.my_classes_title);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        adapter = new JoinedClassAdapter(this);
        binding.recyclerJoinedClasses.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerJoinedClasses.setAdapter(adapter);

        binding.fabJoinClass.setOnClickListener(v -> showJoinClassDialog());

        // Pull-to-refresh: sync from server then reload local data
        binding.swipeRefresh.setColorSchemeResources(R.color.secondary);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            syncManager.syncAllSilently();
            fetchAndLoadClasses();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // First fetch approved classes from the server, then load local
        fetchAndLoadClasses();
        // Opportunistic catch-up: if a previous join or class creation on
        // this device is still PENDING/FAILED and we're online now, push it
        // through. Silent — no UI feedback, and a no-op if nothing's pending.
        syncManager.syncAllSilently();
    }

    /**
     * Fetches the student's approved classes from the server first,
     * then loads from local DB. This ensures classes approved by the
     * teacher on another device appear here immediately.
     */
    private void fetchAndLoadClasses() {
        String email = sessionManager.getEmail();
        syncManager.fetchStudentClassesFromServer(email, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                loadJoinedClasses();
            }

            @Override
            public void onError(String message) {
                // Server unavailable, just show local data
                loadJoinedClasses();
            }
        });
    }

    private void loadJoinedClasses() {
        long studentId = sessionManager.getUserId();
        classRepository.getJoinedClasses(studentId, new RepositoryCallback<List<SchoolClass>>() {
            @Override
            public void onSuccess(List<SchoolClass> result) {
                adapter.submitList(result);
                boolean isEmpty = result == null || result.isEmpty();
                binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerJoinedClasses.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                binding.swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MyClassesActivity.this, message, Toast.LENGTH_SHORT).show();
                binding.swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void showJoinClassDialog() {
        DialogJoinClassBinding dialogBinding = DialogJoinClassBinding.inflate(LayoutInflater.from(this));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.join_class_title)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_join, null) // set below to control dismiss timing
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                        attemptJoin(dialogBinding, dialog)));

        dialog.show();
    }

    /**
     * The Student's flow from the spec: enter a code, tap Join, and let
     * {@link SyncManager#joinClassOnline} handle connectivity checking,
     * the backend lookup, local save, membership recording, and membership
     * upload — surfacing whichever of the spec's required error strings
     * applies (offline / not found / already joined / server error /
     * timeout) directly on the code field.
     */
    private void attemptJoin(DialogJoinClassBinding dialogBinding, AlertDialog dialog) {
        String code = dialogBinding.etClassCode.getText() != null
                ? dialogBinding.etClassCode.getText().toString().trim() : "";

        if (!ValidationUtil.isNotEmpty(code)) {
            dialogBinding.etClassCode.setError(getString(R.string.error_field_required));
            return;
        }

        dialogBinding.etClassCode.setError(null);
        setDialogBusy(dialog, dialogBinding, true);

        long studentId = sessionManager.getUserId();
        String studentName = sessionManager.getFullName();
        String studentEmail = sessionManager.getEmail();

        syncManager.joinClassOnline(studentId, studentName, studentEmail, code, new RepositoryCallback<SchoolClass>() {
            @Override
            public void onSuccess(SchoolClass result) {
                dialog.dismiss();
                Toast.makeText(MyClassesActivity.this,
                        R.string.join_request_sent, Toast.LENGTH_LONG).show();
                loadJoinedClasses();
                // Immediately push the join request to the server so the
                // teacher sees it right away instead of waiting for onResume.
                syncManager.syncAllSilently();
            }

            @Override
            public void onError(String message) {
                setDialogBusy(dialog, dialogBinding, false);
                dialogBinding.etClassCode.setError(message);
            }
        });
    }

    private void setDialogBusy(AlertDialog dialog, DialogJoinClassBinding dialogBinding, boolean busy) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(busy ? R.string.btn_joining : R.string.btn_join);
        dialog.setCancelable(!busy);
        dialogBinding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClassClick(SchoolClass schoolClass) {
        Intent intent = new Intent(this, LessonsListActivity.class);
        intent.putExtra(LessonsListActivity.EXTRA_CLASS_ID, schoolClass.getId());
        intent.putExtra(LessonsListActivity.EXTRA_CLASS_NAME, schoolClass.getClassName());
        startActivity(intent);
    }

    @Override
    public void onLeaveClass(SchoolClass schoolClass) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.leave_class_title)
                .setMessage(R.string.leave_class_body)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_leave, (dialog, which) -> performLeaveClass(schoolClass))
                .show();
    }

    private void performLeaveClass(SchoolClass schoolClass) {
        long studentId = sessionManager.getUserId();
        String studentEmail = sessionManager.getEmail();

        // 1. Remove locally
        classRepository.leaveClass(schoolClass.getId(), studentId, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(MyClassesActivity.this,
                        R.string.class_left, Toast.LENGTH_SHORT).show();
                loadJoinedClasses();

                // 2. Push the leave to the server (best-effort)
                syncManager.leaveClassOnline(schoolClass.getClassCode(), studentEmail,
                        new RepositoryCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                // Server updated
                            }

                            @Override
                            public void onError(String message) {
                                // Offline or server error — the leave is saved
                                // locally and will be handled on next sync
                            }
                        });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MyClassesActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
