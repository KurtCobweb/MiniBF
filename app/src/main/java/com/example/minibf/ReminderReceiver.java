package com.example.minibf;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.List;

/** Displays the matching pixel character and schedules that slot's next occurrence. */
public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID="pixel_companion_reminders";
    @Override public void onReceive(Context context, Intent intent) {
        int id=intent.getIntExtra("slot_id",-1);
        String type=intent.getStringExtra("type");
        if(id<0||type==null||!ReminderStore.masterEnabled(context))return;
        ReminderStore.Slot match=null;
        List<ReminderStore.Slot> all=ReminderStore.load(context);
        for(ReminderStore.Slot s:all)if(s.id==id&&s.type.equals(type)){match=s;break;}
        if(match==null||!match.enabled)return;
        if(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
            showNotification(context,match);

        // Show the character over the current app and speak the reminder when the
        // user has enabled floating characters and granted overlay access.
        if (ReminderStore.floatingEnabled(context) && android.provider.Settings.canDrawOverlays(context)) {
            Intent floating = new Intent(context, FloatingReminderService.class)
                    .putExtra("type", match.type).putExtra("slot_id", match.id);
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(floating);
                else context.startService(floating);
            } catch (Exception ignored) {
                // Keep the normal notification as a fallback if Android blocks the overlay service.
            }
        }
        ReminderScheduler.schedule(context,match);
    }
    private void showNotification(Context c,ReminderStore.Slot slot) {
        boolean study=ReminderStore.STUDY.equals(slot.type);
        int image=study?R.drawable.study_character :R.drawable.water_character;
        String title=study?"Study time!":"Time to drink water!";
        String message=study?"Your study companion is waiting. Start a focused session.":"Your water companion says: take a drink of water.";
        Bitmap bitmap=BitmapFactory.decodeResource(c.getResources(),image);
        Intent open=new Intent(c,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(c,slot.id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new NotificationCompat.Builder(c,CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(message)
                .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bitmap).bigLargeIcon((Bitmap)null))
                .setLargeIcon(bitmap).setContentIntent(content).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH).build();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(slot.id,n);
    }
}
