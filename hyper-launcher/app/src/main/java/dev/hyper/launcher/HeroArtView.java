package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Procedural Hyper OS artwork used when featured content has no image. */
public final class HeroArtView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public HeroArtView(Context c) {
        super(c);
    }

    public HeroArtView(Context c, AttributeSet a) {
        super(c, a);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float d = getResources().getDisplayMetrics().density;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, w, h,
                0xFF101010, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);

        paint.setShader(new RadialGradient(w * 0.72f, h * 0.15f,
                Math.max(w, h) * 0.85f,
                new int[]{0x18C8FF3D, 0x00000000},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        c.save();
        c.rotate(-18, w * 0.55f, h * 0.5f);

        rect.set(w * 0.28f, h * 0.14f, w * 0.70f, h * 0.78f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x22FFFFFF);
        c.drawRoundRect(rect, 12 * d, 12 * d, paint);

        rect.set(w * 0.41f, h * 0.27f, w * 0.84f, h * 0.93f);
        paint.setColor(0x18FFFFFF);
        c.drawRoundRect(rect, 12 * d, 12 * d, paint);

        rect.set(w * 0.21f, h * 0.38f, w * 0.59f, h * 0.95f);
        paint.setColor(0x12FFFFFF);
        c.drawRoundRect(rect, 12 * d, 12 * d, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f * d);
        paint.setColor(0x44FFFFFF);
        rect.set(w * 0.28f, h * 0.14f, w * 0.70f, h * 0.78f);
        c.drawRoundRect(rect, 12 * d, 12 * d, paint);
        rect.set(w * 0.41f, h * 0.27f, w * 0.84f, h * 0.93f);
        c.drawRoundRect(rect, 12 * d, 12 * d, paint);
        c.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * d);
        paint.setColor(0x33FFFFFF);
        c.drawLine(w * 0.12f, h * 0.30f, w * 0.56f, h * 0.55f, paint);
        c.drawLine(w * 0.18f, h * 0.78f, w * 0.62f, h * 0.48f, paint);
        c.drawLine(w * 0.44f, h * 0.08f, w * 0.78f, h * 0.34f, paint);

        paint.setShader(new RadialGradient(w * 0.50f, h * 0.45f,
                Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x3A000000},
                new float[]{0.55f, 1f}, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }
}
