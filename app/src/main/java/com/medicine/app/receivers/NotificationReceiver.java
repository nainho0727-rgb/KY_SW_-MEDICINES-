package com.medicine.app.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.medicine.app.R;
import com.medicine.app.activities.SplashActivity;
import com.medicine.app.utils.AlarmScheduler;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "medicine_reminder_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "약 복용 알림", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("약 복용 시간을 알려주는 알림입니다.");
            notificationManager.createNotificationChannel(channel);
        }

        // 알람에 담겨 온 약 정보
        String medicineName = intent.getStringExtra("medicineName");
        String slotLabel = intent.getStringExtra("slotLabel");
        int notificationId = intent.getIntExtra("notificationId", 1001);
        int hour = intent.getIntExtra("hour", -1);
        int minute = intent.getIntExtra("minute", -1);

        // 알림 문구 구성
        String title = "💊 약 복용 시간입니다!";
        String text;
        if (!TextUtils.isEmpty(medicineName)) {
            String when = TextUtils.isEmpty(slotLabel) ? "" : "(" + slotLabel + ") ";
            text = when + medicineName + " 복용할 시간이에요. 잊지 말고 챙겨주세요!";
        } else {
            text = "잊지 말고 약을 복용해주세요. 건강이 최우선입니다.";
        }

        Intent splashIntent = new Intent(context, SplashActivity.class);
        splashIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notificationId, splashIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pill)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }

        // 매일 반복: 같은 시각으로 다음 날 알람을 다시 예약
        if (hour >= 0 && minute >= 0) {
            AlarmScheduler.scheduleDaily(context, notificationId, hour, minute, medicineName, slotLabel);
        }
    }
}
