package com.medicine.app.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.medicine.app.R;
import com.medicine.app.activities.SplashActivity;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "medicine_reminder_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "약 복용 알림",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("약 복용 시간을 알려주는 알림입니다.");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        Intent splashIntent = new Intent(context, SplashActivity.class);
        splashIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            splashIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("💊 약 복용 시간입니다!")
            .setContentText("잊지 말고 약을 복용해주세요. 건강이 최우선입니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }
}
