package com.example.minibf;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;

/** Handles the two quick actions on Bobby's reminder notification. */
public class BobbyTaskActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("event_id", -1);
        String status = intent.getStringExtra("task_status");
        if (id < 0 || status == null) return;
        if (!BobbyEvent.IN_PROGRESS.equals(status) && !BobbyEvent.CHANGE_OF_PLAN.equals(status)) return;
        BobbyDatabase db = new BobbyDatabase(context);
        db.setStatus(id, status);
        db.close();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel((int) id);
        android.widget.Toast.makeText(context, BobbyEvent.IN_PROGRESS.equals(status) ? "Marked In Progress" : "Marked Change of plan", android.widget.Toast.LENGTH_SHORT).show();
    }
}
