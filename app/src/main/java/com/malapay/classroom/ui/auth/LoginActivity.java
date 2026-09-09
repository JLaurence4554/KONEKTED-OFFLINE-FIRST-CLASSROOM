package com.malapay.classroom.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.graphics.Typeface;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputLayout;
import com.malapay.classroom.R;
import com.malapay.classroom.data.repository.AuthCallback;
import com.malapay.classroom.data.repository.UserRepository;
import com.malapay.classroom.data.local.entity.User;
import com.malapay.classroom.databinding.ActivityLoginBinding;
import com.malapay.classroom.ui.dashboard.student.StudentDashboardActivity;
import com.malapay.classroom.ui.dashboard.teacher.TeacherDashboardActivity;
import com.malapay.classroom.util.SessionManager;
import com.malapay.classroom.util.ValidationUtil;

/**
 * Entry point of the app. Auto-skips to the correct dashboard if a session
 * already exists; otherwise validates and authenticates credentials against
 * the local SQLite (Room) database.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private UserRepository userRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        // Requirement: automatically skip login if already authenticated.
        if (sessionManager.isLoggedIn()) {
            routeToDashboard(sessionManager.isTeacher());
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userRepository = new UserRepository(this);

        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        setupSignUpPrompt();
        binding.tvSignUpPrompt.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
    }

    private void setupSignUpPrompt() {
        String prompt = getString(R.string.prompt_no_account);
        String action = getString(R.string.action_sign_up);
        SpannableString spannable = new SpannableString(prompt + " " + action);
        int start = prompt.length();
        int end = start + action.length();
        spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.tvSignUpPrompt.setText(spannable);
    }

    private void attemptLogin() {
        clearErrors();

        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";

        boolean isValid = validateInputs(email, password);
        if (!isValid) {
            return;
        }

        setLoading(true);

        userRepository.login(email, password, new AuthCallback() {
            @Override
            public void onSuccess(User user) {
                setLoading(false);
                sessionManager.createSession(user);
                routeToDashboard(user.isTeacher());
            }

            @Override
            public void onFailure(AuthFailureReason reason) {
                setLoading(false);
                if (reason == AuthFailureReason.INVALID_CREDENTIALS) {
                    binding.tilPassword.setError(getString(R.string.error_invalid_credentials));
                } else {
                    binding.tilPassword.setError(getString(R.string.error_unexpected));
                }
            }
        });
    }

    /** Returns true if both fields pass validation; shows inline errors otherwise. */
    private boolean validateInputs(String email, String password) {
        boolean valid = true;

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
        }

        return valid;
    }

    private void clearErrors() {
        clearError(binding.tilEmail);
        clearError(binding.tilPassword);
    }

    private void clearError(TextInputLayout layout) {
        layout.setError(null);
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!loading);
    }

    private void routeToDashboard(boolean isTeacher) {
        Intent intent = new Intent(this,
                isTeacher ? TeacherDashboardActivity.class : StudentDashboardActivity.class);
        // Clear back stack so pressing "back" from the dashboard doesn't return to login.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
