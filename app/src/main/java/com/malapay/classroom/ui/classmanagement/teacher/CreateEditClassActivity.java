package com.malapay.classroom.ui.classmanagement.teacher;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.SchoolClass;
import com.malapay.classroom.data.repository.ClassRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityCreateEditClassBinding;
import com.malapay.classroom.sync.SyncManager;
import com.malapay.classroom.util.SessionManager;
import com.malapay.classroom.util.ValidationUtil;

/**
 * Handles both creating a new class and editing an existing one.
 * Edit mode is triggered by passing {@link #EXTRA_CLASS_ID}; without it,
 * the screen is in create mode.
 */
public class CreateEditClassActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID = "extra_class_id";
    private static final long NO_ID = -1L;

    private ActivityCreateEditClassBinding binding;
    private ClassRepository classRepository;
    private SessionManager sessionManager;
    private SyncManager syncManager;

    private boolean isEditMode;
    private SchoolClass existingClass; // non-null only in edit mode, once loaded

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEditClassBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        classRepository = new ClassRepository(this);
        sessionManager = new SessionManager(this);
        syncManager = new SyncManager(this);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        long classId = getIntent().getLongExtra(EXTRA_CLASS_ID, NO_ID);
        isEditMode = classId != NO_ID;

        if (isEditMode) {
            binding.appHeader.txtHeaderTitle.setText(R.string.edit_class_title);
            loadExistingClass(classId);
        } else {
            binding.appHeader.txtHeaderTitle.setText(R.string.create_class_title);
        }

        binding.btnSave.setOnClickListener(v -> attemptSave());
    }

    private void loadExistingClass(long classId) {
        setLoading(true);
        classRepository.getClassById(classId, new RepositoryCallback<SchoolClass>() {
            @Override
            public void onSuccess(SchoolClass result) {
                setLoading(false);
                existingClass = result;
                binding.etClassName.setText(result.getClassName());
                binding.etSubject.setText(result.getSubject());
                binding.etSection.setText(result.getSection());
                binding.etSchoolYear.setText(result.getSchoolYear());
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                android.widget.Toast.makeText(CreateEditClassActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void attemptSave() {
        clearErrors();

        String className = textOf(binding.etClassName);
        String subject = textOf(binding.etSubject);
        String section = textOf(binding.etSection);
        String schoolYear = textOf(binding.etSchoolYear);

        boolean valid = true;
        valid &= requireNotEmpty(className, binding.tilClassName);
        valid &= requireNotEmpty(subject, binding.tilSubject);
        valid &= requireNotEmpty(section, binding.tilSection);
        valid &= requireNotEmpty(schoolYear, binding.tilSchoolYear);

        if (!valid) {
            return;
        }

        setLoading(true);

        if (isEditMode && existingClass != null) {
            existingClass.setClassName(className);
            existingClass.setSubject(subject);
            existingClass.setSection(section);
            existingClass.setSchoolYear(schoolYear);
            classRepository.updateClass(existingClass, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    setLoading(false);
                    finish();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    android.widget.Toast.makeText(CreateEditClassActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            long teacherId = sessionManager.getUserId();
            classRepository.createClass(teacherId, className, subject, section, schoolYear,
                    new RepositoryCallback<SchoolClass>() {
                        @Override
                        public void onSuccess(SchoolClass result) {
                            setLoading(false);
                            // Sync in background if online; class is already saved locally.
                            // Show a brief toast with the class code and go back to the list.
                            attemptImmediateSyncThenFinish(result);
                        }

                        @Override
                        public void onError(String message) {
                            setLoading(false);
                            android.widget.Toast.makeText(CreateEditClassActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void attemptImmediateSyncThenFinish(SchoolClass createdClass) {
        syncManager.syncPendingClasses(new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer stillPendingCount) {
                String message = getString(R.string.class_created_title) + "\n" + createdClass.getClassCode();
                android.widget.Toast.makeText(CreateEditClassActivity.this,
                        getString(R.string.join_class_success, createdClass.getClassName()),
                        android.widget.Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                // Offline, or the sync attempt itself failed to start — the class
                // is still saved locally as PENDING either way.
                android.widget.Toast.makeText(CreateEditClassActivity.this,
                        getString(R.string.class_created_title) + "\n" + createdClass.getClassCode(),
                        android.widget.Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private boolean requireNotEmpty(String value, TextInputLayout layout) {
        if (!ValidationUtil.isNotEmpty(value)) {
            layout.setError(getString(R.string.error_field_required));
            return false;
        }
        return true;
    }

    private void clearErrors() {
        binding.tilClassName.setError(null);
        binding.tilSubject.setError(null);
        binding.tilSection.setError(null);
        binding.tilSchoolYear.setError(null);
    }

    private String textOf(com.google.android.material.textfield.TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
    }
}
