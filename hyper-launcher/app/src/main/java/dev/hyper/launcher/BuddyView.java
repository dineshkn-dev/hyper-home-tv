package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * A little blob-cat that lives on the bottom edge of the screen.
 * It breathes, blinks, trots along the edge when you steer it,
 * hops rocks, wraps around the screen, and dozes off when idle.
 */
public final class BuddyView extends View {

    private static final long IDLE_SLEEP_MS = 5_000;
    private static final long FRAME = 33;
    private static final float STEP_DP = 110f;

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
    private long blinkAt = -1;
    private boolean sleeping;

    private float lookX, lookY, tLookX, tLookY;
    private float squash, hopY;
    private float facing = -1f;

    private float tx, curX, walkPhase, lean;
    private GroundView ground;
    private float airY, vy;

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

    public void setGround(GroundView g) { ground = g; }

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

            float d = getResources().getDisplayMetrics().density;
            float parentW = getRootView().getWidth();
            float leftEdge = -getLeft() - getWidth();   // fully off left
            float rightEdge = parentW - getLeft();      // fully off right
            tx = Math.min(rightEdge + getWidth(),
                    Math.max(leftEdge - getWidth(), tx));
            float diff = tx - curX;
            if (Math.abs(diff) > 1.5f) {
                curX += diff * 0.12f;
                walkPhase += 0.42f;
                lean += ((Math.signum(diff) * 0.06f) - lean) * 0.2f;
            } else {
                curX = tx;
                lean *= 0.8f;
                walkPhase *= 0.8f;
            }
            // fully off one edge → land where the sliding twin already is:
            // fully inside the opposite edge
            if (curX < leftEdge)
                curX = tx = rightEdge - getWidth();
            else if (curX > rightEdge)
                curX = tx = leftEdge + getWidth();
            setTranslationX(curX);

            // auto-jump: a rock dead ahead while moving (grass is soft)
            if (ground != null && Math.abs(diff) > 2f && airY <= 0) {
                float catX = getLeft() + curX + getWidth() / 2f;
                float dir = Math.signum(diff);
                float[] obs = ground.obstacles();
                int[] types = ground.types();
                for (int i = 0; i < obs.length; i++) {
                    if (types[i] != 0) continue;
                    float gap = (obs[i] - catX) * dir;
                    if (gap > 0 && gap < 52 * d) {
                        vy = 13.5f * d;
                        airY = 0.1f;
                        break;
                    }
                }
            }
            if (airY > 0 || vy != 0) {
                airY += vy;
                vy -= 2.7f * d;
                if (airY <= 0) { airY = 0; vy = 0; squash = -0.5f; }
            }

            invalidate();
            postDelayed(this, FRAME);
        }
    };

    public void walk(int dir) {
        float d = getResources().getDisplayMetrics().density;
        tx += dir * STEP_DP * 0.5f * d;
        if (ground != null) ground.scroll(-dir * STEP_DP * 0.4f * d);
        facing = dir;
        tLookX = dir * 0.8f;
        wake();
    }

    public void look(float dx, float dy) { tLookX = dx; tLookY = dy; wake(); }
    public void hop() { squash = 1f; hopY = -1f; wake(); }
    public void boop() { squash = -0.9f; wake(); }
    private void wake() {
        lastAction = SystemClock.uptimeMillis();
        sleeping = false;
    }

    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float cx = w / 2f;
        float t = (SystemClock.uptimeMillis() - born) / 1000f;

        boolean walking = Math.abs(tx - curX) > 1.5f;
        float breath = sleeping
                ? (float) Math.sin(t * Math.PI * 0.55) * 0.05f
                : (float) Math.sin(t * Math.PI * 1.1) * 0.028f;
        float bob = walking ? Math.abs((float) Math.sin(walkPhase)) * 4f * d
                : 0;

        float R = 26 * d;
        float sx = 1f + breath - squash * 0.18f;
        float sy = 1f + breath + squash * 0.20f;
        float base = h;
        float cy = base - 2 * R * sy - bob + hopY * 16 * d - airY
                + R * sy;

        // draw a twin on the opposite edge while crossing — seamless wrap
        float parentW = getRootView().getWidth();
        float sl = getLeft() + curX;         // view's left edge on screen
        float[] offs = sl < 0 ? new float[]{0f, parentW}
                : sl + w > parentW ? new float[]{0f, -parentW}
                : new float[]{0f};
        for (float off : offs) {
        c.save();
        c.translate(off, 0);
        c.rotate(lean * 57.3f, cx, base);   // lean into the walk

        // little feet — alternating steps while walking
        float fw = 8 * d, fh = 5.5f * d;
        float step = walking ? (float) Math.sin(walkPhase) * 8 * d : 0;
        float spread = 12 * d;
        c.drawRoundRect(new RectF(cx - spread - fw / 2,
                base - fh - Math.max(0, step) - airY,
                cx - spread + fw / 2, base - Math.max(0, step) - airY),
                fh / 2, fh / 2, foot);
        c.drawRoundRect(new RectF(cx + spread - fw / 2,
                base - fh - Math.max(0, -step) - airY,
                cx + spread + fw / 2, base - Math.max(0, -step) - airY),
                fh / 2, fh / 2, foot);

        // tail — sways behind, trailing the direction of travel
        float sway = (float) Math.sin(
                t * Math.PI * (walking ? 4f : 1.4f)
                        + (walking ? walkPhase : 0)) * (walking ? 9 : 5) * d;
        mouth.setStrokeWidth(5 * d);
        mouth.setColor(0xFF8A7CFF);
        Path tail = new Path();
        tail.moveTo(cx - facing * R * 0.7f, base - 8 * d - airY - bob);
        tail.quadTo(cx - facing * R * 1.5f, base - 12 * d - airY - bob,
                cx - facing * R * 1.6f, base - 26 * d + sway - airY - bob);
        c.drawPath(tail, mouth);
        mouth.setColor(0xFF141A28);

        // body — soft squircle blob, cool gradient
        body.setShader(new LinearGradient(cx - R, cy - R, cx + R, cy + R,
                0xFF4FC4F7, 0xFF8A7CFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(cx - R * sx, cy - R * sy,
                cx + R * sx, cy + R * sy), R * 0.55f, R * 0.55f, body);

        // ears — little triangles on top
        body.setShader(null);
        body.setColor(0xFF6A8CF7);
        float earH = 9 * d;
        float earY = cy - R * sy;
        Path ear = new Path();
        ear.moveTo(cx - R * 0.52f, earY + 3 * d);
        ear.lineTo(cx - R * 0.30f, earY - earH);
        ear.lineTo(cx - R * 0.06f, earY + 1 * d);
        ear.close();
        c.drawPath(ear, body);
        ear.reset();
        ear.moveTo(cx + R * 0.52f, earY + 3 * d);
        ear.lineTo(cx + R * 0.30f, earY - earH);
        ear.lineTo(cx + R * 0.06f, earY + 1 * d);
        ear.close();
        c.drawPath(ear, body);
        // inner ears
        pupil.setColor(0x80FFFFFF);
        Path inner = new Path();
        inner.moveTo(cx - R * 0.44f, earY + 1 * d);
        inner.lineTo(cx - R * 0.32f, earY - earH * 0.55f);
        inner.lineTo(cx - R * 0.18f, earY + 1 * d);
        inner.close();
        c.drawPath(inner, pupil);
        inner.reset();
        inner.moveTo(cx + R * 0.44f, earY + 1 * d);
        inner.lineTo(cx + R * 0.32f, earY - earH * 0.55f);
        inner.lineTo(cx + R * 0.18f, earY + 1 * d);
        inner.close();
        c.drawPath(inner, pupil);
        pupil.setColor(0xFF141A28);

        if (sleeping) {
            // closed eyes
            mouth.setStrokeWidth(2.4f * d);
            mouth.setColor(0xFF141A28);
            float ey = cy - R * 0.18f, ex = R * 0.30f;
            c.drawArc(new RectF(cx - ex - 5 * d, ey, cx - ex + 5 * d,
                    ey + 5 * d), 15, 150, false, mouth);
            c.drawArc(new RectF(cx + ex - 5 * d, ey, cx + ex + 5 * d,
                    ey + 5 * d), 15, 150, false, mouth);
        } else {
            // eyes — cat slit pupils tracking your direction
            float blink = blinkAt >= 0
                    ? Math.abs((SystemClock.uptimeMillis() - blinkAt)
                            / 150f - 0.5f) * 2f : 1f;
            blink = 0.12f + 0.88f * Math.min(1f, blink * 1.6f);
            float ex = R * 0.32f, ey = cy - R * 0.24f, er = 6.8f * d;
            float ox = lookX * 3.6f * d, oy = lookY * 3f * d;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float ec = cx + sgn * ex;
                c.save();
                c.scale(1f, blink, ec, ey);
                c.drawCircle(ec, ey, er, eyeW);
                c.drawOval(new RectF(ec + ox - er * 0.22f,
                        ey + oy - er * 0.62f,
                        ec + ox + er * 0.22f,
                        ey + oy + er * 0.62f), pupil);
                c.restore();
            }
            // whiskers
            mouth.setStrokeWidth(1.6f * d);
            mouth.setColor(0x70FFFFFF);
            float wy = cy + R * 0.02f;
            for (int sgn = -1; sgn <= 1; sgn += 2)
                for (int k = 0; k < 3; k++) {
                    float wy2 = wy + (k - 1) * 3.4f * d;
                    c.drawLine(cx + sgn * R * 0.5f, wy2,
                            cx + sgn * (R * 0.95f + k * 2 * d),
                            wy2 + (k - 1) * 2.6f * d, mouth);
                }
            mouth.setColor(0xFF141A28);
            // nose + little cat "w" mouth
            cheek.setColor(0xFFFF8FB3);
            Path nose = new Path();
            nose.moveTo(cx - 2.6f * d, cy + R * 0.02f);
            nose.lineTo(cx + 2.6f * d, cy + R * 0.02f);
            nose.lineTo(cx, cy + R * 0.12f);
            nose.close();
            c.drawPath(nose, cheek);
            cheek.setColor(0x40FF9EC7);
            c.drawCircle(cx - R * 0.55f, cy + R * 0.08f, 4.6f * d, cheek);
            c.drawCircle(cx + R * 0.55f, cy + R * 0.08f, 4.6f * d, cheek);
            mouth.setStrokeWidth(2.4f * d);
            float mw = 5 * d + Math.abs(squash) * 4 * d;
            c.drawArc(new RectF(cx - mw * 2, cy + R * 0.10f,
                    cx, cy + R * 0.36f), 10, 160, false, mouth);
            c.drawArc(new RectF(cx, cy + R * 0.10f,
                    cx + mw * 2, cy + R * 0.36f), 10, 160, false, mouth);
        }
        c.restore();
        }   // end twin offsets

        // sleepy z's float off the head — bigger and brighter
        if (sleeping) {
            for (int i = 0; i < 2; i++) {
                float zt = ((t + i * 1.2f) % 2.4f) / 2.4f;
                pupil.setTextSize((15 + i * 5) * d);
                pupil.setColor(0xFFB9CCFF);
                pupil.setAlpha((int) (255 * (1 - zt * 0.7f)));
                c.drawText("z", cx + facing * R * 0.6f
                        + (zt * 12 + i * 10) * d,
                        cy - R * 0.9f - zt * 14 * d - airY - i * 8 * d,
                        pupil);
            }
            pupil.setColor(0xFF141A28);
            pupil.setAlpha(255);
        }
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
