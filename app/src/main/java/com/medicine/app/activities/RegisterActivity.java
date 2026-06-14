package com.medicine.app.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.medicine.app.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private TextView tvMale, tvFemale, tvBirthday;
    private EditText etNickname, etName, etPhone;
    private LinearLayout llBirthday;
    private Button btnComplete;

    private String selectedGender = "";
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = FirebaseFirestore.getInstance();

        tvMale = findViewById(R.id.tv_male);
        tvFemale = findViewById(R.id.tv_female);
        tvBirthday = findViewById(R.id.tv_reg_birthday);
        etNickname = findViewById(R.id.et_reg_nickname);
        etName = findViewById(R.id.et_reg_name);
        etPhone = findViewById(R.id.et_reg_phone);
        llBirthday = findViewById(R.id.ll_birthday);
        btnComplete = findViewById(R.id.btn_complete);

        setupGenderSelection();
        setupBirthdayPicker();

        btnComplete.setOnClickListener(v -> completeRegistration());
    }

    private void setupGenderSelection() {
        tvMale.setOnClickListener(v -> {
            selectedGender = "남성";
            tvMale.setBackground(getDrawable(R.drawable.bg_gender_selected));
            tvMale.setTextColor(getColor(R.color.primary));
            tvFemale.setBackground(getDrawable(R.drawable.bg_gender_unselected));
            tvFemale.setTextColor(getColor(R.color.text_secondary));
        });

        tvFemale.setOnClickListener(v -> {
            selectedGender = "여성";
            tvFemale.setBackground(getDrawable(R.drawable.bg_gender_selected));
            tvFemale.setTextColor(getColor(R.color.primary));
            tvMale.setBackground(getDrawable(R.drawable.bg_gender_unselected));
            tvMale.setTextColor(getColor(R.color.text_secondary));
        });
    }

    private void setupBirthdayPicker() {
        llBirthday.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, year1, month1, dayOfMonth) -> {
                        String date = year1 + "-" + (month1 + 1) + "-" + dayOfMonth;
                        tvBirthday.setText(date);
                        tvBirthday.setTextColor(getColor(R.color.text_primary));
                    }, year, month, day);
            datePickerDialog.show();
        });
    }

    private void completeRegistration() {
        String nickname = etNickname.getText().toString().trim();
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String birthday = tvBirthday.getText().toString();

        if (TextUtils.isEmpty(selectedGender)) {
            Toast.makeText(this, "성별을 선택해주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(nickname)) {
            Toast.makeText(this, "닉네임을 입력해주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "이름을 입력해주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "전화번호를 입력해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        btnComplete.setEnabled(false);
        btnComplete.setText("확인 중...");

        final String fNickname = nickname, fName = name, fPhone = phone, fBirthday = birthday;

        // 1) 닉네임 중복 확인 → 2) 전화번호 중복 확인 → 3) 통과 시 가입
        db.collection("users").whereEqualTo("nickname", fNickname).limit(1).get()
            .addOnSuccessListener(nickSnap -> {
                if (!nickSnap.isEmpty()) {
                    resetCompleteButton();
                    Toast.makeText(this, "이미 사용 중인 닉네임입니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                db.collection("users").whereEqualTo("phone", fPhone).limit(1).get()
                    .addOnSuccessListener(phoneSnap -> {
                        if (!phoneSnap.isEmpty()) {
                            resetCompleteButton();
                            Toast.makeText(this, "이미 가입된 전화번호입니다.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        registerUser(fNickname, fName, fPhone, fBirthday);
                    })
                    .addOnFailureListener(e -> {
                        resetCompleteButton();
                        Toast.makeText(this, "중복 확인 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            })
            .addOnFailureListener(e -> {
                resetCompleteButton();
                Toast.makeText(this, "중복 확인 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private void resetCompleteButton() {
        btnComplete.setEnabled(true);
        btnComplete.setText("회원가입 완료");
    }

    private void registerUser(String nickname, String name, String phone, String birthday) {
        btnComplete.setText("처리 중...");
        String joinDate = new SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN).format(new Date());

        Map<String, Object> user = new HashMap<>();
        user.put("nickname", nickname);
        user.put("name", name);
        user.put("gender", selectedGender);
        user.put("birthday", birthday.equals("년-월-일") ? "" : birthday);
        user.put("phone", phone);
        user.put("joinDate", joinDate);
        user.put("createdAt", System.currentTimeMillis());

        db.collection("users")
            .add(user)
            .addOnSuccessListener(documentReference -> {
                String userId = documentReference.getId();

                SharedPreferences prefs = getSharedPreferences("medicine_prefs", MODE_PRIVATE);
                prefs.edit()
                    .putString("user_id", userId)
                    .putString("nickname", nickname)
                    .putString("name", name)
                    .putString("phone", phone)
                    .putString("join_date", joinDate)
                    .apply();

                Toast.makeText(this, "회원가입이 완료되었습니다!", Toast.LENGTH_LONG).show();

                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
            })
            .addOnFailureListener(e -> {
                resetCompleteButton();
                Toast.makeText(this, "오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
}
