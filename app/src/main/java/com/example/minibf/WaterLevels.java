package com.example.minibf;

import java.util.Locale;

/**
 * Pure (Android-free) water settings and maths, so they are easy to find, change and unit-test.
 *
 * >>> To change how much one gulp counts for, edit {@link #GULP_VOLUME_ML} below. <<<
 */
public final class WaterLevels {
    private WaterLevels() {}

    /** Volume of ONE gulp in millilitres. Everything in the app (meter, list, floating character) uses this. */
    public static final int GULP_VOLUME_ML = 30;

    /** The floating character's slider goes from 0 up to this many gulps. */
    public static final int MAX_GULPS_PER_PROMPT = 5;

    // Status-bar zone boundaries in millilitres.
    public static final int YELLOW_FROM_ML = 1500;   // below this  -> red
    public static final int GREEN_FROM_ML  = 2000;   // 1.5 - 2 L   -> yellow
    public static final int BLUE_FROM_ML   = 3000;   // 2 - 3 L     -> green, 3 L and above -> blue
    public static final int METER_MAX_ML   = 4000;   // the bar is full at 4 L (and stays full above it)

    public static final int ZONE_RED = 0, ZONE_YELLOW = 1, ZONE_GREEN = 2, ZONE_BLUE = 3;

    public static int zoneFor(int totalMl) {
        if (totalMl < YELLOW_FROM_ML) return ZONE_RED;
        if (totalMl < GREEN_FROM_ML) return ZONE_YELLOW;
        if (totalMl < BLUE_FROM_ML) return ZONE_GREEN;
        return ZONE_BLUE;
    }

    public static String zoneLabel(int zone) {
        switch (zone) {
            case ZONE_RED: return "Too low - drink up!";
            case ZONE_YELLOW: return "Getting there";
            case ZONE_GREEN: return "Well hydrated";
            default: return "Excellent!";
        }
    }

    /** Millilitres for an entry given in gulps or mL. */
    public static int toMl(boolean gulps, int amount) {
        return gulps ? amount * GULP_VOLUME_ML : amount;
    }

    /** 1250 -> "1.25 L", 2000 -> "2 L". */
    public static String litres(int ml) {
        String s = String.format(Locale.US, "%.2f", (ml / 10) / 100.0);   // round down so the text never disagrees with the zone colour
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + " L";
    }
}
