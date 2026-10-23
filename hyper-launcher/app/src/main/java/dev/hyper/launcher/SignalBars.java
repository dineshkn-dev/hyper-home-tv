package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Four-bar Wi-Fi signal indicator used by network rows. */
public final class SignalBars extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int level;

    public SignalBars(Context c) {
        super(c);
    }

    public SignalBars(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setLevel(int level) {
        this.level = Math.max(0, Math.min(4, level));
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float barW = 2.4f * d;
        float gap = 2.2f * d;
        float baseline = getHeight() - 1 * d;
        float maxH = getHeight() - 2 * d;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(barW);
        for (int i = 0; i < 4; i++) {
            float h = maxH * (0.30f + i * 0.22f);
            paint.setColor(i < level ? 0xFFF5F5F5 : 0xFF2A2A2A);
            float x = 1.5f * d + i * (barW + gap);
            c.drawLine(x, baseline, x, baseline - h, paint);
        }
    }
}
