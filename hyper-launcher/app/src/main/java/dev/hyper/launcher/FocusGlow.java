package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Soft accent halo for the three top-bar action icons. */
final class FocusGlow {

    private static final long MS = 170;

    private FocusGlow() {}

    static void apply(View v, boolean focused, float scale) {
        if (v == null) return;
        v.setBackground(focused ? new Halo(v.getContext()) : null);
        v.animate().cancel();
        v.animate()
                .scaleX(focused ? scale : 1f)
                .scaleY(focused ? scale : 1f)
                .setDuration(MS)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /** Draws a feathered ring that always fits inside the icon's bounds. */
    private static final class Halo extends Drawable {
        private static final int[] COLORS = {
                0x00C8FF3D, 0x00C8FF3D, 0x40C8FF3D, 0x14C8FF3D, 0x00C8FF3D
        };
        private static final float[] STOPS = {0f, 0.48f, 0.9f, 0.97f, 1f};

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float inset;
        private Shader shader;

        Halo(Context ctx) {
            inset = 3 * ctx.getResources().getDisplayMetrics().density;
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            float radius = Math.min(b.width(), b.height()) / 2f - inset;
            if (radius <= 0) return;
            if (shader == null) {
                shader = new RadialGradient(cx, cy, radius, COLORS, STOPS,
                        Shader.TileMode.CLAMP);
                paint.setShader(shader);
            }
            canvas.drawCircle(cx, cy, radius, paint);
        }

        @Override protected void onBoundsChange(Rect bounds) {
            shader = null;
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
