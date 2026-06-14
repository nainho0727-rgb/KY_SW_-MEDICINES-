package com.medicine.app.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.medicine.app.R;
import com.medicine.app.adapters.RecordAdapter;
import com.medicine.app.models.Medicine;
import com.medicine.app.models.RecordItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static android.content.Context.MODE_PRIVATE;

public class HistoryFragment extends Fragment {

    private TextView tvWeekRange;
    private TextView[] tvDays = new TextView[7];
    private View[] dots = new View[7];
    private ImageButton btnPrevWeek, btnNextWeek;
    private TextView tvPerfectCount, tvPartialCount, tvMissedCount;
    private RecyclerView rvRecord;
    private LinearLayout llRecordEmpty;

    private FirebaseFirestore db;
    private String userId;
    private Calendar currentWeekStart;
    private int selectedDayIndex = -1;
    private RecordAdapter recordAdapter;
    private List<RecordItem> recordList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = requireActivity().getSharedPreferences("medicine_prefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", "");

        tvWeekRange = view.findViewById(R.id.tv_week_range);
        btnPrevWeek = view.findViewById(R.id.btn_prev_week);
        btnNextWeek = view.findViewById(R.id.btn_next_week);
        tvPerfectCount = view.findViewById(R.id.tv_perfect_count);
        tvPartialCount = view.findViewById(R.id.tv_partial_count);
        tvMissedCount = view.findViewById(R.id.tv_missed_count);
        rvRecord = view.findViewById(R.id.rv_record);
        llRecordEmpty = view.findViewById(R.id.ll_record_empty);

        int[] dayIds = {R.id.tv_day0, R.id.tv_day1, R.id.tv_day2, R.id.tv_day3, R.id.tv_day4, R.id.tv_day5, R.id.tv_day6};
        int[] dotIds = {R.id.dot0, R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4, R.id.dot5, R.id.dot6};

        for (int i = 0; i < 7; i++) {
            tvDays[i] = view.findViewById(dayIds[i]);
            dots[i] = view.findViewById(dotIds[i]);
            final int idx = i;
            tvDays[i].setOnClickListener(v -> selectDay(idx));
        }

        recordAdapter = new RecordAdapter(recordList);
        rvRecord.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecord.setAdapter(recordAdapter);

        currentWeekStart = Calendar.getInstance();
        currentWeekStart.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        currentWeekStart.set(Calendar.HOUR_OF_DAY, 0);
        currentWeekStart.set(Calendar.MINUTE, 0);
        currentWeekStart.set(Calendar.SECOND, 0);

        btnPrevWeek.setOnClickListener(v -> {
            currentWeekStart.add(Calendar.WEEK_OF_YEAR, -1);
            updateCalendar();
        });

        btnNextWeek.setOnClickListener(v -> {
            currentWeekStart.add(Calendar.WEEK_OF_YEAR, 1);
            updateCalendar();
        });

        updateCalendar();
        Calendar today = Calendar.getInstance();
        selectDay(today.get(Calendar.DAY_OF_WEEK) - 1);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCalendar();
        if (selectedDayIndex != -1) selectDay(selectedDayIndex);
    }

    private void updateCalendar() {
        Calendar weekEnd = (Calendar) currentWeekStart.clone();
        weekEnd.add(Calendar.DAY_OF_YEAR, 6);

        SimpleDateFormat fmt = new SimpleDateFormat("M월 d일", Locale.KOREAN);
        tvWeekRange.setText(fmt.format(currentWeekStart.getTime()) + " ~ " + fmt.format(weekEnd.getTime()));

        for (int i = 0; i < 7; i++) {
            Calendar day = (Calendar) currentWeekStart.clone();
            day.add(Calendar.DAY_OF_YEAR, i);
            tvDays[i].setText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)));
        }

        calculateWeekStats();
    }

    private void selectDay(int index) {
        selectedDayIndex = index;
        for (int i = 0; i < 7; i++) {
            tvDays[i].setBackground(null);
            tvDays[i].setTextColor(getResources().getColor(R.color.text_primary, null));
        }
        tvDays[index].setBackground(getResources().getDrawable(R.drawable.bg_date_selected, null));
        tvDays[index].setTextColor(getResources().getColor(R.color.white, null));

        Calendar selectedDay = (Calendar) currentWeekStart.clone();
        selectedDay.add(Calendar.DAY_OF_YEAR, index);
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDay.getTime());
        loadDayRecord(dateStr);
    }

    private void loadDayRecord(String dateStr) {
        if (userId.isEmpty()) return;

        db.collection("records")
            .whereEqualTo("userId", userId)
            .whereEqualTo("date", dateStr)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                recordList.clear();
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    String medicineName = doc.getString("medicineName");
                    String timeSlot = doc.getString("timeSlot");
                    String status = doc.getString("status");

                    String timeLabel = "";
                    if ("morning".equals(timeSlot)) timeLabel = "아침";
                    else if ("lunch".equals(timeSlot)) timeLabel = "점심";
                    else if ("evening".equals(timeSlot)) timeLabel = "저녁";
                    else if ("bedtime".equals(timeSlot)) timeLabel = "취침 전";

                    recordList.add(new RecordItem(
                        medicineName != null ? medicineName : "",
                        timeLabel,
                        "taken".equals(status) ? "복용 완료" : "미복용"
                    ));
                }
                recordAdapter.updateList(new ArrayList<>(recordList));
                llRecordEmpty.setVisibility(recordList.isEmpty() ? View.VISIBLE : View.GONE);
                rvRecord.setVisibility(recordList.isEmpty() ? View.GONE : View.VISIBLE);
            });
    }

    private void calculateWeekStats() {
        if (userId.isEmpty()) return;

        // 1. 등록된 약 정보 가져오기
        db.collection("medicines")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(medicineSnapshots -> {
                int dailyTotalTarget = 0;
                for (QueryDocumentSnapshot doc : medicineSnapshots) {
                    List<String> slots = (List<String>) doc.get("timeSlots");
                    if (slots != null) dailyTotalTarget += slots.size();
                }

                final int finalTarget = dailyTotalTarget;

                // 등록된 약이 하나도 없으면 챙길 것도 없으므로 통계는 모두 0.
                if (finalTarget == 0) {
                    tvPerfectCount.setText("0일");
                    tvPartialCount.setText("0일");
                    tvMissedCount.setText("0일");
                    for (int i = 0; i < 7; i++) dots[i].setVisibility(View.INVISIBLE);
                    return;
                }

                // 2. 이번 주 범위 계산
                Calendar weekEnd = (Calendar) currentWeekStart.clone();
                weekEnd.add(Calendar.DAY_OF_YEAR, 6);
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                final String startStr = fmt.format(currentWeekStart.getTime());
                final String endStr = fmt.format(weekEnd.getTime());
                final String todayStr = fmt.format(Calendar.getInstance().getTime());

                // userId 로만 조회(복합 색인 불필요) → 날짜 필터는 코드에서 처리
                db.collection("records")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener(recordSnapshots -> {
                        Map<String, Integer> dailyTakenCount = new HashMap<>();
                        String firstRecordDate = null; // 사용자가 복약을 처음 시작한 날(통계 시작 기준)
                        for (QueryDocumentSnapshot doc : recordSnapshots) {
                            String date = doc.getString("date");
                            if (date == null) continue;
                            if (firstRecordDate == null || date.compareTo(firstRecordDate) < 0) {
                                firstRecordDate = date;
                            }
                            // 이번 주(startStr ~ endStr) 범위만 집계
                            if (date.compareTo(startStr) >= 0 && date.compareTo(endStr) <= 0) {
                                dailyTakenCount.put(date, dailyTakenCount.getOrDefault(date, 0) + 1);
                            }
                        }
                        final String startBoundary = firstRecordDate;

                        int perfectDays = 0;
                        int partialDays = 0;
                        int missedDays = 0;

                        for (int i = 0; i < 7; i++) {
                            Calendar day = (Calendar) currentWeekStart.clone();
                            day.add(Calendar.DAY_OF_YEAR, i);
                            String dayStr = fmt.format(day.getTime());

                            int taken = dailyTakenCount.getOrDefault(dayStr, 0);

                            // 점 표시 (하나라도 먹었으면 표시)
                            dots[i].setVisibility(taken > 0 ? View.VISIBLE : View.INVISIBLE);

                            // 아직 오지 않은 미래 날짜는 제외
                            if (dayStr.compareTo(todayStr) > 0) continue;
                            // 복약을 처음 시작하기 전 날짜는 제외(기록이 아예 없던 기간은 미복용으로 안 셈)
                            if (startBoundary == null || dayStr.compareTo(startBoundary) < 0) continue;

                            if (finalTarget > 0 && taken >= finalTarget) perfectDays++;
                            else if (taken > 0) partialDays++;
                            // 오늘은 아직 진행 중이라 미복용으로 세지 않음(지난 날만 미복용 집계)
                            else if (!dayStr.equals(todayStr)) missedDays++;
                        }

                        tvPerfectCount.setText(perfectDays + "일");
                        tvPartialCount.setText(partialDays + "일");
                        tvMissedCount.setText(missedDays + "일");
                    })
                    .addOnFailureListener(e ->
                            Log.e("HistoryFragment", "주간 통계 기록 조회 실패", e));
            })
            .addOnFailureListener(e ->
                    Log.e("HistoryFragment", "주간 통계 약 조회 실패", e));
    }
}
