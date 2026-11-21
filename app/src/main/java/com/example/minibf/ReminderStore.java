package com.example.minibf;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Persistent reminder model. Times are stored as separate 24-hour hour/minute fields. */
public final class ReminderStore {
    public static final String STUDY = "study";
    public static final String WATER = "water";
    private static final String PREFS = "pixel_companion_settings";
    private static final String KEY_SLOTS = "slots_json";
    private static final String KEY_MASTER = "master_enabled";
    private static final String KEY_NEXT_ID = "next_slot_id";
    private static final String KEY_FLOATING = "floating_characters_enabled";
    private ReminderStore() {}

    public static class Slot {
        public int id, hour, minute, daysMask;
        public String type;
        public boolean enabled;
        public Slot(int id, String type, int hour, int minute, boolean enabled, int daysMask) {
            this.id=id; this.type=type; this.hour=hour; this.minute=minute;
            this.enabled=enabled; this.daysMask=daysMask;
        }
        public String timeText() { return String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute); }
    }

    public static boolean floatingEnabled(Context c) { return prefs(c).getBoolean(KEY_FLOATING, false); }
    public static void setFloatingEnabled(Context c, boolean enabled) { prefs(c).edit().putBoolean(KEY_FLOATING, enabled).apply(); }

    public static boolean masterEnabled(Context c) { return prefs(c).getBoolean(KEY_MASTER, true); }
    public static void setMasterEnabled(Context c, boolean enabled) { prefs(c).edit().putBoolean(KEY_MASTER, enabled).apply(); }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static List<Slot> load(Context c) {
        ArrayList<Slot> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY_SLOTS, null);
        if (raw == null) return defaults(c);
        try {
            JSONArray a = new JSONArray(raw);
            for (int i=0;i<a.length();i++) {
                JSONObject o=a.getJSONObject(i);
                out.add(new Slot(o.getInt("id"), o.getString("type"), o.getInt("hour"),
                        o.getInt("minute"), o.optBoolean("enabled", true), o.optInt("days", 127)));
            }
        } catch (Exception ignored) { return defaults(c); }
        return out;
    }

    public static void save(Context c, List<Slot> slots) {
        JSONArray a=new JSONArray(); int max=0;
        for (Slot s:slots) try {
            JSONObject o=new JSONObject(); o.put("id",s.id); o.put("type",s.type);
            o.put("hour",s.hour); o.put("minute",s.minute); o.put("enabled",s.enabled); o.put("days",s.daysMask);
            a.put(o); max=Math.max(max,s.id);
        } catch (Exception ignored) {}
        prefs(c).edit().putString(KEY_SLOTS,a.toString()).putInt(KEY_NEXT_ID,max+1).apply();
    }

    public static int newId(Context c, List<Slot> slots) {
        int id=prefs(c).getInt(KEY_NEXT_ID,1);
        for (Slot s:slots) id=Math.max(id,s.id+1);
        prefs(c).edit().putInt(KEY_NEXT_ID,id+1).apply();
        return id;
    }

    private static List<Slot> defaults(Context c) {
        ArrayList<Slot> result=new ArrayList<>();
        // Defaults are read from res/xml/default_schedule.xml on first launch.
        try {
            org.xmlpull.v1.XmlPullParser p=c.getResources().getXml(R.xml.default_schedule);
            String group=""; int id=1;
            while(p.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if(p.getEventType()==org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if("study".equals(p.getName()) || "water".equals(p.getName())) group=p.getName();
                    if("slot".equals(p.getName()) && !group.isEmpty()) {
                        int h=Integer.parseInt(p.getAttributeValue(null,"hour"));
                        int m=Integer.parseInt(p.getAttributeValue(null,"minute"));
                        String days=p.getAttributeValue(null,"days");
                        result.add(new Slot(id++,group,h,m,true,parseDays(days)));
                    }
                }
            }
        } catch(Exception ignored) {}
        if(result.isEmpty()) {
            result.add(new Slot(1,STUDY,8,0,true,127));
            result.add(new Slot(2,STUDY,10,0,true,127));
            result.add(new Slot(3,STUDY,16,0,true,127));
            result.add(new Slot(4,WATER,9,0,true,127));
            result.add(new Slot(5,WATER,11,0,true,127));
            result.add(new Slot(6,WATER,13,0,true,127));
            result.add(new Slot(7,WATER,15,0,true,127));
            result.add(new Slot(8,WATER,17,0,true,127));
        }
        save(c,result);
        return result;
    }
    private static int parseDays(String value) {
        if(value==null || value.trim().isEmpty()) return 127;
        int mask=0;
        for(String d:value.split(",")) {
            switch(d.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "SUN": mask|=1; break; case "MON": mask|=2; break; case "TUE": mask|=4; break;
                case "WED": mask|=8; break; case "THU": mask|=16; break; case "FRI": mask|=32; break;
                case "SAT": mask|=64; break;
            }
        }
        return mask==0?127:mask;
    }
    public static String daysText(int mask) {
        if(mask==127) return "Every day";
        String[] names={"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
        StringBuilder b=new StringBuilder();
        for(int i=0;i<7;i++) if((mask&(1<<i))!=0) { if(b.length()>0)b.append(", "); b.append(names[i]); }
        return b.length()==0?"No days selected":b.toString();
    }
    public static int calendarDayBit(int calendarDay) { return 1 << (calendarDay-Calendar.SUNDAY); }
}
