package com.example.minibf;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the next alarm occurrences after reboot or a wall-clock/time-zone change. */
public class RescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ReminderScheduler.scheduleAll(context);
        BobbyAlarmScheduler.scheduleAll(context);
    }
}
