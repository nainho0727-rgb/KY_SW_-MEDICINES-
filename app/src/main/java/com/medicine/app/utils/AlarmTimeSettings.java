package com.medicine.app.utils;

import android.app.TimePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.medicine.app.R;
import com.medicine.app.models.Medicine;

import java.util.Locale;

/**
 * 시간대별(아침/점심/저녁/취침) 복용 알림 시각을 설정하는 다이얼로그.
 * 홈 화면과 내 약 화면에서 동일하게 호출해 사용한다.
 *
 * 저장 시: SharedPreferences 에 시각 저장({@link AlarmScheduler#saveSlotTime}) →
 * 등록된 모든 약 알림을 새 시각으로 재예약.
 */
public class AlarmTimeSettings {

    public static void show(Context context, FirebaseFirestore db, String userId) {
        if (context == null) return;

        View dv = LayoutInflater.from(context).inflate(R.layout.dialog_slot_times, null);
        TextView tvM = dv.findViewById(R.id.tv_time_morning);
        TextView tvL = dv.findViewById(R.id.tv_time_lunch);
        TextView tvE = dv.findViewById(R.id.tv_time_evening);
        TextView tvB = dv.findViewById(R.id.tv_time_bedtime);
        Button btnCancel = dv.findViewById(R.id.btn_cancel);
        Button btnSave = dv.findViewById(R.id.btn_save);

        // 현재 저장된(없으면 기본) 시각으로 초기화. 배열로 들고 다니며 피커에서 갱신.
        final int[] mT = AlarmScheduler.timeForSlot(context, "morning");
        final int[] lT = AlarmScheduler.timeForSlot(context, "lunch");
        final int[] eT = AlarmScheduler.timeForSlot(context, "evening");
        final int[] bT = AlarmScheduler.timeForSlot(context, "bedtime");
        setupPicker(context, tvM, mT);
        setupPicker(context, tvL, lT);
        setupPicker(context, tvE, eT);
        setupPicker(context, tvB, bT);

        AlertDialog dialog = new AlertDialog.Builder(context).setView(dv).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            AlarmScheduler.saveSlotTime(context, "morning", mT[0], mT[1]);
            AlarmScheduler.saveSlotTime(context, "lunch", lT[0], lT[1]);
            AlarmScheduler.saveSlotTime(context, "evening", eT[0], eT[1]);
            AlarmScheduler.saveSlotTime(context, "bedtime", bT[0], bT[1]);
            rescheduleAll(context, db, userId);
            Toast.makeText(context, "알림 시간을 저장하고 모든 약 알림을 갱신했어요.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private static void setupPicker(Context context, TextView tv, int[] time) {
        tv.setText(fmt(time));
        tv.setOnClickListener(v -> new TimePickerDialog(context, (view, h, m) -> {
            time[0] = h;
            time[1] = m;
            tv.setText(fmt(time));
        }, time[0], time[1], true).show());
    }

    private static String fmt(int[] t) {
        return String.format(Locale.KOREAN, "%02d:%02d", t[0], t[1]);
    }

    /** 설정한 시각이 반영되도록 등록된 모든 약의 알림을 다시 예약. */
    private static void rescheduleAll(Context context, FirebaseFirestore db, String userId) {
        if (db == null || userId == null || userId.isEmpty()) return;
        final Context app = context.getApplicationContext();
        db.collection("medicines")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(snap -> {
                for (QueryDocumentSnapshot doc : snap) {
                    Medicine m = doc.toObject(Medicine.class);
                    AlarmScheduler.scheduleForMedicine(app, doc.getId(), m.getName(), m.getTimeSlots());
                }
            });
    }
}
