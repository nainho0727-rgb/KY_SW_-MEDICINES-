package com.medicine.app.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.medicine.app.receivers.NotificationReceiver;

import java.util.Calendar;
import java.util.List;

/**
 * 복용시간(timeSlots)별로 매일 반복되는 약 복용 알람을 등록/해제하는 유틸.
 *
 * - timeSlots 값: "morning" / "lunch" / "evening" / "bedtime"
 * - 각 (약 + 시간대) 조합마다 고유 requestCode 를 사용해 알람이 겹치지 않게 함.
 * - 정확 알람(setExactAndAllowWhileIdle) 1회 등록 후, NotificationReceiver 에서
 *   알림을 띄울 때마다 다음 날 같은 시각으로 다시 예약 → 매일 반복.
 */
public class AlarmScheduler {

    public static final String PREFS = "medicine_prefs";

    /** 시간대별 기본 알람 시각 (사용자 설정이 없을 때 사용). */
    public static int[] defaultTimeForSlot(String slot) {
        if (slot == null) return new int[]{9, 0};
        switch (slot) {
            case "morning":  return new int[]{8, 0};
            case "lunch":    return new int[]{12, 30};
            case "evening":  return new int[]{18, 30};
            case "bedtime":  return new int[]{22, 0};
            default:         return new int[]{9, 0};
        }
    }

    /** 사용자가 설정에서 정한 시간대별 알람 시각 (없으면 기본값). */
    public static int[] timeForSlot(Context context, String slot) {
        int[] def = defaultTimeForSlot(slot);
        if (context == null || slot == null) return def;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new int[]{
                p.getInt("slot_" + slot + "_h", def[0]),
                p.getInt("slot_" + slot + "_m", def[1])
        };
    }

    /** 시간대별 알람 시각 저장. 저장 후엔 등록된 약들을 다시 스케줄해야 반영됨. */
    public static void saveSlotTime(Context context, String slot, int hour, int minute) {
        if (context == null || slot == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("slot_" + slot + "_h", hour)
                .putInt("slot_" + slot + "_m", minute)
                .apply();
    }

    public static String slotLabel(String slot) {
        if (slot == null) return "";
        switch (slot) {
            case "morning":  return "아침";
            case "lunch":    return "점심";
            case "evening":  return "저녁";
            case "bedtime":  return "취침 전";
            default:         return "";
        }
    }

    private static int slotIndex(String slot) {
        if (slot == null) return 9;
        switch (slot) {
            case "morning":  return 0;
            case "lunch":    return 1;
            case "evening":  return 2;
            case "bedtime":  return 3;
            default:         return 9;
        }
    }

    /** 약 문서 id + 시간대로 유일한 requestCode 생성 (알람/알림 id 로 공용 사용). */
    public static int requestCode(String medicineId, String slot) {
        String key = (medicineId == null ? "" : medicineId) + "_" + slot;
        return Math.abs(key.hashCode()) % 1000000;
    }

    /** 약 하나에 대해 선택된 모든 시간대 알람을 등록. */
    public static void scheduleForMedicine(Context context, String medicineId,
                                           String medicineName, List<String> timeSlots) {
        if (context == null || timeSlots == null) return;
        for (String slot : timeSlots) {
            int[] t = timeForSlot(context, slot);
            int code = requestCode(medicineId, slot);
            scheduleDaily(context, code, t[0], t[1], medicineName, slotLabel(slot));
        }
    }

    /** 약 하나에 대한 모든 시간대 알람 해제. */
    public static void cancelForMedicine(Context context, String medicineId, List<String> timeSlots) {
        if (context == null || timeSlots == null) return;
        for (String slot : timeSlots) {
            cancel(context, requestCode(medicineId, slot));
        }
    }

    /**
     * 단일 알람 등록. NotificationReceiver 에서 다음 날 재예약할 때도 이 메서드를 호출한다.
     */
    public static void scheduleDaily(Context context, int requestCode, int hour, int minute,
                                     String medicineName, String slotLabel) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra("medicineName", medicineName);
        intent.putExtra("slotLabel", slotLabel);
        intent.putExtra("notificationId", requestCode);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);

        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DATE, 1); // 이미 지난 시각이면 내일로
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        }
    }

    public static void cancel(Context context, int requestCode) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }
}
