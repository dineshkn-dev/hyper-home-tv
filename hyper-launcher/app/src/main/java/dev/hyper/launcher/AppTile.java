package dev.hyper.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * A 16:9 rounded card. Focus lifts it with a scale and shadow, shifts the art
 * for a parallax feel, and press dips it slightly. Edit mode wiggles it.
 */
final class AppTile extends LinearLayout {

    private static final float PARALLAX = 0.04f;
    private static final float PRESS_SCALE = 0.96f;

    private final FrameLayout box;
    private final FrameLayout card;
    private final ImageView art;
    private final View ring;
    private final TextView label;
    private final int dim, bright;
    private final float corner;
    private final Random rng = new Random();
    private ValueAnimator wiggle;
    private boolean focused;

    AppTile(Context ctx, AppEntry app, int w, int h) {
        super(ctx);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        float d = getResources().getDisplayMetrics().density;
        corner = 20 * d;
        int ringPad = (int) (4 * d);
        dim = 0xFF69748F;
        bright = 0xFFE8EDF8;

        box = new FrameLayout(ctx);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setCameraDistance(d * 4000);
        addView(box, new LayoutParams(w, h));

        card = new FrameLayout(ctx);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF151A26);
        cardBg.setCornerRadius(corner);
        card.setBackground(cardBg);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        card.setElevation(0f);
        box.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        art = new ImageView(ctx);
        if (app.banner != null) {
            art.setImageDrawable(app.banner);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(art, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            int s = (int) (h * 0.55f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s);
            lp.gravity = Gravity.CENTER;
            art.setImageDrawable(app.icon);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(art, lp);
        }

        ring = new View(ctx);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setStroke((int) (3 * d), Color.WHITE);
        ringBg.setCornerRadius(corner + ringPad);
        ring.setBackground(ringBg);
        ring.setAlpha(0f);
        FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ringLp.setMargins(-ringPad, -ringPad, -ringPad, -ringPad);
        box.addView(ring, ringLp);

        label = new TextView(ctx);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(13f);
        label.setTextColor(dim);
        label.setPadding(0, (int) (10 * d), 0, 0);
        addView(label, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    void setFocusedState(boolean focused, float dx) {
        this.focused = focused;
        float d = getResources().getDisplayMetrics().density;
        float scale = focused ? Motion.FOCUS_SCALE : 1f;
        box.animate().cancel();
        box.animate()
                .scaleX(scale).scaleY(scale)
                .translationZ(focused ? 24 * d : 0f)
                .setDuration(focused ? Motion.FOCUS_MS : Motion.UNFOCUS_MS)
                .setInterpolator(focused ? Motion.FOCUS_IN : Motion.FOCUS_OUT)
                .start();
        card.setElevation(focused ? 18 * d : 0f);
        ring.animate().alpha(focused ? 1f : 0f).setDuration(Motion.UNFOCUS_MS).start();
        art.animate()
                .translationX(focused ? -dx * getWidth() * PARALLAX : 0f)
                .setDuration(Motion.FOCUS_MS)
                .setInterpolator(Motion.EASE_OUT_EXPO)
                .start();
        label.animate().alpha(focused ? 1f : 0.7f).setDuration(Motion.UNFOCUS_MS).start();
        label.setTextColor(focused ? bright : dim);
    }

    void setPressScale(boolean pressed) {
        box.animate().cancel();
        float base = focused ? Motion.FOCUS_SCALE : 1f;
        float s = pressed ? PRESS_SCALE : base;
        box.animate().scaleX(s).scaleY(s).setDuration(120).start();
    }

    void startWiggle() {
        stopWiggle();
        wiggle = ValueAnimator.ofFloat(-1.4f, 1.4f);
        wiggle.setDuration(110);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setStartDelay(rng.nextInt(140));
        wiggle.addUpdateListener(a -> box.setRotation((float) a.getAnimatedValue()));
        wiggle.start();
    }

    void stopWiggle() {
        if (wiggle != null) { wiggle.cancel(); wiggle = null; }
        box.animate().rotation(0f).setDuration(100).start();
    }
}
