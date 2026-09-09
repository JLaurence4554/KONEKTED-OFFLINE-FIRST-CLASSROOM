package com.malapay.classroom.ui.auth;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.ContextCompat;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.User;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.data.repository.UserRepository;
import com.malapay.classroom.databinding.ActivitySignUpBinding;
import com.malapay.classroom.ui.dashboard.student.StudentDashboardActivity;
import com.malapay.classroom.ui.dashboard.teacher.TeacherDashboardActivity;
import com.malapay.classroom.util.SessionManager;
import com.malapay.classroom.util.ValidationUtil;

/**
 * Registration screen: creates a new Teacher or Student account in the
 * local database. On success, the person is logged straight in (a session
 * is created immediately) and routed to the matching dashboard, rather
 * than being sent back to re-type their credentials on the Login screen.
 */
public class SignUpActivity extends AppCompatActivity {

    private ActivitySignUpBinding binding;
    private UserRepository userRepository;
    private SessionManager sessionManager;
    private String selectedRoleValue = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);

        binding.btnSignUp.setOnClickListener(v -> attemptSignUp());

        setupLoginPrompt();
        binding.tvLoginPrompt.setOnClickListener(v -> finish());

        binding.cardTeacher.setOnClickListener(v -> selectRole(User.ROLE_TEACHER));
        binding.cardStudent.setOnClickListener(v -> selectRole(User.ROLE_STUDENT));
    }

    private void setupLoginPrompt() {
        String prompt = getString(R.string.prompt_have_account);
        String action = getString(R.string.action_login);
        SpannableString spannable = new SpannableString(prompt + " " + action);
        int start = prompt.length();
        int end = start + action.length();
        spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.tvLoginPrompt.setText(spannable);
    }

    private void selectRole(String role) {
        selectedRoleValue = role;
        boolean isTeacher = User.ROLE_TEACHER.equals(role);

        binding.cardTeacher.setCardBackgroundColor(
                ContextCompat.getColor(this, isTeacher ? R.color.card_highlight : R.color.background));
        binding.cardTeacher.setStrokeColor(
                ContextCompat.getColor(this, isTeacher ? R.color.primary : R.color.outline));

        binding.cardStudent.setCardBackgroundColor(
                ContextCompat.getColor(this, !isTeacher ? R.color.card_highlight : R.color.background));
        binding.cardStudent.setStrokeColor(
                ContextCompat.getColor(this, !isTeacher ? R.color.secondary : R.color.outline));
    }

    private void attemptSignUp() {
        clearErrors();

        String fullName = textOf(binding.etFullName);
        String email = textOf(binding.etEmail);
        String password = textOf(binding.etPassword);
        String confirmPassword = textOf(binding.etConfirmPassword);
        String role = selectedRole();

        if (!validateInputs(fullName, email, password, confirmPassword, role)) {
            return;
        }

        setLoading(true);

        userRepository.register(fullName, email, password, role, new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User user) {
                setLoading(false);
                sessionManager.createSession(user);
                routeToDashboard(user.isTeacher());
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                binding.tilEmail.setError(message);
            }
        });
    }

    private boolean validateInputs(String fullName, String email, String password,
                                    String confirmPassword, String role) {
        boolean valid = true;

        if (!ValidationUtil.isNotEmpty(fullName)) {
            binding.tilFullName.setError(getString(R.string.error_full_name_required));
            valid = false;
        }

        if (!ValidationUtil.isNotEmpty(email)) {
            binding.tilEmail.setError(getString(R.string.error_email_required));
            valid = false;
        } else if (!ValidationUtil.isEmailValid(email)) {
            binding.tilEmail.setError(getString(R.string.error_email_invalid));
            valid = false;
        }

        if (!ValidationUtil.isNotEmpty(password)) {
            binding.tilPassword.setError(getString(R.string.error_password_required));
            valid = false;
        } else if (!ValidationUtil.isPasswordValid(password)) {
            binding.tilPassword.setError(getString(R.string.error_password_short));
            valid = false;
        } else if (!password.equals(confirmPassword)) {
            binding.tilConfirmPassword.setError(getString(R.string.error_password_mismatch));
            valid = false;
        }

        if (role == null) {
            android.widget.Toast.makeText(this, R.string.error_role_required, android.widget.Toast.LENGTH_SHORT).show();
            valid = false;
        }

        return valid;
    }

    /** Returns User.ROLE_TEACHER / User.ROLE_STUDENT based on the selected card, or null if none selected. */
    private String selectedRole() {
        return selectedRoleValue;
    }

    private void clearErrors() {
        binding.tilFullName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);
    }

    private String textOf(com.google.android.material.textfield.TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSignUp.setEnabled(!loading);
    }

    private void routeToDashboard(boolean isTeacher) {
        Intent intent = new Intent(this,
                isTeacher ? TeacherDashboardActivity.class : StudentDashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
