package com.example.minibf;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Segmented water status bar: red (&lt;1.5 L), yellow (1.5-2 L), green (2-3 L), blue (3-4 L+).
 * Each zone is drawn faded; the part that has been drunk is drawn in full colour.
 * Call {@link #setTotalMl(int)} to update it.
 */
public class WaterMeterView extends View {
    private static final int[] ZONE_COLORS = {0xFFD32F2F, 0xFFF9A825, 0xFF3F7D20, 0xFF2459C4};
    private static final int[] ZONE_STARTS = {0, WaterLevels.YELLOW_FROM_ML, WaterLevels.GREEN_FROM_ML, WaterLevels.BLUE_FROM_ML};
    private static final int[] TICKS = {WaterLevels.YELLOW_FROM_ML, WaterLevels.GREEN_FROM_ML, WaterLevels.BLUE_FROM_ML, WaterLevels.METER_MAX_ML};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float density;
    private int totalMl = 0;

    public WaterMeterView(Context context) { this(context, null); }

    public WaterMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(2 * density);
        outline.setColor(0xFF171717);
        text.setColor(0xFF555555);
        text.setTextSize(11 * density);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setTotalMl(int ml) {
        totalMl = Math.max(0, ml);
        setContentDescription("Water drunk today: " + WaterLevels.litres(totalMl));
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int h = (int) (54 * density);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec), resolveSize(h, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        float pad = 2 * density;
        float left = pad, right = getWidth() - pad, top = pad, bottom = top + 26 * density;
        float w = right - left;
        float perMl = w / WaterLevels.METER_MAX_ML;
        float filledX = left + Math.min(totalMl, WaterLevels.METER_MAX_ML) * perMl;

        for (int i = 0; i < 4; i++) {
            float zs = left + ZONE_STARTS[i] * perMl;
            float ze = (i == 3) ? right : left + ZONE_STARTS[i + 1] * perMl;
            fill.setColor(ZONE_COLORS[i]);
            fill.setAlpha(55);                                   // faded: not reached yet
            canvas.drawRect(zs, top, ze, bottom, fill);
            if (filledX > zs) {                                  // reached part, full colour
                fill.setAlpha(255);
                canvas.drawRect(zs, top, Math.min(filledX, ze), bottom, fill);
            }
        }
        bar.set(left, top, right, bottom);
        canvas.drawRect(bar, outline);
        for (int t : TICKS) {                                    // zone dividers + labels
            float x = left + t * perMl;
            if (t < WaterLevels.METER_MAX_ML) canvas.drawLine(x, top, x, bottom, outline);
            String label = (t == WaterLevels.METER_MAX_ML) ? "4+ L" : WaterLevels.litres(t);
            float lx = Math.min(Math.max(x, left + text.measureText(label) / 2), right - text.measureText(label) / 2);
            canvas.drawText(label, lx, bottom + 16 * density, text);
        }
    }
}
