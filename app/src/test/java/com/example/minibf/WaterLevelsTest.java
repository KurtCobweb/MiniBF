package com.example.minibf;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class WaterLevelsTest {
    @Test public void zonesMatchSpec() {
        assertEquals(WaterLevels.ZONE_RED, WaterLevels.zoneFor(0));
        assertEquals(WaterLevels.ZONE_RED, WaterLevels.zoneFor(1499));
        assertEquals(WaterLevels.ZONE_YELLOW, WaterLevels.zoneFor(1500));
        assertEquals(WaterLevels.ZONE_YELLOW, WaterLevels.zoneFor(1999));
        assertEquals(WaterLevels.ZONE_GREEN, WaterLevels.zoneFor(2000));
        assertEquals(WaterLevels.ZONE_GREEN, WaterLevels.zoneFor(2999));
        assertEquals(WaterLevels.ZONE_BLUE, WaterLevels.zoneFor(3000));
        assertEquals(WaterLevels.ZONE_BLUE, WaterLevels.zoneFor(4500));
    }

    @Test public void gulpsAndMlConvert() {
        assertEquals(3 * WaterLevels.GULP_VOLUME_ML, WaterLevels.toMl(true, 3));
        assertEquals(250, WaterLevels.toMl(false, 250));
    }

    @Test public void litresFormatting() {
        assertEquals("0 L", WaterLevels.litres(0));
        assertEquals("1.25 L", WaterLevels.litres(1250));
        assertEquals("2 L", WaterLevels.litres(2000));
        assertEquals("1.5 L", WaterLevels.litres(1500));
    }
}
