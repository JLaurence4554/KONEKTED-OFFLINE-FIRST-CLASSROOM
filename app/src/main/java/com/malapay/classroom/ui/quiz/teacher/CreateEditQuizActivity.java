package com.malapay.classroom.ui.quiz.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.data.local.entity.Quiz;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityCreateEditQuizBinding;
import com.malapay.classroom.util.ValidationUtil;

import java.util.List;

/**
 * Handles creating a new quiz and editing an existing one. In edit mode it
 * also doubles as the quiz's hub: Manage Questions, View Results, Publish/
 * Unpublish, and Delete Quiz all live here rather than a separate screen.
 * Publishing is blocked until the quiz has at least one question.
 */
public class CreateEditQuizActivity extends AppCompatActivity {

    public static final String EXTRA_LESSON_ID = "extra_lesson_id";
    public static final String EXTRA_QUIZ_ID = "extra_quiz_id";
    private static final long NO_ID = -1L;

    private ActivityCreateEditQuizBinding binding;
    private QuizRepository quizRepository;

    private long lessonId;
    private boolean isEditMode;
    private Quiz existingQuiz;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEditQuizBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        quizRepository = new QuizRepository(this);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        long quizId = getIntent().getLongExtra(EXTRA_QUIZ_ID, NO_ID);
        isEditMode = quizId != NO_ID;

        if (isEditMode) {
            binding.appHeader.txtHeaderTitle.setText(R.string.edit_quiz_title);
            binding.editModeActions.setVisibility(View.VISIBLE);
            loadExistingQuiz(quizId);
        } else {
            lessonId = getIntent().getLongExtra(EXTRA_LESSON_ID, NO_ID);
            binding.appHeader.txtHeaderTitle.setText(R.string.create_quiz_title);
        }

        binding.btnSave.setOnClickListener(v -> attemptSave());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (existingQuiz != null) {
            loadExistingQuiz(existingQuiz.getId());
        }
    }

    private void loadExistingQuiz(long quizId) {
        quizRepository.getQuizById(quizId, new RepositoryCallback<Quiz>() {
            @Override
            public void onSuccess(Quiz result) {
                existingQuiz = result;
                lessonId = result.getLessonId();
                binding.etTitle.setText(result.getTitle());
                binding.etInstructions.setText(result.getInstructions());
                binding.etTimeLimit.setText(result.getTimeLimitMinutes() != null
                        ? String.valueOf(result.getTimeLimitMinutes()) : "");
                binding.etPassingScore.setText(String.valueOf(result.getPassingScore()));
                wireEditModeButtons(result);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CreateEditQuizActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void wireEditModeButtons(Quiz quiz) {
        binding.btnPublishUnpublish.setText(quiz.isPublished() ? R.string.action_unpublish : R.string.action_publish);

        binding.btnManageQuestions.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuestionListActivity.class);
            intent.putExtra(QuestionListActivity.EXTRA_QUIZ_ID, quiz.getId());
            intent.putExtra(QuestionListActivity.EXTRA_QUIZ_TITLE, quiz.getTitle());
            startActivity(intent);
        });

        binding.btnViewResults.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuizResultsActivity.class);
            intent.putExtra(QuizResultsActivity.EXTRA_QUIZ_ID, quiz.getId());
            intent.putExtra(QuizResultsActivity.EXTRA_QUIZ_TITLE, quiz.getTitle());
            startActivity(intent);
        });

        binding.btnStartLiveSession.setOnClickListener(v -> {
            Intent intent = new Intent(this, LiveSessionActivity.class);
            intent.putExtra(LiveSessionActivity.EXTRA_QUIZ_ID, quiz.getId());
            startActivity(intent);
        });

        binding.btnPublishUnpublish.setOnClickListener(v -> attemptTogglePublish(quiz));

        binding.btnDeleteQuiz.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.delete_quiz_title)
                .setMessage(getString(R.string.delete_quiz_body, quiz.getTitle()))
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> performDelete(quiz))
                .show());
    }

    private void attemptTogglePublish(Quiz quiz) {
        if (!quiz.isPublished()) {
            quizRepository.getQuestionsForQuiz(quiz.getId(), new RepositoryCallback<List<Question>>() {
                @Override
                public void onSuccess(List<Question> result) {
                    if (result == null || result.isEmpty()) {
                        Toast.makeText(CreateEditQuizActivity.this, R.string.publish_needs_question, Toast.LENGTH_SHORT).show();
                    } else {
                        togglePublish(quiz);
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(CreateEditQuizActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            togglePublish(quiz);
        }
    }

    private void togglePublish(Quiz quiz) {
        quiz.setStatus(quiz.isPublished() ? Quiz.STATUS_DRAFT : Quiz.STATUS_PUBLISHED);
        quizRepository.updateQuiz(quiz, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                wireEditModeButtons(quiz);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CreateEditQuizActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performDelete(Quiz quiz) {
        quizRepository.deleteQuiz(quiz, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CreateEditQuizActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attemptSave() {
        binding.tilTitle.setError(null);
        binding.tilInstructions.setError(null);
        binding.tilPassingScore.setError(null);
        binding.tilTimeLimit.setError(null);

        String title = textOf(binding.etTitle);
        String instructions = textOf(binding.etInstructions);
        String timeLimitText = textOf(binding.etTimeLimit);
        String passingScoreText = textOf(binding.etPassingScore);

        boolean valid = true;
        if (!ValidationUtil.isNotEmpty(title)) {
            binding.tilTitle.setError(getString(R.string.error_field_required));
            valid = false;
        }
        if (!ValidationUtil.isNotEmpty(instructions)) {
            binding.tilInstructions.setError(getString(R.string.error_field_required));
            valid = false;
        }

        Integer timeLimit = null;
        if (!timeLimitText.isEmpty()) {
            try {
                timeLimit = Integer.parseInt(timeLimitText);
                if (timeLimit <= 0) {
                    binding.tilTimeLimit.setError(getString(R.string.error_time_limit_invalid));
                    valid = false;
                }
            } catch (NumberFormatException e) {
                binding.tilTimeLimit.setError(getString(R.string.error_time_limit_invalid));
                valid = false;
            }
        }

        int passingScore = 0;
        if (!ValidationUtil.isNotEmpty(passingScoreText)) {
            binding.tilPassingScore.setError(getString(R.string.error_field_required));
            valid = false;
        } else {
            try {
                passingScore = Integer.parseInt(passingScoreText);
                if (passingScore < 0 || passingScore > 100) {
                    binding.tilPassingScore.setError(getString(R.string.error_passing_score_range));
                    valid = false;
                }
            } catch (NumberFormatException e) {
                binding.tilPassingScore.setError(getString(R.string.error_passing_score_range));
                valid = false;
            }
        }

        if (!valid) {
            return;
        }

        setLoading(true);

        if (isEditMode && existingQuiz != null) {
            existingQuiz.setTitle(title);
            existingQuiz.setInstructions(instructions);
            existingQuiz.setTimeLimitMinutes(timeLimit);
            existingQuiz.setPassingScore(passingScore);
            quizRepository.updateQuiz(existingQuiz, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    setLoading(false);
                    finish();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Toast.makeText(CreateEditQuizActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            quizRepository.createQuiz(lessonId, title, instructions, timeLimit, passingScore,
                    new RepositoryCallback<Quiz>() {
                        @Override
                        public void onSuccess(Quiz result) {
                            // Push to server so students on other devices can see it
                            quizRepository.pushQuizToServer(result);
                            setLoading(false);
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            setLoading(false);
                            Toast.makeText(CreateEditQuizActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private String textOf(com.google.android.material.textfield.TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
    }
}
