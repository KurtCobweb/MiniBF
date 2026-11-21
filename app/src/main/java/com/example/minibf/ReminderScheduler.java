package com.example.minibf;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;
import java.util.List;

/** Schedules one next occurrence per enabled slot; receiver schedules its next repeat. */
public final class ReminderScheduler {
    private ReminderScheduler() {}
    static PendingIntent pending(Context c, ReminderStore.Slot s) {
        Intent i=new Intent(c, ReminderReceiver.class).putExtra("slot_id",s.id).putExtra("type",s.type);
        return PendingIntent.getBroadcast(c,s.id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void scheduleAll(Context c) {
        List<ReminderStore.Slot> slots=ReminderStore.load(c);
        for(ReminderStore.Slot s:slots) schedule(c,s);
    }
    public static void schedule(Context c, ReminderStore.Slot s) {
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent pi=pending(c,s);
        am.cancel(pi);
        if(!ReminderStore.masterEnabled(c)||!s.enabled||s.daysMask==0)return;
        long when=nextTime(s);
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
        } else {
            if(Build.VERSION.SDK_INT>=23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            else am.setExact(AlarmManager.RTC_WAKEUP,when,pi);
        }
    }
    public static void cancel(Context c, ReminderStore.Slot s) {
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null) am.cancel(pending(c,s));
    }
    public static long nextTime(ReminderStore.Slot s) {
        Calendar now=Calendar.getInstance();
        for(int offset=0;offset<8;offset++) {
            Calendar candidate=(Calendar)now.clone();
            candidate.add(Calendar.DAY_OF_YEAR,offset);
            candidate.set(Calendar.HOUR_OF_DAY,s.hour); candidate.set(Calendar.MINUTE,s.minute);
            candidate.set(Calendar.SECOND,0); candidate.set(Calendar.MILLISECOND,0);
            int bit=ReminderStore.calendarDayBit(candidate.get(Calendar.DAY_OF_WEEK));
            if((s.daysMask&bit)!=0 && candidate.getTimeInMillis()>now.getTimeInMillis()) return candidate.getTimeInMillis();
        }
        return now.getTimeInMillis()+AlarmManager.INTERVAL_DAY;
    }
}
