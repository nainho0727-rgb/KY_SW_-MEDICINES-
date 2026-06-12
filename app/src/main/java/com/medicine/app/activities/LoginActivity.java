package com.medicine.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.medicine.app.R;

public class LoginActivity extends AppCompatActivity {

    private EditText etNickname, etPhone;
    private Button btnLogin, btnGoRegister;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = FirebaseFirestore.getInstance();

        etNickname = findViewById(R.id.et_nickname);
        etPhone = findViewById(R.id.et_phone);
        btnLogin = findViewById(R.id.btn_login);
        btnGoRegister = findViewById(R.id.btn_go_register);

        btnLogin.setOnClickListener(v -> attemptLogin());

        btnGoRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void attemptLogin() {
        String nickname = etNickname.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(nickname)) {
            Toast.makeText(this, "닉네임을 입력해주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "전화번호를 입력해주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        if (phone.length() != 11 || !phone.matches("^[0-9]*$")) {
            Toast.makeText(this, "전화번호는 11자리 숫자여야 합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("로그인 중...");

        db.collection("users")
            .whereEqualTo("nickname", nickname)
            .whereEqualTo("phone", phone)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                btnLogin.setEnabled(true);
                btnLogin.setText("로그인");

                if (!queryDocumentSnapshots.isEmpty()) {
                    QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                    String userId = doc.getId();
                    String name = doc.getString("name");
                    String joinDate = doc.getString("joinDate");

                    SharedPreferences prefs = getSharedPreferences("medicine_prefs", MODE_PRIVATE);
                    prefs.edit()
                        .putString("user_id", userId)
                        .putString("nickname", nickname)
                        .putString("name", name)
                        .putString("phone", phone)
                        .putString("join_date", joinDate)
                        .apply();

                    Toast.makeText(this, nickname + "님, 환영합니다!", Toast.LENGTH_SHORT).show();
                    
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                } else {
                    Toast.makeText(this, "일치하는 사용자 정보를 찾을 수 없습니다", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                btnLogin.setEnabled(true);
                btnLogin.setText("로그인");
                Toast.makeText(this, "오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
}
