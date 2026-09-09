package com.malapay.classroom.ui.notifications;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.AppDatabase;
import com.malapay.classroom.data.local.dao.ClassMemberDao;
import com.malapay.classroom.data.local.dao.JoinRequestDao;
import com.malapay.classroom.data.local.entity.ClassMember;
import com.malapay.classroom.data.local.entity.JoinRequest;
import com.malapay.classroom.data.local.model.JoinRequestWithDetails;
import com.malapay.classroom.databinding.ActivityJoinRequestsBinding;
import com.malapay.classroom.ui.notifications.adapter.JoinRequestAdapter;
import com.malapay.classroom.util.HeaderHelper;
import com.malapay.classroom.util.SessionManager;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.sync.SyncManager;

import java.util.List;

/**
 * Shows all pending join requests for the teacher's classes.
 * Teacher can approve (creates ClassMember) or deny each request.
 */
public class JoinRequestsActivity extends AppCompatActivity implements JoinRequestAdapter.Listener {

    private ActivityJoinRequestsBinding binding;
    private JoinRequestDao joinRequestDao;
    private ClassMemberDao classMemberDao;
    private SessionManager sessionManager;
    private JoinRequestAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityJoinRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppDatabase db = AppDatabase.getInstance(this);
        joinRequestDao = db.joinRequestDao();
        classMemberDao = db.classMemberDao();
        sessionManager = new SessionManager(this);

        HeaderHelper.setupSubHeader(this, getString(R.string.join_requests_title), true);

        adapter = new JoinRequestAdapter(this);
        binding.recyclerJoinRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerJoinRequests.setAdapter(adapter);

        loadPendingRequests();
    }

    private void loadPendingRequests() {
        if (isFinishing() || isDestroyed()) return;
        // First fetch from server, then reload local
        SyncManager syncManager = new SyncManager(this);
        String teacherEmail = sessionManager.getEmail();
        syncManager.fetchJoinRequestsFromServer(teacherEmail, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                // Fetched from server, now reload local
                reloadLocalRequests();
            }

            @Override
            public void onError(String message) {
                // Server unavailable, just show local data
                reloadLocalRequests();
            }
        });
    }

    private void reloadLocalRequests() {
        if (isFinishing() || isDestroyed()) return;
        long teacherId = sessionManager.getUserId();
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            List<JoinRequestWithDetails> requests = joinRequestDao.getPendingRequestsWithDetails(teacherId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                adapter.submitList(requests);
                boolean isEmpty = requests == null || requests.isEmpty();
                binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerJoinRequests.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            });
        });
    }

    @Override
    public void onApprove(JoinRequestWithDetails request) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.approve_request_title)
                .setMessage(getString(R.string.approve_request_body, request.studentName, request.className))
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_approve, (dialog, which) -> performApprove(request))
                .show();
    }

    private void performApprove(JoinRequestWithDetails request) {
        // First: local DB writes
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            try {
                // Create the class membership locally
                ClassMember member = new ClassMember(request.classId, request.studentId, System.currentTimeMillis());
                classMemberDao.insert(member);

                // Mark request as approved locally
                joinRequestDao.approveRequest(request.requestId, System.currentTimeMillis());
            } catch (Exception e) {
                android.util.Log.e("JoinRequestsActivity", "Local approve failed: " + e.getMessage());
            }
        });

        // Second: sync the approval to the server so the student can see the class
        SyncManager syncManager = new SyncManager(this);
        if (request.classCode != null && request.studentEmail != null) {
            syncManager.approveJoinRequestOnServer(request.classCode, request.studentEmail, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    android.util.Log.d("JoinRequestsActivity", "Server approve synced");
                }

                @Override
                public void onError(String message) {
                    android.util.Log.w("JoinRequestsActivity", "Server approve failed: " + message);
                }
            });
        }

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this,
                    getString(R.string.request_approved, request.studentName),
                    Toast.LENGTH_SHORT).show();
            loadPendingRequests();
        });
    }

    @Override
    public void onDeny(JoinRequestWithDetails request) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.deny_request_title)
                .setMessage(getString(R.string.deny_request_body, request.studentName, request.className))
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_deny, (dialog, which) -> performDeny(request))
                .show();
    }
    private void performDeny(JoinRequestWithDetails request) {
        AppDatabase.DATABASE_WRITE_EXECUTOR.execute(() -> {
            joinRequestDao.denyRequest(request.requestId, System.currentTimeMillis());

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this,
                        getString(R.string.request_denied, request.studentName),
                        Toast.LENGTH_SHORT).show();
                loadPendingRequests();
            });
        });
    }
}
