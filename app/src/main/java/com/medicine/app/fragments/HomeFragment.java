package com.medicine.app.fragments;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.medicine.app.R;
import com.medicine.app.adapters.HomeMedicineAdapter;
import com.medicine.app.models.Medicine;
import com.medicine.app.receivers.NotificationReceiver;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

public class HomeFragment extends Fragment {

    private static final int NOTI_PERMISSION_CODE = 200;

    private TextView tvGreeting, tvDate, tvProgressText, tvProgressPercent, tvMedicineCount;
    private TextView tvNextMedicineName, tvNextMedicineTime;
    private ProgressBar progressBar;
    private Button btnTakeNext;
    private RecyclerView rvTodayMedicines;
    private LinearLayout llEmpty;
    private ImageButton btnNotification;

    private HomeMedicineAdapter adapter;
    private List<Medicine> medicineList = new ArrayList<>();
    private FirebaseFirestore db;
    private String userId;
    private String todayDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = requireActivity().getSharedPreferences("medicine_prefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", "");

        tvGreeting = view.findViewById(R.id.tv_greeting);
        String nickname = prefs.getString("nickname", "사용자");
        tvDate = view.findViewById(R.id.tv_date);
        tvProgressText = view.findViewById(R.id.tv_progress_text);
        tvProgressPercent = view.findViewById(R.id.tv_progress_percent);
        tvMedicineCount = view.findViewById(R.id.tv_medicine_count);
        tvNextMedicineName = view.findViewById(R.id.tv_next_medicine_name);
        tvNextMedicineTime = view.findViewById(R.id.tv_next_medicine_time);
        progressBar = view.findViewById(R.id.progress_bar);
        btnTakeNext = view.findViewById(R.id.btn_take_next);
        rvTodayMedicines = view.findViewById(R.id.rv_today_medicines);
        llEmpty = view.findViewById(R.id.ll_empty);
        btnNotification = view.findViewById(R.id.btn_notification);

        // Set greeting based on time
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            tvGreeting.setText("좋은 아침이에요, " + nickname + "님! 🌅");
        } else if (hour < 18) {
            tvGreeting.setText("좋은 오후예요, " + nickname + "님! ☀️");
        } else {
            tvGreeting.setText("좋은 저녁이에요, " + nickname + "님! 🌙");
        }

        // Set today's date
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String displayDate = new SimpleDateFormat("M월 d일", Locale.KOREAN).format(new Date());
        tvDate.setText(displayDate);

        // Setup RecyclerView
        adapter = new HomeMedicineAdapter(medicineList, (medicine, position) -> takeMedicine(medicine, position));
        rvTodayMedicines.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTodayMedicines.setAdapter(adapter);

        btnNotification.setOnClickListener(v -> showNotificationSettings());

        loadTodayMedicines();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTodayMedicines(); // 화면 돌아올 때 데이터 최신화
    }

    private void showNotificationSettings() {
        if (getContext() == null) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_notification, null);
        bottomSheetDialog.setContentView(view);

        SwitchCompat switchNoti = view.findViewById(R.id.switch_notification);
        TimePicker timePicker = view.findViewById(R.id.time_picker);
        Button btnSave = view.findViewById(R.id.btn_save_notification);

        SharedPreferences prefs = requireActivity().getSharedPreferences("medicine_prefs", MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean("noti_enabled", false);
        int savedHour = prefs.getInt("noti_hour", 8);
        int savedMinute = prefs.getInt("noti_minute", 0);

        switchNoti.setChecked(isEnabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.setHour(savedHour);
            timePicker.setMinute(savedMinute);
        } else {
            timePicker.setCurrentHour(savedHour);
            timePicker.setCurrentMinute(savedMinute);
        }

        btnSave.setOnClickListener(v -> {
            boolean enabled = switchNoti.isChecked();
            int hour, minute;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hour = timePicker.getHour();
                minute = timePicker.getMinute();
            } else {
                hour = timePicker.getCurrentHour();
                minute = timePicker.getCurrentMinute();
            }

            // Android 13+ Notification Permission Check
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTI_PERMISSION_CODE);
                    bottomSheetDialog.dismiss();
                    return;
                }
            }

            // Android 12+ Exact Alarm Permission Check
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    startActivity(intent);
                    bottomSheetDialog.dismiss();
                    return;
                }
            }

            prefs.edit()
                .putBoolean("noti_enabled", enabled)
                .putInt("noti_hour", hour)
                .putInt("noti_minute", minute)
                .apply();

            if (enabled) {
                scheduleNotification(hour, minute);
                Toast.makeText(getContext(), String.format(Locale.KOREAN, "매일 %02d:%02d에 알림이 설정되었습니다.", hour, minute), Toast.LENGTH_SHORT).show();
            } else {
                cancelNotification();
                Toast.makeText(getContext(), "알림이 해제되었습니다.", Toast.LENGTH_SHORT).show();
            }

            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void scheduleNotification(int hour, int minute) {
        Context context = getContext();
        if (context == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DATE, 1);
        }

        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                }
            } catch (SecurityException e) {
                // Handle cases where exact alarm permission is not granted
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            }
        }
    }

    private void cancelNotification() {
        Context context = getContext();
        if (context == null) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == NOTI_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(), "알림 권한이 허용되었습니다. 다시 설정을 완료해주세요.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "알림 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadTodayMedicines() {
        if (userId == null || userId.isEmpty()) return;

        db.collection("medicines")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                medicineList.clear();

                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    Medicine baseMedicine = doc.toObject(Medicine.class);
                    baseMedicine.setId(doc.getId());

                    List<String> timeSlots = baseMedicine.getTimeSlots();
                    if (timeSlots != null && !timeSlots.isEmpty()) {
                        for (String slot : timeSlots) {
                            Medicine medicineForSlot = new Medicine(baseMedicine);
                            medicineForSlot.setTimeSlots(Collections.singletonList(slot)); // Create a medicine instance for each slot
                            checkTakenStatus(medicineForSlot);
                            medicineList.add(medicineForSlot);
                        }
                    } else {
                        // If no time slots are specified, default to morning
                        Medicine medicineForSlot = new Medicine(baseMedicine);
                        medicineForSlot.setTimeSlots(Collections.singletonList("morning"));
                        checkTakenStatus(medicineForSlot);
                        medicineList.add(medicineForSlot);
                    }
                }

                updateUI();
            })
            .addOnFailureListener(e -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "약 목록 로드 실패", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void checkTakenStatus(Medicine medicine) {
        db.collection("records")
            .whereEqualTo("userId", userId)
            .whereEqualTo("medicineId", medicine.getId())
            .whereEqualTo("date", todayDate)
            .get()
            .addOnSuccessListener(snap -> {
                if (!snap.isEmpty()) {
                    medicine.setTaken(true);
                }
                updateUI();
            });
    }

    private void updateUI() {
        if (getContext() == null || getView() == null) return;

        int total = medicineList.size();
        int taken = 0;
        for (Medicine m : medicineList) {
            if (m.isTaken()) taken++;
        }

        tvProgressText.setText(taken + "/" + total + "회 완료");
        int percent = total > 0 ? (taken * 100 / total) : 0;
        tvProgressPercent.setText(percent + "%");
        progressBar.setProgress(percent);
        tvMedicineCount.setText(total + "개");

        adapter.updateList(new ArrayList<>(medicineList));

        // Update next schedule
        Medicine nextMedicine = null;
        for (Medicine m : medicineList) {
            if (!m.isTaken()) {
                nextMedicine = m;
                break;
            }
        }

        View cardNextSchedule = getView().findViewById(R.id.card_next_schedule);
        if (nextMedicine != null && cardNextSchedule != null) {
            cardNextSchedule.setVisibility(View.VISIBLE);
            tvNextMedicineName.setText(nextMedicine.getName());
            tvNextMedicineTime.setText(nextMedicine.getTimeSlotLabel());
            final Medicine finalNext = nextMedicine;
            btnTakeNext.setOnClickListener(v -> takeMedicine(finalNext, medicineList.indexOf(finalNext)));
        } else if (cardNextSchedule != null) {
            cardNextSchedule.setVisibility(View.GONE);
        }

        if (total == 0) {
            llEmpty.setVisibility(View.VISIBLE);
            rvTodayMedicines.setVisibility(View.GONE);
        } else {
            llEmpty.setVisibility(View.GONE);
            rvTodayMedicines.setVisibility(View.VISIBLE);
        }
    }

    private void takeMedicine(Medicine medicine, int position) {
        if (medicine.isTaken()) return;

        Map<String, Object> record = new HashMap<>();
        record.put("userId", userId);
        record.put("medicineId", medicine.getId());
        record.put("medicineName", medicine.getName());
        // Assuming medicineForSlot in loadTodayMedicines has only one time slot
        record.put("timeSlot", medicine.getTimeSlots().get(0));
        record.put("date", todayDate);
        record.put("takenAt", System.currentTimeMillis());
        record.put("status", "taken");

        db.collection("records")
            .add(record)
            .addOnSuccessListener(ref -> {
                medicine.setTaken(true);
                if (position >= 0 && position < medicineList.size()) {
                    adapter.notifyItemChanged(position);
                }
                updateUI();
                if (getContext() != null) {
                    Toast.makeText(getContext(), "💊 " + medicine.getName() + " 복용 완료!", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }
}
