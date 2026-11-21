package com.example.minibf;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class BobbyAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "bobby_schedule_alerts";

    @Override public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("event_id", -1);
        if (id < 0) return;
        BobbyDatabase db = new BobbyDatabase(context);
        BobbyEvent event = db.get(id);
        db.close();
        if (event == null || !BobbyEvent.SCHEDULED.equals(event.status)) return;

        createChannel(context);
        Intent open = new Intent(context, BobbyChatActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, (int) id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent doingIt = actionPendingIntent(context, id, BobbyEvent.IN_PROGRESS, 100000 + (int) id);
        PendingIntent changePlan = actionPendingIntent(context, id, BobbyEvent.CHANGE_OF_PLAN, 200000 + (int) id);
        Bitmap bobbyFace = BitmapFactory.decodeResource(context.getResources(), R.drawable.bobby_avatar);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(bobbyFace)
                .setContentTitle("Bobby")
                .setContentText("It's time to " + event.activity)
                .setStyle(new NotificationCompat.BigTextStyle().bigText("It's time to " + event.activity))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, "Doing it", doingIt)
                .addAction(0, "Change of plan", changePlan);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) id, builder.build());

        // Reuse the existing optional floating-character feature. The regular
        // notification and its actions work even when overlay permission is off.
        if (ReminderStore.floatingEnabled(context)
                && android.provider.Settings.canDrawOverlays(context)) {
            Intent floating = new Intent(context, FloatingReminderService.class)
                    .putExtra("custom_message", event.activity);
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(floating);
                else context.startService(floating);
            } catch (Exception ignored) { }
        }
    }

    private PendingIntent actionPendingIntent(Context context, long id, String status, int requestCode) {
        Intent action = new Intent(context, BobbyTaskActionReceiver.class)
                .putExtra("event_id", id)
                .putExtra("task_status", status);
        return PendingIntent.getBroadcast(context, requestCode, action,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Bobby schedule reminders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Reminders and task actions created in Bobby chat");
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
