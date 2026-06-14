package com.medicine.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.medicine.app.utils.AlarmScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * 재부팅 시 AlarmManager 에 등록돼 있던 알람은 모두 사라진다.
 * 이 리시버는 BOOT_COMPLETED 를 받아 Firestore 에 저장된 해당 사용자의 약을 다시 읽어
 * 복용시간(timeSlots)별 알람을 전부 재등록한다.
 *
 * 알람은 (약 id + slot) 기반의 동일한 requestCode 로 다시 걸리므로 중복되지 않는다.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("medicine_prefs", Context.MODE_PRIVATE);
        final String userId = prefs.getString("user_id", "");
        if (userId == null || userId.isEmpty()) return;

        // Firestore 조회는 비동기 → 리시버가 바로 끝나면 프로세스가 종료될 수 있으므로
        // goAsync() 로 결과가 올 때까지 살려 둔다(최대 약 10초).
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();

        FirebaseFirestore.getInstance()
                .collection("medicines")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (QueryDocumentSnapshot doc : task.getResult()) {
                                String name = doc.getString("name");
                                Object slotsObj = doc.get("timeSlots");
                                if (slotsObj instanceof List) {
                                    List<String> slots = new ArrayList<>();
                                    for (Object o : (List<?>) slotsObj) {
                                        if (o != null) slots.add(String.valueOf(o));
                                    }
                                    if (!slots.isEmpty()) {
                                        AlarmScheduler.scheduleForMedicine(
                                                appContext, doc.getId(), name, slots);
                                    }
                                }
                            }
                        }
                    } finally {
                        pendingResult.finish();
                    }
                });
    }
}
