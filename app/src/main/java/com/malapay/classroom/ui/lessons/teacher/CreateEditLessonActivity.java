package com.malapay.classroom.ui.lessons.teacher;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Lesson;
import com.malapay.classroom.data.repository.LessonRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityCreateEditLessonBinding;
import com.malapay.classroom.util.FileUtil;
import com.malapay.classroom.util.ValidationUtil;

/**
 * Handles both creating a new lesson and editing an existing one, including
 * an independent image and an independent generic attachment (e.g. PDF), plus
 * an optional subject and an optional video link (stored as plain text only —
 * never downloaded). Edit mode is triggered by passing {@link #EXTRA_LESSON_ID};
 * without it, the screen is in create mode and {@link #EXTRA_CLASS_ID} is required.
 */
public class CreateEditLessonActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID = "extra_class_id";
    public static final String EXTRA_LESSON_ID = "extra_lesson_id";
    private static final long NO_ID = -1L;

    private ActivityCreateEditLessonBinding binding;
    private LessonRepository lessonRepository;

    private long classId;
    private boolean isEditMode;
    private Lesson existingLesson; // non-null only in edit mode, once loaded

    private Uri pendingImageUri;
    private boolean imageRemoved;
    private Uri pendingAttachmentUri;
    private boolean attachmentRemoved;

    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEditLessonBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lessonRepository = new LessonRepository(this);

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                pendingImageUri = uri;
                imageRemoved = false;
                showImageName(FileUtil.getFileName(this, uri));
            }
        });

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                pendingAttachmentUri = uri;
                attachmentRemoved = false;
                showAttachmentName(FileUtil.getFileName(this, uri));
            }
        });

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        long lessonId = getIntent().getLongExtra(EXTRA_LESSON_ID, NO_ID);
        isEditMode = lessonId != NO_ID;

        if (isEditMode) {
            binding.appHeader.txtHeaderTitle.setText(R.string.edit_lesson_title);
            loadExistingLesson(lessonId);
        } else {
            classId = getIntent().getLongExtra(EXTRA_CLASS_ID, NO_ID);
            binding.appHeader.txtHeaderTitle.setText(R.string.create_lesson_title);
        }

        binding.btnAttachImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        binding.btnRemoveImage.setOnClickListener(v -> {
            pendingImageUri = null;
            imageRemoved = true;
            binding.btnAttachImage.setText(R.string.btn_attach_image);
            showImageName(null);
        });

        binding.btnAttachFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        binding.btnRemoveAttachment.setOnClickListener(v -> {
            pendingAttachmentUri = null;
            attachmentRemoved = true;
            binding.btnAttachFile.setText(R.string.btn_attach_file);
            showAttachmentName(null);
        });

        binding.btnSave.setOnClickListener(v -> attemptSave());
    }

    private void loadExistingLesson(long lessonId) {
        setLoading(true);
        lessonRepository.getLessonById(lessonId, new RepositoryCallback<Lesson>() {
            @Override
            public void onSuccess(Lesson result) {
                setLoading(false);
                existingLesson = result;
                classId = result.getClassId();
                binding.etTitle.setText(result.getTitle());
                binding.etBody.setText(result.getBody());
                binding.etSubject.setText(result.getSubject());
                binding.etVideoLink.setText(result.getVideoLink());

                if (result.hasImage()) {
                    showImageName(result.getImageName());
                    binding.btnAttachImage.setText(R.string.btn_replace_image);
                }
                if (result.hasAttachment()) {
                    showAttachmentName(result.getAttachmentName());
                    binding.btnAttachFile.setText(R.string.btn_replace_file);
                }
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(CreateEditLessonActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void attemptSave() {
        binding.tilTitle.setError(null);
        binding.tilBody.setError(null);

        String title = textOf(binding.etTitle);
        String body = textOf(binding.etBody);
        String subject = textOf(binding.etSubject);
        String videoLink = textOf(binding.etVideoLink);

        boolean valid = true;
        if (!ValidationUtil.isNotEmpty(title)) {
            binding.tilTitle.setError(getString(R.string.error_field_required));
            valid = false;
        }
        if (!ValidationUtil.isNotEmpty(body)) {
            binding.tilBody.setError(getString(R.string.error_field_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);

        if (isEditMode && existingLesson != null) {
            existingLesson.setTitle(title);
            existingLesson.setBody(body);
            existingLesson.setSubject(subject);
            existingLesson.setVideoLink(videoLink.isEmpty() ? null : videoLink);
            lessonRepository.updateLesson(existingLesson, pendingImageUri, imageRemoved,
                    pendingAttachmentUri, attachmentRemoved, new RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            setLoading(false);
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            setLoading(false);
                            Toast.makeText(CreateEditLessonActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            lessonRepository.createLesson(classId, title, body, subject, videoLink,
                    pendingImageUri, pendingAttachmentUri, new RepositoryCallback<Lesson>() {
                        @Override
                        public void onSuccess(Lesson result) {
                            // Push to server so students on other devices can see it
                            lessonRepository.pushLessonToServer(result);
                            setLoading(false);
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            setLoading(false);
                            Toast.makeText(CreateEditLessonActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void showImageName(String name) {
        binding.tvImageName.setText(name != null ? name : getString(R.string.no_image_label));
        binding.btnRemoveImage.setVisibility(name != null ? View.VISIBLE : View.GONE);
    }

    private void showAttachmentName(String name) {
        binding.tvAttachmentName.setText(name != null ? name : getString(R.string.no_attachment_label));
        binding.btnRemoveAttachment.setVisibility(name != null ? View.VISIBLE : View.GONE);
    }

    private String textOf(com.google.android.material.textfield.TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
    }
}
