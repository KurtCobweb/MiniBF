package com.example.minibf;

public class BobbyEvent {
    public static final String SCHEDULED = "SCHEDULED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String FINISHED = "FINISHED";
    public static final String CHANGE_OF_PLAN = "CHANGE_OF_PLAN";
    public static final String UNFINISHED = "UNFINISHED";

    public long id;
    public String activity;
    public long triggerAtMillis;
    public boolean active;
    public String status;

    public BobbyEvent(long id, String activity, long triggerAtMillis, boolean active, String status) {
        this.id = id;
        this.activity = activity;
        this.triggerAtMillis = triggerAtMillis;
        this.active = active;
        this.status = status == null ? SCHEDULED : status;
    }

    public String effectiveStatus(long now) {
        if (SCHEDULED.equals(status) && triggerAtMillis < now) return UNFINISHED;
        return status;
    }
}
