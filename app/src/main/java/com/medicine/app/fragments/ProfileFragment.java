package com.medicine.app.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.medicine.app.R;
import com.medicine.app.activities.LoginActivity;

import java.util.HashMap;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

public class ProfileFragment extends Fragment {

    private TextView tvNickname, tvPhone, tvJoinDate;
    private ImageButton btnEditProfile;
    private LinearLayout btnAccountSettings, btnLogout;
    private FirebaseFirestore db;
    private String userId, nickname, phone, joinDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = requireActivity().getSharedPreferences("medicine_prefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", "");
        nickname = prefs.getString("nickname", "사용자");
        phone = prefs.getString("phone", "");
        joinDate = prefs.getString("join_date", "");

        tvNickname = view.findViewById(R.id.tv_nickname);
        tvPhone = view.findViewById(R.id.tv_phone);
        tvJoinDate = view.findViewById(R.id.tv_join_date);
        btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        btnAccountSettings = view.findViewById(R.id.btn_account_settings);
        btnLogout = view.findViewById(R.id.btn_logout);

        tvNickname.setText(nickname);
        tvPhone.setText(phone.isEmpty() ? "전화번호 없음" : phone);
        tvJoinDate.setText(joinDate.isEmpty() ? "가입일 없음" : joinDate);

        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        btnAccountSettings.setOnClickListener(v -> showAccountSettingsDialog());

        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    private void showEditProfileDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_profile, null);
        TextInputEditText etNickname = dialogView.findViewById(R.id.et_edit_nickname);
        TextInputEditText etPhone = dialogView.findViewById(R.id.et_edit_phone);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);

        etNickname.setText(nickname);
        etPhone.setText(phone);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
            .setView(dialogView)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newNickname = etNickname.getText() != null ? etNickname.getText().toString().trim() : "";
            String newPhone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

            if (TextUtils.isEmpty(newNickname)) {
                Toast.makeText(getContext(), "닉네임을 입력해주세요", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPhone.isEmpty() && (newPhone.length() != 11 || !newPhone.matches("^[0-9]*$"))) {
                Toast.makeText(getContext(), "전화번호는 11자리 숫자여야 합니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("nickname", newNickname);
            updates.put("phone", newPhone);

            db.collection("users")
                .document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    SharedPreferences prefs = requireActivity().getSharedPreferences("medicine_prefs", MODE_PRIVATE);
                    prefs.edit()
                        .putString("nickname", newNickname)
                        .putString("phone", newPhone)
                        .apply();

                    nickname = newNickname;
                    phone = newPhone;
                    tvNickname.setText(nickname);
                    tvPhone.setText(phone.isEmpty() ? "전화번호 없음" : phone);

                    Toast.makeText(getContext(), "프로필이 수정되었습니다", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .addOnFailureListener(e ->
                    Toast.makeText(getContext(), "수정 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
        });

        dialog.show();
    }

    private void showAccountSettingsDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
            .setTitle("계정 설정")
            .setMessage("계정 관련 설정을 선택하세요")
            .setPositiveButton("프로필 수정", (dialog, which) -> showEditProfileDialog())
            .setNegativeButton("닫기", null)
            .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
            .setTitle("로그아웃")
            .setMessage("로그아웃 하시겠습니까?")
            .setPositiveButton("로그아웃", (dialog, which) -> logout())
            .setNegativeButton("취소", null)
            .show();
    }

    private void logout() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("medicine_prefs", MODE_PRIVATE);
        prefs.edit().clear().apply();

        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
