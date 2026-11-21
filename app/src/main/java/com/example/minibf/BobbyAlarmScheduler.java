package com.example.minibf;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.List;

public final class BobbyAlarmScheduler {
    private BobbyAlarmScheduler() {}
    private static PendingIntent pending(Context c, long id) {
        Intent i = new Intent(c, BobbyAlarmReceiver.class).putExtra("event_id", id);
        return PendingIntent.getBroadcast(c, (int)id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static void schedule(Context c, BobbyEvent e) {
        if (e.triggerAtMillis <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c,e.id);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,e.triggerAtMillis,pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,e.triggerAtMillis,pi);
        } else am.setExact(AlarmManager.RTC_WAKEUP,e.triggerAtMillis,pi);
    }
    public static void cancel(Context c,long id) {
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null) am.cancel(pending(c,id));
    }
    public static void scheduleAll(Context c) {
        BobbyDatabase db=new BobbyDatabase(c);
        for(BobbyEvent e:db.getAll()) if (BobbyEvent.SCHEDULED.equals(e.status)) schedule(c,e);
        db.close();
    }
}
