package com.example.minibf;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BobbyDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "bobby_schedule.db";
    private static final int DB_VERSION = 2;

    public BobbyDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, activity TEXT NOT NULL, trigger_at INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1, status TEXT NOT NULL DEFAULT 'SCHEDULED')");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE events ADD COLUMN status TEXT NOT NULL DEFAULT 'SCHEDULED'");
    }

    public long addEvent(String activity, long triggerAt) {
        ContentValues v = new ContentValues();
        v.put("activity", activity); v.put("trigger_at", triggerAt); v.put("active", 1); v.put("status", BobbyEvent.SCHEDULED);
        return getWritableDatabase().insert("events", null, v);
    }

    private BobbyEvent fromCursor(Cursor c) {
        return new BobbyEvent(c.getLong(c.getColumnIndexOrThrow("_id")), c.getString(c.getColumnIndexOrThrow("activity")),
                c.getLong(c.getColumnIndexOrThrow("trigger_at")), c.getInt(c.getColumnIndexOrThrow("active")) == 1,
                c.getString(c.getColumnIndexOrThrow("status")));
    }

    public List<BobbyEvent> getAll() {
        ArrayList<BobbyEvent> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("events", null, "active=1", null, null, null, "trigger_at ASC")) {
            while (c.moveToNext()) out.add(fromCursor(c));
        } return out;
    }

    public List<BobbyEvent> getToday() {
        Calendar start = Calendar.getInstance(); start.set(Calendar.HOUR_OF_DAY,0); start.set(Calendar.MINUTE,0); start.set(Calendar.SECOND,0); start.set(Calendar.MILLISECOND,0);
        long from = start.getTimeInMillis(); start.add(Calendar.DAY_OF_MONTH,1); long to = start.getTimeInMillis();
        ArrayList<BobbyEvent> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("events", null, "trigger_at>=? AND trigger_at<?", new String[]{String.valueOf(from),String.valueOf(to)}, null, null, "trigger_at ASC")) {
            while (c.moveToNext()) out.add(fromCursor(c));
        } return out;
    }

    public BobbyEvent get(long id) {
        try (Cursor c = getReadableDatabase().query("events", null, "_id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (c.moveToFirst()) return fromCursor(c);
        } return null;
    }

    public void updateEvent(long id, String activity, long triggerAt) {
        ContentValues v = new ContentValues();
        v.put("activity", activity);
        v.put("trigger_at", triggerAt);
        v.put("status", BobbyEvent.SCHEDULED);
        getWritableDatabase().update("events", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public void setStatus(long id, String status) {
        ContentValues v = new ContentValues(); v.put("status", status);
        getWritableDatabase().update("events", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) { getWritableDatabase().delete("events", "_id=?", new String[]{String.valueOf(id)}); }
}
