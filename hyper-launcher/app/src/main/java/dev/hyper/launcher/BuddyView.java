package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * A little blob that lives on the bottom edge of the screen.
 * It breathes, blinks, walks along the edge when you steer it,
 * hops on OK, and dozes off when the room goes quiet.
 */
public final class BuddyView extends View {

    private static final long IDLE_SLEEP_MS = 25_000;
    private static final long IDLE_WANDER_MS = 9_000;
    private static final long FRAME = 33;
    private static final float STEP_DP = 110f;   // walk distance per press

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint foot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeW = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouth = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cheek = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();

    private final long born = SystemClock.uptimeMillis();
    private long lastAction = born;
    private long nextBlink = born + 1500;
    private long nextWander = born + IDLE_WANDER_MS;
    private long blinkAt = -1;
    private boolean sleeping;

    private float lookX, lookY, tLookX, tLookY;
    private float squash;
    private float hopY;

    // walking: tx = target translationX (px), curX = current
    private float tx, curX, walkPhase;
    private float lean;

    public BuddyView(Context c) { super(c); init(); }
    public BuddyView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        eyeW.setColor(0xFFF4F7FF);
        pupil.setColor(0xFF141A28);
        cheek.setColor(0x40FF9EC7);
        foot.setColor(0xFF3D7CE0);
        mouth.setColor(0xFF141A28);
        mouth.setStyle(Paint.Style.STROKE);
        mouth.setStrokeCap(Paint.Cap.ROUND);
        post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            lookX += (tLookX - lookX) * 0.22f;
            lookY += (tLookY - lookY) * 0.22f;
            squash *= 0.86f;
            if (hopY < 0) { hopY *= 0.82f; if (hopY > -0.5f) hopY = 0; }

            sleeping = now - lastAction > IDLE_SLEEP_MS;
            if (!sleeping && now >= nextBlink && blinkAt < 0) {
                blinkAt = now;
                nextBlink = now + 2200 + rng.nextInt(3200);
            }
            if (blinkAt >= 0 && now - blinkAt > 150) blinkAt = -1;

            // stroll along the edge
            float d = getResources().getDisplayMetrics().density;
            float minX = -getRootView().getWidth() + getWidth() - 20 * d;
            tx = Math.min(0, Math.max(minX, tx));
            float diff = tx - curX;
            if (Math.abs(diff) > 1.5f) {
                curX += diff * 0.12f;
                walkPhase += 0.35f;
                lean += ((Math.signum(diff) * 0.08f) - lean) * 0.2f;
            } else {
                curX = tx;
                lean *= 0.8f;
                walkPhase *= 0.8f;
            }
            setTranslationX(curX);

            // idle wander: take a little stroll now and then
            if (!sleeping && now >= nextWander) {
                nextWander = now + IDLE_WANDER_MS + rng.nextInt(9000);
                if (Math.abs(diff) < 2f)
                    tx += (rng.nextFloat() - 0.5f) * 2f * 140 * d;
            }
            invalidate();
            postDelayed(this, FRAME);
        }
    };

    /** take a few steps along the bottom edge; dir = -1 left, +1 right */
    public void walk(int dir) {
        tx += dir * STEP_DP * getResources().getDisplayMetrics().density;
        tLookX = dir * 0.8f;
        wake();
    }

    public void look(float dx, float dy) { tLookX = dx; tLookY = dy; wake(); }
    public void hop() { squash = 1f; hopY = -1f; wake(); }
    public void boop() { squash = -0.9f; wake(); }
    private void wake() {
        lastAction = SystemClock.uptimeMillis();
        nextWander = lastAction + IDLE_WANDER_MS;
        sleeping = false;
    }

    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float cx = w / 2f;
        float t = (SystemClock.uptimeMillis() - born) / 1000f;

        boolean walking = Math.abs(tx - curX) > 1.5f;
        float breath = sleeping
                ? (float) Math.sin(t * Math.PI * 0.55) * 0.045f
                : (float) Math.sin(t * Math.PI * 1.1) * 0.028f;
        float bob = walking ? Math.abs((float) Math.sin(walkPhase)) * 3.2f * d
                : 0;

        float R = 26 * d;
        float sx = 1f + breath - squash * 0.20f;
        float sy = 1f + breath + squash * 0.22f;
        // feet sit on the view's bottom edge = bottom edge of the TV
        float base = h;
        float bodyTop = base - 2 * R * sy - bob + hopY * 16 * d;
        float cy = bodyTop + R * sy;

        c.save();
        c.rotate(lean * 57.3f, cx, base);   // lean into the walk

        // little feet — alternate steps while walking
        float fw = 7.5f * d, fh = 5f * d;
        float step = walking ? (float) Math.sin(walkPhase) * 7 * d
                : Math.max(0, 4 * d * Math.abs(squash));
        float spread = 11 * d;
        c.drawRoundRect(new RectF(cx - spread - fw / 2,
                base - fh - Math.max(0, step),
                cx - spread + fw / 2, base - Math.max(0, step)),
                fh / 2, fh / 2, foot);
        c.drawRoundRect(new RectF(cx + spread - fw / 2,
                base - fh - Math.max(0, -step),
                cx + spread + fw / 2, base - Math.max(0, -step)),
                fh / 2, fh / 2, foot);

        // body — soft squircle blob, cool gradient
        body.setShader(new LinearGradient(cx - R, cy - R, cx + R, cy + R,
                0xFF4FC4F7, 0xFF8A7CFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(cx - R * sx, cy - R * sy,
                cx + R * sx, cy + R * sy), R * 0.55f, R * 0.55f, body);

        if (sleeping) {
            float ey = cy - R * 0.18f, ex = R * 0.30f;
            mouth.setStrokeWidth(2.4f * d);
            c.drawArc(new RectF(cx - ex - 5 * d, ey, cx - ex + 5 * d,
                    ey + 5 * d), 15, 150, false, mouth);
            c.drawArc(new RectF(cx + ex - 5 * d, ey, cx + ex + 5 * d,
                    ey + 5 * d), 15, 150, false, mouth);
            float zt = (t % 2.4f) / 2.4f;
            pupil.setTextSize(11 * d);
            pupil.setColor(0xFF9BB4FF);            // readable on the dark bg
            pupil.setAlpha((int) (190 * (1 - zt)));
            c.drawText("z", cx + R * 0.5f + zt * 8 * d,
                    cy - R * 0.6f - zt * 10 * d, pupil);
            pupil.setColor(0xFF141A28);
            pupil.setAlpha(255);
        } else {
            float blink = blinkAt >= 0
                    ? Math.abs((SystemClock.uptimeMillis() - blinkAt)
                            / 150f - 0.5f) * 2f : 1f;
            blink = 0.12f + 0.88f * Math.min(1f, blink * 1.6f);
            float ex = R * 0.32f, ey = cy - R * 0.22f, er = 6.4f * d;
            float ox = lookX * 3.4f * d, oy = lookY * 3f * d;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float ec = cx + sgn * ex;
                c.save();
                c.scale(1f, blink, ec, ey);
                c.drawCircle(ec, ey, er, eyeW);
                c.drawCircle(ec + ox, ey + oy, er * 0.48f, pupil);
                c.restore();
            }
            c.drawCircle(cx - R * 0.55f, cy + R * 0.05f, 4.6f * d, cheek);
            c.drawCircle(cx + R * 0.55f, cy + R * 0.05f, 4.6f * d, cheek);
            mouth.setStrokeWidth(2.6f * d);
            float mw = 8 * d + Math.abs(squash) * 6 * d;
            c.drawArc(new RectF(cx - mw, cy + R * 0.05f,
                    cx + mw, cy + R * 0.32f), 20, 140, false, mouth);
        }
        c.restore();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
