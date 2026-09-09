package com.malapay.classroom.ui.dashboard.student;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.malapay.classroom.R;
import com.malapay.classroom.databinding.FragmentStudentProfileBinding;
import com.malapay.classroom.ui.auth.LoginActivity;
import com.malapay.classroom.util.SessionManager;

/** "Profile" tab: read-only view of the logged-in student's session details. */
public class StudentProfileFragment extends Fragment {

    private FragmentStudentProfileBinding binding;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentStudentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());

        binding.tvProfileName.setText(sessionManager.getFullName());
        binding.tvProfileEmail.setText(getString(R.string.profile_email_label, sessionManager.getEmail()));
        binding.tvProfileRole.setText(getString(R.string.profile_role_label, sessionManager.getRole()));

        binding.btnLogout.setOnClickListener(v -> logout());
    }

    private void logout() {
        sessionManager.logout();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
