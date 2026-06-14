package com.medicine.app.fragments;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.cardview.widget.CardView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.medicine.app.BuildConfig;
import com.medicine.app.R;
import com.medicine.app.activities.OcrActivity;
import com.medicine.app.adapters.MedicineAdapter;
import com.medicine.app.models.Medicine;
import com.medicine.app.utils.AlarmScheduler;
import com.medicine.app.utils.AlarmTimeSettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static android.content.Context.MODE_PRIVATE;

public class MyMedicineFragment extends Fragment {

    private static final int CAMERA_PERMISSION_CODE = 1001;
    private static final int NOTI_PERMISSION_CODE = 1002;
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

    private Button btnCheckInteraction;
    /** 상호작용 검사용 모델 우선순위(429 시 다음 모델로 전환). */
    private static final String[] AI_MODELS = {
            "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest"
    };
    private final Executor aiExecutor = Executors.newSingleThreadExecutor();
    private AlertDialog aiProgress;

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

        btnCheckInteraction = view.findViewById(R.id.btn_check_interaction);
        btnCheckInteraction.setOnClickListener(v -> checkInteractions());

        view.findViewById(R.id.btn_slot_times)
                .setOnClickListener(v -> AlarmTimeSettings.show(getContext(), db, userId));

        ensureNotificationPermission(); // 알람 알림이 실제로 표시되도록 권한 확보(Android 13+)
        loadMedicines();
    }

    /**
     * Android 13(TIRAMISU)+ 에서는 POST_NOTIFICATIONS 런타임 권한이 없으면
     * AlarmScheduler 가 알람을 걸어도 알림이 표시되지 않는다.
     * 약을 등록하는 화면(OCR/수동 등록 공통 경로)에 진입할 때 한 번 요청해 둔다.
     */
    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTI_PERMISSION_CODE);
            }
        }
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
        } else if (requestCode == NOTI_PERMISSION_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(),
                        "알림 권한이 없으면 복용시간 알람이 표시되지 않습니다. 설정에서 허용해주세요.",
                        Toast.LENGTH_LONG).show();
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
            // 등록과 동시에 복용시간(timeSlots)별 알람 자동 설정
            if (getContext() != null) {
                AlarmScheduler.scheduleForMedicine(getContext(), ref.getId(), name, timeSlots);
            }
            Toast.makeText(getContext(), "약이 추가되었습니다", Toast.LENGTH_SHORT).show();
            loadMedicines();
        });
    }

    /**
     * OcrActivity 가 돌려준 약 목록 JSON 을 파싱해 확인 다이얼로그를 띄운다.
     * 사용자가 확인하면 모든 약을 Firestore 에 등록하고 알람까지 자동 설정.
     */
    private void showOcrConfirmDialog(String medicinesJson, String warnings) {
        if (getContext() == null) return;
        final JSONArray meds;
        try {
            meds = new JSONArray(medicinesJson);
        } catch (Exception e) {
            Toast.makeText(getContext(), "스캔 결과를 읽지 못했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (meds.length() == 0) {
            Toast.makeText(getContext(), "인식된 약이 없습니다. 다시 촬영해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder("아래 약들을 등록하고 복용시간 알람을 설정할까요?\n\n");
        for (int i = 0; i < meds.length(); i++) {
            JSONObject m = meds.optJSONObject(i);
            if (m == null) continue;
            sb.append("• ").append(m.optString("name", "(이름 미상)"));
            String cat = m.optString("category", "").trim();
            if (!cat.isEmpty()) sb.append(" (").append(cat).append(")");
            List<String> slots = parseSlots(m.optJSONArray("timeSlots"));
            sb.append("  [").append(joinSlotLabels(slots)).append("]\n");
        }

        new AlertDialog.Builder(getContext())
                .setTitle("스캔 결과 확인")
                .setMessage(sb.toString().trim())
                .setPositiveButton("모두 등록", (d, w) -> registerParsedMedicines(meds, warnings))
                .setNegativeButton("취소", null)
                .show();
    }

    private void registerParsedMedicines(JSONArray meds, String warnings) {
        int count = 0;
        for (int i = 0; i < meds.length(); i++) {
            JSONObject m = meds.optJSONObject(i);
            if (m == null) continue;
            String name = m.optString("name", "").trim();
            if (TextUtils.isEmpty(name)) continue;

            String dose = m.optString("dose", "").trim();
            if (dose.isEmpty()) dose = "1정";

            List<String> slots = parseSlots(m.optJSONArray("timeSlots"));
            if (slots.isEmpty()) slots.add("morning"); // 시간 정보 없으면 기본 아침

            // 분류 + 복용법 + 주의사항을 메모로 합쳐 저장
            String category = m.optString("category", "").trim();
            String freq = m.optString("frequency", "").trim();
            String cautions = m.optString("cautions", "").trim();
            StringBuilder memo = new StringBuilder();
            // 1줄: 분류 · 복용법 (예: "위산과다증약 · 하루 2회")
            if (!category.isEmpty()) memo.append(category);
            if (!freq.isEmpty()) {
                if (memo.length() > 0) memo.append(" · ");
                memo.append(freq);
            }
            if (!cautions.isEmpty()) {
                if (memo.length() > 0) memo.append("\n");
                memo.append("주의: ").append(cautions);
            }
            // 문서 전체 공통 주의사항도 각 약 메모에 덧붙임
            if (warnings != null && !warnings.trim().isEmpty()) {
                if (memo.length() > 0) memo.append("\n");
                memo.append("공통 주의: ").append(warnings.trim());
            }

            saveMedicine(name, dose, slots, memo.toString());
            count++;
        }
        if (getContext() != null) {
            Toast.makeText(getContext(),
                    count + "개의 약을 등록하고 알람을 설정했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private List<String> parseSlots(JSONArray arr) {
        List<String> slots = new ArrayList<>();
        if (arr == null) return slots;
        for (int i = 0; i < arr.length(); i++) {
            String v = arr.optString(i, "").trim();
            if (v.equals("morning") || v.equals("lunch")
                    || v.equals("evening") || v.equals("bedtime")) {
                if (!slots.contains(v)) slots.add(v);
            }
        }
        return slots;
    }

    private String joinSlotLabels(List<String> slots) {
        if (slots.isEmpty()) return "시간 미지정";
        StringBuilder sb = new StringBuilder();
        for (String s : slots) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(AlarmScheduler.slotLabel(s));
        }
        return sb.toString();
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
        // 0. 이 약의 복용시간 알람 모두 해제
        if (getContext() != null) {
            AlarmScheduler.cancelForMedicine(getContext(), medicine.getId(), medicine.getTimeSlots());
        }
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

    // ───────────────────────── 약 상호작용/중복 검사 ─────────────────────────

    /** 등록된 약들을 모아 Gemini 로 중복·병용금기·상호작용을 검사한다. */
    private void checkInteractions() {
        if (getContext() == null) return;
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(getContext(), "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("medicines")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(snap -> {
                LinkedHashMap<String, String> meds = new LinkedHashMap<>();
                for (QueryDocumentSnapshot doc : snap) {
                    Medicine m = doc.toObject(Medicine.class);
                    String name = m.getName() == null ? "" : m.getName().trim();
                    if (name.isEmpty() || meds.containsKey(name)) continue;
                    meds.put(name, m.getMemo() == null ? "" : m.getMemo().trim());
                }
                if (meds.size() < 2) {
                    Toast.makeText(getContext(),
                            "상호작용 검사는 약이 2개 이상 등록되어 있어야 가능해요.", Toast.LENGTH_LONG).show();
                    return;
                }
                showAiProgress();
                runInteractionAi(meds, 0);
            })
            .addOnFailureListener(e ->
                Toast.makeText(getContext(), "약 목록을 불러오지 못했어요.", Toast.LENGTH_SHORT).show());
    }

    private void showAiProgress() {
        if (getContext() == null) return;
        aiProgress = new AlertDialog.Builder(getContext())
                .setMessage("AI가 약 상호작용을 분석 중입니다...")
                .setCancelable(false)
                .create();
        aiProgress.show();
    }

    private void dismissAiProgress() {
        if (aiProgress != null && aiProgress.isShowing()) aiProgress.dismiss();
        aiProgress = null;
    }

    private void runInteractionAi(LinkedHashMap<String, String> meds, int modelIndex) {
        StringBuilder list = new StringBuilder();
        int i = 1;
        for (Map.Entry<String, String> e : meds.entrySet()) {
            list.append(i++).append(". ").append(e.getKey());
            if (!e.getValue().isEmpty()) list.append(" (메모: ").append(e.getValue()).append(")");
            list.append("\n");
        }
        String prompt =
            "너는 한국 약사 보조 AI야. 아래는 사용자가 현재 복용 중인 약 목록이야.\n\n" +
            list + "\n" +
            "이 약들을 함께 복용할 때 (1) 같은 성분 중복, (2) 병용금기, (3) 상호작용 주의가 있는지 분석해서 " +
            "JSON 객체 하나로만 답해(설명·마크다운·코드블록 없이).\n" +
            "형식:\n{\n" +
            "  \"overall\": \"safe|caution|danger\",\n" +
            "  \"summary\": \"전체 한 줄 요약\",\n" +
            "  \"findings\": [\n" +
            "    {\"type\": \"중복성분|병용금기|상호작용주의\", \"medicines\": [\"약A\",\"약B\"], " +
            "\"ingredient\": \"근거가 된 공통 성분(중복일 때) 또는 빈 문자열\", " +
            "\"severity\": \"높음|중간|낮음\", \"reason\": \"이유\", \"advice\": \"권고\"}\n" +
            "  ]\n}\n" +
            "매우 중요 — 환각(잘못된 성분 추측) 금지:\n" +
            "- 약 이름만으로 주성분을 100% 확신할 수 없으면 그 약이 들어간 finding 을 절대 만들지 마.\n" +
            "- '중복성분'은 두 약의 실제 주성분을 모두 확실히 아는 경우에만, 근거 성분명을 ingredient 와 reason 에 반드시 명시해.\n" +
            "- 이름이 비슷하다고 같은 성분이라 단정하지 마. 예: '코푸/코푸정' 계열은 진해거담제(디히드로코데인 등)로 아세트아미노펜이 아님.\n" +
            "위험이 없거나 성분이 불확실하면 findings 를 빈 배열로 하고 overall 을 \"safe\" 로 해.";

        GenerativeModelFutures model = GenerativeModelFutures.from(new GenerativeModel(
                AI_MODELS[Math.min(modelIndex, AI_MODELS.length - 1)], BuildConfig.GEMINI_API_KEY));
        Content content = new Content.Builder().addText(prompt).build();

        Futures.addCallback(model.generateContent(content), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                final String raw = result.getText();
                runOnUiSafe(() -> { dismissAiProgress(); showInteractionResult(raw); });
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e("MyMedicine", "interaction AI failed (model=" + modelIndex + ")", t);
                if (modelIndex + 1 < AI_MODELS.length) {
                    runOnUiSafe(() -> runInteractionAi(meds, modelIndex + 1)); // 다음 모델로 전환
                } else {
                    runOnUiSafe(() -> {
                        dismissAiProgress();
                        if (getContext() != null) {
                            Toast.makeText(getContext(),
                                    "분석에 실패했어요. 잠시 후 다시 시도해주세요.", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, aiExecutor);
    }

    private void runOnUiSafe(Runnable r) {
        if (!isAdded()) return;
        androidx.fragment.app.FragmentActivity act = getActivity();
        if (act != null) act.runOnUiThread(r);
    }

    private void showInteractionResult(String raw) {
        if (getContext() == null) return;
        View dv = LayoutInflater.from(getContext()).inflate(R.layout.dialog_interaction_result, null);
        TextView tvTitle = dv.findViewById(R.id.tv_result_title);
        TextView tvSummary = dv.findViewById(R.id.tv_result_summary);
        LinearLayout llFindings = dv.findViewById(R.id.ll_findings);

        try {
            JSONObject root = new JSONObject(extractJsonObject(raw));
            String overall = root.optString("overall", "caution");
            String summary = root.optString("summary", "").trim();
            JSONArray findings = root.optJSONArray("findings");

            if ("safe".equalsIgnoreCase(overall) || findings == null || findings.length() == 0) {
                tvTitle.setText("✅ 특별한 위험 없음");
                tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.success));
                tvSummary.setText(summary.isEmpty()
                        ? "함께 복용해도 특별한 상호작용·중복은 발견되지 않았어요." : summary);
                tvSummary.setVisibility(View.VISIBLE);
            } else {
                boolean danger = "danger".equalsIgnoreCase(overall);
                tvTitle.setText(danger ? "🚨 주의가 필요한 조합이 있어요" : "⚠ 확인이 필요한 항목이 있어요");
                tvTitle.setTextColor(ContextCompat.getColor(getContext(),
                        danger ? R.color.error : R.color.warning));
                if (summary.isEmpty()) {
                    tvSummary.setVisibility(View.GONE);
                } else {
                    tvSummary.setText(summary);
                    tvSummary.setVisibility(View.VISIBLE);
                }
                for (int k = 0; k < findings.length(); k++) {
                    JSONObject f = findings.optJSONObject(k);
                    if (f != null) addFindingCard(llFindings, f);
                }
            }
        } catch (Exception e) {
            tvTitle.setText("검사 결과");
            tvSummary.setText(raw == null ? "결과를 받지 못했어요." : raw.trim());
            tvSummary.setVisibility(View.VISIBLE);
        }

        new AlertDialog.Builder(getContext())
                .setView(dv)
                .setPositiveButton("확인", null)
                .show();
    }

    /** finding 하나를 카드 형태로 ll_findings 에 추가. 위험도에 따라 색/아이콘 구분. */
    private void addFindingCard(LinearLayout parent, JSONObject f) {
        if (getContext() == null) return;
        View row = LayoutInflater.from(getContext())
                .inflate(R.layout.item_interaction_finding, parent, false);
        TextView head = row.findViewById(R.id.tv_finding_head);
        TextView meds = row.findViewById(R.id.tv_finding_meds);
        TextView ing = row.findViewById(R.id.tv_finding_ingredient);
        TextView reason = row.findViewById(R.id.tv_finding_reason);
        TextView advice = row.findViewById(R.id.tv_finding_advice);

        String type = f.optString("type", "주의");
        String severity = f.optString("severity", "").trim();
        String dot;
        int colorRes;
        if (severity.contains("높")) { dot = "🔴"; colorRes = R.color.error; }
        else if (severity.contains("중")) { dot = "🟠"; colorRes = R.color.warning; }
        else { dot = "🟡"; colorRes = R.color.text_secondary; }
        head.setText(dot + "  [" + (severity.isEmpty() ? "주의" : severity) + "] " + type);
        head.setTextColor(ContextCompat.getColor(getContext(), colorRes));

        JSONArray ms = f.optJSONArray("medicines");
        StringBuilder names = new StringBuilder();
        if (ms != null) {
            for (int j = 0; j < ms.length(); j++) {
                if (j > 0) names.append("  +  ");
                names.append(ms.optString(j, ""));
            }
        }
        if (names.length() > 0) meds.setText(names.toString());
        else meds.setVisibility(View.GONE);

        String ingredient = f.optString("ingredient", "").trim();
        if (!ingredient.isEmpty()) ing.setText("성분: " + ingredient);
        else ing.setVisibility(View.GONE);

        String r = f.optString("reason", "").trim();
        if (!r.isEmpty()) reason.setText(r);
        else reason.setVisibility(View.GONE);

        String a = f.optString("advice", "").trim();
        if (!a.isEmpty()) advice.setText("→ " + a);
        else advice.setVisibility(View.GONE);

        parent.addView(row);
    }

    /** Gemini 응답에서 순수 JSON 객체 부분만 추출. */
    private String extractJsonObject(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl != -1) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) return s.substring(start, end + 1);
        return s;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OCR_REQUEST_CODE && resultCode == -1 && data != null) {
            String json = data.getStringExtra("medicines_json");
            String warnings = data.getStringExtra("warnings_text");
            if (json != null && !json.isEmpty()) {
                showOcrConfirmDialog(json, warnings);
            } else {
                // 구버전 호환: 단일 텍스트로 넘어온 경우
                String result = data.getStringExtra("ocr_result");
                if (result != null) showAddMedicineDialog(result);
            }
        }
    }
}
