package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * The ground strip the cat lives on — wind-blown grass tufts and
 * irregular rocks it has to hop. Obstacle positions are in root-view
 * coordinates so BuddyView can read them.
 */
public final class GroundView extends View {

    private static final int ROCK = 0, GRASS = 1;
    private static final long FRAME = 40;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();   // fresh scatter each launch

    private float[] obsX = new float[0];
    private int[] obsType = new int[0];
    private float[] obsScale = new float[0];
    private float[] obsPhase = new float[0];
    private float[][] rockPts = new float[0][];   // precomputed jagged outlines
    private final long born = SystemClock.uptimeMillis();

    public GroundView(Context c) { super(c); init(); }
    public GroundView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() { post(tick); }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (Math.abs(scrollPx) > 0.4f) {
                float mv = scrollPx * 0.07f;        // slow eased slide
                scrollPx -= mv;
                int w = getWidth();
                for (int i = 0; i < obsX.length; i++) {
                    obsX[i] += mv;
                    if (obsX[i] > w + 70) respawn(i, -60);
                    else if (obsX[i] < -70) respawn(i, w + 60);
                }
            }
            invalidate();
            postDelayed(this, FRAME);
        }
    };

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        int n = 8;
        obsX = new float[n];
        obsType = new int[n];
        obsScale = new float[n];
        obsPhase = new float[n];
        rockPts = new float[n][];
        float margin = 120;   // px-ish min separation between neighbours
        for (int i = 0; i < n; i++) {
            float x;
            int tries = 0;
            do {
                x = 60 + rng.nextFloat() * (w - 120);
                tries++;
            } while (tries < 40 && tooClose(x, i, margin));
            obsX[i] = x;
            obsType[i] = rng.nextFloat() < 0.5f ? ROCK : GRASS;
            obsScale[i] = 0.75f + rng.nextFloat() * 0.7f;
            obsPhase[i] = rng.nextFloat() * 6.28f;
            if (obsType[i] == ROCK) {
                // irregular 8-point outline around the rock
                rockPts[i] = new float[16];
                for (int k = 0; k < 8; k++) {
                    double ang = Math.PI * 2 * k / 8.0;
                    float r = 0.62f + rng.nextFloat() * 0.55f;
                    rockPts[i][k * 2] = (float) Math.cos(ang) * r;
                    rockPts[i][k * 2 + 1] = (float) Math.sin(ang) * r;
                }
            }
        }
    }

    private boolean tooClose(float x, int filled, float min) {
        for (int i = 0; i < filled; i++)
            if (Math.abs(obsX[i] - x) < min) return true;
        return false;
    }

    private float scrollPx;   // remaining px the ground should slide

    /** slide the world horizontally; obstacles wrap around the edges */
    public void scroll(float px) { scrollPx += px; }

    private void respawn(int i, float x) {
        obsX[i] = x;
        obsType[i] = rng.nextFloat() < 0.5f ? ROCK : GRASS;
        obsScale[i] = 0.75f + rng.nextFloat() * 0.7f;
        obsPhase[i] = rng.nextFloat() * 6.28f;
        if (obsType[i] == ROCK) {
            rockPts[i] = new float[16];
            for (int k = 0; k < 8; k++) {
                double ang = Math.PI * 2 * k / 8.0;
                float r = 0.62f + rng.nextFloat() * 0.55f;
                rockPts[i][k * 2] = (float) Math.cos(ang) * r;
                rockPts[i][k * 2 + 1] = (float) Math.sin(ang) * r;
            }
        }
    }

    public float[] obstacles() { return obsX; }
    public int[] types() { return obsType; }   // ROCK = 0, GRASS = 1

    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float base = getHeight();
        float t = (SystemClock.uptimeMillis() - born) / 1000f;

        for (int i = 0; i < obsX.length; i++) {
            float x = obsX[i];
            float s = obsScale[i];
            if (obsType[i] == ROCK) drawRock(c, x, base, s, d, i);
            else drawGrass(c, x, base, s, d, t, obsPhase[i]);
        }
    }

    /** jagged boulder with a lit top-left facet */
    private void drawRock(Canvas c, float x, float base, float s,
            float d, int i) {
        float w = 14 * d * s, h = 11 * d * s;
        float cy = base - h * 0.72f;
        Path p = new Path();
        float[] pts = rockPts[i];
        for (int k = 0; k < 8; k++) {
            float px = x + pts[k * 2] * w;
            float py = cy + Math.max(0, pts[k * 2 + 1]) * h * 1.15f
                    + pts[k * 2 + 1] * h;   // squashed: flatter bottom
            if (k == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.close();
        paint.setColor(0xFF333C4E);
        c.drawPath(p, paint);

        // lit facet — upper-left sliver
        Path facet = new Path();
        facet.moveTo(x - w * 0.55f, cy - h * 0.45f);
        facet.lineTo(x + pts[0] * w, cy + pts[1] * h);
        facet.lineTo(x + pts[2] * w, cy + pts[3] * h);
        facet.lineTo(x - w * 0.1f, cy + h * 0.25f);
        facet.close();
        paint.setColor(0xFF47536B);
        c.drawPath(facet, paint);
    }

    /** a dense tuft of tapered leaf-shaped blades bending in the breeze */
    private void drawGrass(Canvas c, float x, float base, float s,
            float d, float t, float phase) {
        paint.setStyle(Paint.Style.FILL);
        int blades = 11;
        for (int k = 0; k < blades; k++) {
            float f = k / (blades - 1f) - 0.5f;          // -0.5 .. 0.5
            float bx = x + f * 34 * d * s;
            // outer blades shorter, fanned out
            float bh = (26 - Math.abs(f) * 14
                    + ((k * 31) % 5) * 1.4f) * d * s;
            float bw = (3.4f + ((k * 17) % 3) * 0.9f) * d * s;
            // wind: sway grows toward the tip, outer blades lean more
            float gust = (float) Math.sin(t * 1.6 + phase + f * 2.4f) * 6.5f
                    + (float) Math.sin(t * 3.3 + phase + f) * 2.2f;
            float sway = (gust + f * 7f) * d;
            float tipX = bx + sway, tipY = base - bh;
            float midX = bx + sway * 0.4f, midY = base - bh * 0.55f;

            // two greens, deeper in the middle of the tuft
            paint.setColor(Math.abs(f) < 0.2f ? 0xFF2E6B4C
                    : (k % 2 == 0 ? 0xFF4FA875 : 0xFF3E8C60));

            Path leaf = new Path();
            leaf.moveTo(bx - bw / 2, base);
            leaf.quadTo(midX - bw * 0.28f, midY, tipX, tipY);
            leaf.quadTo(midX + bw * 0.28f, midY, bx + bw / 2, base);
            leaf.close();
            c.drawPath(leaf, paint);
        }
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
