package com.example.minibf;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Persistent log of water drunk. Each entry is a "slot" added either in gulps or in mL.
 * Only today's entries count towards the meter; entries older than {@link #KEEP_DAYS} are pruned.
 * Every call reads/writes SharedPreferences directly, so the app and the floating-character
 * service can both use it without holding stale copies.
 *
 * The gulp size lives in {@link WaterLevels#GULP_VOLUME_ML}.
 */
public final class WaterStore {
    public static final String UNIT_GULPS = "gulps";
    public static final String UNIT_ML = "ml";
    private static final String PREFS = "pixel_companion_water";
    private static final String KEY_ENTRIES = "entries_json";
    private static final int KEEP_DAYS = 14;
    private WaterStore() {}

    public static class Entry {
        public long id;
        public String day;      // yyyy-MM-dd
        public long timeMillis;
        public String unit;     // UNIT_GULPS or UNIT_ML
        public int amount;      // number of gulps, or mL

        public Entry(long id, String day, long timeMillis, String unit, int amount) {
            this.id = id; this.day = day; this.timeMillis = timeMillis; this.unit = unit; this.amount = amount;
        }
        public boolean isGulps() { return UNIT_GULPS.equals(unit); }
        public int volumeMl() { return WaterLevels.toMl(isGulps(), amount); }
        public String timeText() { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timeMillis)); }
        public String describe() {
            if (isGulps()) return amount + (amount == 1 ? " gulp" : " gulps") + " (" + volumeMl() + " mL)";
            return amount + " mL";
        }
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static String dayOf(long millis) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis)); }

    // ---------------------------------------------------------------- queries

    public static List<Entry> today(Context c) {
        String today = dayOf(System.currentTimeMillis());
        ArrayList<Entry> out = new ArrayList<>();
        for (Entry e : loadAll(c)) if (today.equals(e.day)) out.add(e);
        return out;
    }

    public static int totalMl(List<Entry> entries) {
        int sum = 0;
        for (Entry e : entries) sum += e.volumeMl();
        return sum;
    }

    // ---------------------------------------------------------------- changes

    public static void add(Context c, String unit, int amount) {
        if (amount <= 0) return;
        List<Entry> all = loadAll(c);
        long now = System.currentTimeMillis();
        long id = now;
        for (Entry e : all) if (e.id >= id) id = e.id + 1;   // keep ids unique
        all.add(new Entry(id, dayOf(now), now, unit, amount));
        saveAll(c, all);
    }

    public static void addGulps(Context c, int gulps) { add(c, UNIT_GULPS, gulps); }

    public static void update(Context c, long id, String unit, int amount) {
        if (amount <= 0) return;
        List<Entry> all = loadAll(c);
        for (Entry e : all) if (e.id == id) { e.unit = unit; e.amount = amount; }
        saveAll(c, all);
    }

    public static void delete(Context c, long id) {
        List<Entry> all = loadAll(c);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id == id) all.remove(i);
        saveAll(c, all);
    }

    // ---------------------------------------------------------------- storage

    private static List<Entry> loadAll(Context c) {
        ArrayList<Entry> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY_ENTRIES, null);
        if (raw == null) return out;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Entry(o.getLong("id"), o.getString("day"), o.getLong("time"),
                        o.getString("unit"), o.getInt("amount")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static void saveAll(Context c, List<Entry> all) {
        long cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000;
        JSONArray a = new JSONArray();
        for (Entry e : all) {
            if (e.timeMillis < cutoff) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("id", e.id); o.put("day", e.day); o.put("time", e.timeMillis);
                o.put("unit", e.unit); o.put("amount", e.amount);
                a.put(o);
            } catch (Exception ignored) { }
        }
        prefs(c).edit().putString(KEY_ENTRIES, a.toString()).apply();
    }
}
