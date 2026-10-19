package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Tom & Jerry sketch on the ground strip: a mouse hole pops up at a
 * random spot, Jerry peeks out while the cat is asleep and sneaks out;
 * the cat wakes, gives chase, Jerry jinks away, then dives back into
 * the hole. The cat stares at the hole, sulks home, and dozes off.
 * Drawn as a full-width overlay anchored to the bottom of the screen.
 */
public final class ChaseView extends View {

    private static final long FRAME = 33;
    private static final int HIDDEN = 0, HOLE_IN = 1, PEEK = 2, OUT = 3,
            CHASE = 4, RUN_HOME = 5, DIVE = 6, STARE = 7,
            HOLE_OUT = 9, RETREAT = 10;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();
    private final long born = SystemClock.uptimeMillis();

    private BuddyView buddy;
    private int mode;
    private boolean enabled = true;   // "Mouse chase" setting
    private int state = HIDDEN;
    private long stateAt;
    private long nextEventAt = born + 8_000;
    private long exclaimAt = -1;
    private long wakeDelay;
    private long lastJink;
    private long lastPuffAt;
    private long wrapAt = -1;    // last portal crossing — commit to the direction

    private float holeX, holePop;
    private float mouseX, rawX, out, fleeX;   // out: 0 = inside hole, 1 = out
    private float facing = 1f, runPhase;      // rawX: unwrapped pos (portal math)
    private float fleeDir = 1f;               // hysteresis: committed flee side
    private long fleeUntil;
    private int jinks;

    private final float[] puffX = new float[10];
    private final float[] puffY = new float[10];
    private final long[] puffAt = new long[10];
    private int puffHead;

    // mouse trail — the cat chases a lagging point, so it visibly
    // runs after the mouse and overshoots when the mouse jinks
    private final float[] trail = new float[14];
    private int trailHead;

    public ChaseView(Context c) { super(c); init(); }
    public ChaseView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() { post(tick); }

    public void setBuddy(BuddyView b) { buddy = b; }
    public void setMode(int m) {
        mode = m;
        if (m != 0) shutdown();
    }
    public void setMouseEnabled(boolean on) {
        enabled = on;
        if (!on) shutdown();
    }
    private void shutdown() {          // hide the sketch, release the cat
        state = HIDDEN;
        out = 0;
        holePop = 0;
        if (buddy != null) buddy.stopChase();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            float w = getWidth(), h = getHeight();
            if (w > 0 && buddy != null && mode == 0 && enabled)
                step(SystemClock.uptimeMillis(),
                        getResources().getDisplayMetrics().density, w, h);
            invalidate();
            postDelayed(this, FRAME);
        }
    };

    private void step(long now, float d, float w, float h) {
        switch (state) {
        case HIDDEN:
            holePop = Math.max(0, holePop - 0.08f);
            out = Math.max(0, out - 0.1f);
            if (now > nextEventAt) {
                float m = 90 * d;
                holeX = m + rng.nextFloat() * (w - 2 * m);
                float catX = buddy.centerX();
                if (Math.abs(holeX - catX) < 140 * d)   // give the chase room
                    holeX = catX < w / 2
                            ? w - m - rng.nextFloat() * w * 0.3f
                            : m + rng.nextFloat() * w * 0.3f;
                holeX = Math.max(m, Math.min(w - m, holeX));
                mouseX = holeX;
                rawX = holeX;
                fleeX = holeX;
                state = HOLE_IN;
                stateAt = now;
            }
            break;
        case HOLE_IN:
            holePop = Math.min(1, (now - stateAt) / 300f);
            if (holePop >= 1) { state = PEEK; stateAt = now; }
            break;
        case PEEK:
            out = Math.min(0.5f, out + 0.045f);        // shoulders above the rim
            facing = (now / 450) % 2 == 0 ? -1 : 1;    // look left, look right
            if (now - stateAt > 1400) {
                state = buddy.isSleeping() ? OUT : RETREAT;
                stateAt = now;
                wakeDelay = 800 + rng.nextInt(900);
            }
            break;
        case RETREAT:                                  // cat was awake — duck back
            out = Math.max(0, out - 0.07f);
            if (out == 0) { state = HOLE_OUT; stateAt = now; }
            break;
        case OUT:
            out = Math.min(1, out + 0.09f);
            runPhase += 0.3f;
            if (Math.abs(fleeX - rawX) < 8 * d)
                fleeX = rawHole(w) + (rng.nextFloat() - 0.5f) * 260 * d;
            moveMouse(fleeX, 5.5f * d, d, w);
            if ((!buddy.isSleeping() && now - stateAt > 350)
                    || now - stateAt > wakeDelay)
                startChase(now);
            break;
        case CHASE:
            track();
            buddy.chase(lagTarget());
            runPhase += 0.55f;
            float catX = buddy.centerX();
            // unwrapped-space separation — stable across portal wraps
            float sep = rawX - (catX + Math.round((rawX - catX) / w) * w);
            if (Math.abs(sep) > 18 * d)
                fleeDir = Math.signum(sep);          // hysteresis: no flip-flop
            if (now - wrapAt < 800) {                // just crossed an edge —
                fleeX = rawX + facing * 400 * d;     // keep running that way
            } else if (Math.abs(sep) < 46 * d && now - lastJink > 450) {
                lastJink = now;                      // jink! dart under the cat
                jinks++;
                fleeDir = -fleeDir;
                fleeX = rawX + fleeDir * (140 + rng.nextFloat() * 130) * d;
                fleeUntil = now + 260;
                for (int i = 0; i < 3; i++)
                    spawnPuff(mouseX - fleeDir * i * 6 * d, h - 5 * d, now);
            } else if (now > fleeUntil
                    && (Math.abs(fleeX - rawX) < 14 * d
                            || (fleeX - rawX) * fleeDir < 0)) {
                fleeX = rawX + fleeDir * (150 + rng.nextFloat() * 170) * d;
                fleeUntil = now + 280 + rng.nextInt(200);
            }
            moveMouse(fleeX, 12.5f * d, d, w);
            if (now - lastPuffAt > 80) {
                lastPuffAt = now;
                spawnPuff(mouseX - facing * 10 * d, h - 4 * d, now);
            }
            if (jinks >= 4 || now - stateAt > 9000) state = RUN_HOME;
            break;
        case RUN_HOME:
            track();
            buddy.chase(lagTarget());
            runPhase += 0.62f;
            moveMouse(rawHole(w), 14f * d, d, w);
            if (now - lastPuffAt > 70) {
                lastPuffAt = now;
                spawnPuff(mouseX - facing * 10 * d, h - 4 * d, now);
            }
            if (Math.abs(rawX - rawHole(w)) < 7 * d) {
                float dir = facing;
                mouseX = holeX;
                rawX = rawHole(w);
                buddy.chase(holeX + dir * 100 * d);    // Tom skids past the hole
                state = DIVE;
                stateAt = now;
            }
            break;
        case DIVE:
            out = Math.max(0, out - 0.1f);
            if (out == 0) {
                buddy.stopChase();
                state = STARE;
                stateAt = now;
            }
            break;
        case STARE:                                    // Tom waits at the hole
            buddy.face(Math.signum(holeX - buddy.centerX()));
            if (now - stateAt > 1700) {
                buddy.doze();                          // gives up, naps on the spot
                state = HOLE_OUT;
                stateAt = now;
            }
            break;
        case HOLE_OUT:
            holePop = Math.max(0, holePop - 0.07f);
            if (holePop == 0) {
                state = HIDDEN;
                nextEventAt = now + 12000 + rng.nextInt(14000);
            }
            break;
        }
    }

    private void startChase(long now) {
        jinks = 0;
        lastJink = 0;
        fleeX = rawX;
        java.util.Arrays.fill(trail, rawX);
        exclaimAt = now;
        buddy.hop();                                   // startled awake
        buddy.face(Math.signum(mouseX - buddy.centerX()));
        state = CHASE;
        stateAt = now;
    }

    private void track() {
        trail[trailHead] = rawX;
        trailHead = (trailHead + 1) % trail.length;
    }

    /** hole position expressed in unwrapped space nearest the mouse */
    private float rawHole(float w) {
        return holeX + Math.round((rawX - holeX) / w) * w;
    }

    /** where the mouse was ~300ms ago — the cat's actual target */
    private float lagTarget() {
        return trail[(trailHead + trail.length - 9) % trail.length];
    }

    /** move toward a raw-space target; mouseX wraps around the edges */
    private void moveMouse(float rawTarget, float speed, float d, float w) {
        float dx = rawTarget - rawX;
        if (Math.abs(dx) > 0.5f) {
            float s = Math.max(-speed, Math.min(speed, dx));
            mouseX += s;
            rawX += s;
            if (Math.abs(dx) > 2 * d) facing = Math.signum(s);
        }
        float m = 26 * d;
        if (mouseX < -m) {
            mouseX += w;
            wrapAt = SystemClock.uptimeMillis();
        } else if (mouseX > w + m) {
            mouseX -= w;
            wrapAt = SystemClock.uptimeMillis();
        }
    }

    private void spawnPuff(float x, float y, long now) {
        puffX[puffHead] = x;
        puffY[puffHead] = y;
        puffAt[puffHead] = now;
        puffHead = (puffHead + 1) % puffX.length;
    }

    private static float backOut(float t) {
        float s = 1.8f;
        t -= 1;
        return t * t * ((s + 1) * t + s) + 1;
    }

    private static Path arch(float x, float baseY, float hw, float hh) {
        Path path = new Path();
        path.moveTo(x - hw / 2, baseY);
        path.lineTo(x - hw / 2, baseY - hh * 0.55f);
        path.quadTo(x - hw / 2, baseY - hh, x, baseY - hh);
        path.quadTo(x + hw / 2, baseY - hh, x + hw / 2, baseY - hh * 0.55f);
        path.lineTo(x + hw / 2, baseY);
        path.close();
        return path;
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float d = getResources().getDisplayMetrics().density;
        float h = getHeight();
        float baseY = h;

        // dust kicked up while sprinting
        for (int i = 0; i < puffX.length; i++) {
            long age = now - puffAt[i];
            if (puffAt[i] <= 0 || age > 450) continue;
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (70 * (1 - age / 450f)));
            c.drawCircle(puffX[i], puffY[i] - age * 0.03f * d,
                    (2 + age / 450f * 5) * d, p);
        }
        p.setAlpha(255);

        if (holePop > 0.01f) {
            // the burrow — dirt mound, rim, dark opening
            float hp = Math.max(0,
                    state == HOLE_IN ? backOut(holePop) : holePop);
            float hw = 56 * d * hp, hh = 42 * d * hp;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF2A2016);
            c.drawOval(new RectF(holeX - hw * 0.85f, baseY - 9 * d * hp,
                    holeX + hw * 0.85f, baseY + 4 * d), p);
            p.setColor(0xFF4A3826);
            c.drawPath(arch(holeX, baseY, hw * 1.12f, hh * 1.08f), p);
            p.setColor(0xFF150E08);
            c.drawPath(arch(holeX, baseY, hw, hh), p);
        }

        drawMouse(c, d, now, baseY, getWidth());

        // "!" — the cat snapping awake
        if (exclaimAt >= 0 && now - exclaimAt < 750 && buddy != null) {
            float t = (now - exclaimAt) / 750f;
            float cx = buddy.centerX();
            float cy = buddy.getTop() - getTop() + 20 * d;
            p.setTextSize(24 * d * backOut(Math.min(1, t * 3)));
            p.setFakeBoldText(true);
            p.setColor(0xFFFFD54A);
            p.setAlpha((int) (255 * (1 - t * t)));
            c.drawText("!", cx - 4 * d, cy - 16 * d * t, p);
            p.setAlpha(255);
            p.setFakeBoldText(false);
        }
    }

    private void drawMouse(Canvas c, float d, long now, float baseY,
            float w) {
        if (out <= 0.01f) return;
        // portal twin — while crossing an edge, draw on both sides
        float[] offs = mouseX < 0 ? new float[]{0, w}
                : mouseX > w ? new float[]{0, -w}
                : new float[]{0};
        for (float off : offs) drawMouseAt(c, d, now, baseY, mouseX + off);
    }

    private void drawMouseAt(Canvas c, float d, long now, float baseY,
            float mx) {
        float mh = 34 * d;
        // rises out of the burrow — below the view's bottom edge it's clipped
        float feetY = baseY + (1f - out) * (mh + 6 * d);
        boolean running = state == OUT || state == CHASE || state == RUN_HOME;
        float bob = running
                ? Math.abs((float) Math.sin(runPhase)) * 2.4f * d : 0;
        float t = (now - born) / 1000f;

        c.save();
        c.translate(mx, feetY - bob);
        c.scale(facing, 1f);

        // tail — whips behind
        float sway = (float) Math.sin(
                running ? runPhase * 0.9f : t * 2.2f) * 3 * d;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2 * d);
        p.setColor(0xFF9C6B42);
        Path tail = new Path();
        tail.moveTo(-10 * d, -7 * d);
        tail.quadTo(-18 * d, -5 * d, -22 * d, -11 * d + sway);
        c.drawPath(tail, p);
        p.setStyle(Paint.Style.FILL);

        // body + cream belly
        p.setColor(0xFFB0774A);
        c.drawOval(new RectF(-13 * d, -20 * d, 5 * d, -4 * d), p);
        p.setColor(0xFFE8C79B);
        c.drawOval(new RectF(-8 * d, -15 * d, 3 * d, -5 * d), p);
        // head + snout
        p.setColor(0xFFB0774A);
        c.drawCircle(5 * d, -19 * d, 7.5f * d, p);
        c.drawOval(new RectF(7 * d, -22 * d, 16 * d, -15 * d), p);
        // big round ear
        c.drawCircle(0, -26.5f * d, 5.2f * d, p);
        p.setColor(0xFFE8C79B);
        c.drawCircle(0, -26.5f * d, 3 * d, p);
        // eye + pink nose
        p.setColor(0xFF14100C);
        c.drawCircle(6.5f * d, -20.5f * d, 1.7f * d, p);
        p.setColor(0xFFFF9EBB);
        c.drawCircle(15.5f * d, -18.5f * d, 1.9f * d, p);

        // legs — alternating scurry
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f * d);
        p.setColor(0xFF9C6B42);
        float l = running ? (float) Math.sin(runPhase) * 4.5f * d : 0;
        c.drawLine(-6 * d, -4 * d, -6 * d + l, -0.5f * d, p);
        c.drawLine(-1 * d, -4 * d, -1 * d - l, -0.5f * d, p);
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
