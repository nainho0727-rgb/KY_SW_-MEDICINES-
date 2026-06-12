package com.medicine.app.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.cardview.widget.CardView;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.medicine.app.R;
import com.medicine.app.activities.OcrActivity;
import com.medicine.app.adapters.MedicineAdapter;
import com.medicine.app.models.Medicine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

public class MyMedicineFragment extends Fragment {

    private static final int CAMERA_PERMISSION_CODE = 1001;
    private static final int OCR_REQUEST_CODE = 100;

    private TextView tvTotalCount, tvMorningCount, tvLunchCount, tvEveningCount, tvBedtimeCount;
    private RecyclerView rvMorning, rvLunch, rvEvening, rvBedtime;
    private CardView cardEmptyMorning, cardEmptyLunch, cardEmptyEvening, cardEmptyBedtime;
    private Button btnAddMedicine, btnOcr;

    private MedicineAdapter morningAdapter, lunchAdapter, eveningAdapter, bedtimeAdapter;
    private List<Medicine> morningList = new ArrayList<>();
    private List<Medicine> lunchList = new ArrayList<>();
    private List<Medicine> eveningList = new ArrayList<>();
    private List<Medicine> bedtimeList = new ArrayList<>();

    private FirebaseFirestore db;
    private String userId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_medicine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = requireActivity().getSharedPreferences("medicine_prefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", "");

        tvTotalCount = view.findViewById(R.id.tv_total_count);
        tvMorningCount = view.findViewById(R.id.tv_morning_count);
        tvLunchCount = view.findViewById(R.id.tv_lunch_count);
        tvEveningCount = view.findViewById(R.id.tv_evening_count);
        tvBedtimeCount = view.findViewById(R.id.tv_bedtime_count);

        rvMorning = view.findViewById(R.id.rv_morning);
        rvLunch = view.findViewById(R.id.rv_lunch);
        rvEvening = view.findViewById(R.id.rv_evening);
        rvBedtime = view.findViewById(R.id.rv_bedtime);

        cardEmptyMorning = view.findViewById(R.id.card_empty_morning);
        cardEmptyLunch = view.findViewById(R.id.card_empty_lunch);
        cardEmptyEvening = view.findViewById(R.id.card_empty_evening);
        cardEmptyBedtime = view.findViewById(R.id.card_empty_bedtime);

        btnAddMedicine = view.findViewById(R.id.btn_add_medicine);
        btnOcr = view.findViewById(R.id.btn_ocr);

        setupRecyclerViews();

        btnAddMedicine.setOnClickListener(v -> showAddMedicineDialog(null));

        btnOcr.setOnClickListener(v -> checkCameraPermission());

        loadMedicines();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMedicines(); // 화면 돌아올 때마다 갱신
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startOcrActivity();
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                showPermissionGuideDialog();
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            }
        }
    }

    private void showPermissionGuideDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("카메라 권한 필요")
            .setMessage("처방전 촬영 기능을 사용하려면 카메라 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
            .setPositiveButton("설정으로 이동", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            })
            .setNegativeButton("취소", null)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startOcrActivity();
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    showPermissionGuideDialog();
                } else {
                    Toast.makeText(getContext(), "카메라 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void startOcrActivity() {
        Intent intent = new Intent(getActivity(), OcrActivity.class);
        startActivityForResult(intent, OCR_REQUEST_CODE);
    }

    private void setupRecyclerViews() {
        morningAdapter = new MedicineAdapter(morningList, (medicine, pos) -> confirmDelete(medicine));
        lunchAdapter = new MedicineAdapter(lunchList, (medicine, pos) -> confirmDelete(medicine));
        eveningAdapter = new MedicineAdapter(eveningList, (medicine, pos) -> confirmDelete(medicine));
        bedtimeAdapter = new MedicineAdapter(bedtimeList, (medicine, pos) -> confirmDelete(medicine));

        rvMorning.setLayoutManager(new LinearLayoutManager(getContext()));
        rvMorning.setAdapter(morningAdapter);
        rvMorning.setNestedScrollingEnabled(false);

        rvLunch.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLunch.setAdapter(lunchAdapter);
        rvLunch.setNestedScrollingEnabled(false);

        rvEvening.setLayoutManager(new LinearLayoutManager(getContext()));
        rvEvening.setAdapter(eveningAdapter);
        rvEvening.setNestedScrollingEnabled(false);

        rvBedtime.setLayoutManager(new LinearLayoutManager(getContext()));
        rvBedtime.setAdapter(bedtimeAdapter);
        rvBedtime.setNestedScrollingEnabled(false);
    }

    private void loadMedicines() {
        if (userId == null || userId.isEmpty()) return;

        db.collection("medicines")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                morningList.clear();
                lunchList.clear();
                eveningList.clear();
                bedtimeList.clear();

                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    Medicine medicine = doc.toObject(Medicine.class);
                    medicine.setId(doc.getId());

                    List<String> slots = medicine.getTimeSlots();
                    if (slots != null) {
                        for (String slot : slots) {
                            switch (slot) {
                                case "morning": morningList.add(medicine); break;
                                case "lunch": lunchList.add(medicine); break;
                                case "evening": eveningList.add(medicine); break;
                                case "bedtime": bedtimeList.add(medicine); break;
                            }
                        }
                    }
                }
                updateUI();
            });
    }

    private void updateUI() {
        if (getContext() == null) return;

        int total = morningList.size() + lunchList.size() + eveningList.size() + bedtimeList.size();
        tvTotalCount.setText("총 " + total + "개 등록됨");

        tvMorningCount.setText("(" + morningList.size() + "개)");
        tvLunchCount.setText("(" + lunchList.size() + "개)");
        tvEveningCount.setText("(" + eveningList.size() + "개)");
        tvBedtimeCount.setText("(" + bedtimeList.size() + "개)");

        morningAdapter.updateList(new ArrayList<>(morningList));
        lunchAdapter.updateList(new ArrayList<>(lunchList));
        eveningAdapter.updateList(new ArrayList<>(eveningList));
        bedtimeAdapter.updateList(new ArrayList<>(bedtimeList));

        cardEmptyMorning.setVisibility(morningList.isEmpty() ? View.VISIBLE : View.GONE);
        cardEmptyLunch.setVisibility(lunchList.isEmpty() ? View.VISIBLE : View.GONE);
        cardEmptyEvening.setVisibility(eveningList.isEmpty() ? View.VISIBLE : View.GONE);
        cardEmptyBedtime.setVisibility(bedtimeList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    public void showAddMedicineDialog(String prefillName) {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_medicine, null);

        TextInputEditText etName = dialogView.findViewById(R.id.et_medicine_name);
        TextInputEditText etDose = dialogView.findViewById(R.id.et_dose);
        TextInputEditText etMemo = dialogView.findViewById(R.id.et_memo);
        CheckBox cbMorning = dialogView.findViewById(R.id.cb_morning);
        CheckBox cbLunch = dialogView.findViewById(R.id.cb_lunch);
        CheckBox cbEvening = dialogView.findViewById(R.id.cb_evening);
        CheckBox cbBedtime = dialogView.findViewById(R.id.cb_bedtime);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);

        if (prefillName != null) etName.setText(prefillName);
        cbMorning.setChecked(true);

        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String dose = etDose.getText() != null ? etDose.getText().toString().trim() : "";
            String memo = etMemo.getText() != null ? etMemo.getText().toString().trim() : "";

            if (TextUtils.isEmpty(name)) {
                Toast.makeText(getContext(), "약 이름을 입력해주세요", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> timeSlots = new ArrayList<>();
            if (cbMorning.isChecked()) timeSlots.add("morning");
            if (cbLunch.isChecked()) timeSlots.add("lunch");
            if (cbEvening.isChecked()) timeSlots.add("evening");
            if (cbBedtime.isChecked()) timeSlots.add("bedtime");

            if (timeSlots.isEmpty()) {
                Toast.makeText(getContext(), "복용 시간을 선택해주세요", Toast.LENGTH_SHORT).show();
                return;
            }

            saveMedicine(name, dose.isEmpty() ? "1정" : dose, timeSlots, memo);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void saveMedicine(String name, String dose, List<String> timeSlots, String memo) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("dose", dose);
        data.put("timeSlots", timeSlots);
        data.put("memo", memo);
        data.put("userId", userId);
        data.put("taken", false);
        data.put("createdAt", System.currentTimeMillis());

        db.collection("medicines").add(data).addOnSuccessListener(ref -> {
            Toast.makeText(getContext(), "약이 추가되었습니다", Toast.LENGTH_SHORT).show();
            loadMedicines();
        });
    }

    private void confirmDelete(Medicine medicine) {
        new AlertDialog.Builder(requireContext())
            .setTitle("약 삭제")
            .setMessage(medicine.getName() + "을(를) 삭제하시겠습니까? 관련 복용 기록도 모두 삭제됩니다.")
            .setPositiveButton("삭제", (dialog, which) -> deleteMedicineAndRecords(medicine))
            .setNegativeButton("취소", null)
            .show();
    }

    private void deleteMedicineAndRecords(Medicine medicine) {
        // 1. 약 삭제 및 관련 기록 삭제를 위한 Batch 처리
        WriteBatch batch = db.batch();
        batch.delete(db.collection("medicines").document(medicine.getId()));

        // 2. 해당 약의 모든 복용 기록 찾아서 삭제
        db.collection("records")
            .whereEqualTo("medicineId", medicine.getId())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    batch.delete(doc.getReference());
                }
                
                // 일괄 실행
                batch.commit().addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "약과 관련 기록이 삭제되었습니다", Toast.LENGTH_SHORT).show();
                    loadMedicines();
                }).addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "삭제 중 오류 발생", Toast.LENGTH_SHORT).show();
                });
            });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OCR_REQUEST_CODE && resultCode == -1 && data != null) {
            String result = data.getStringExtra("ocr_result");
            if (result != null) showAddMedicineDialog(result);
        }
    }
}
