package dev.hyper.launcher;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Wide banner app tile used by the full-width apps row. */
final class PinnedAppTile extends LinearLayout implements FocusedTile {

    private static final float FOCUS_SCALE = 1.07f;

    final AppEntry app;
    private final FrameLayout box;
    private final TextView label;
    private final View focusDot;
    private ObjectAnimator dotBreath;

    PinnedAppTile(Context ctx, AppEntry app, int w, int artH) {
        super(ctx);
        this.app = app;
        float d = getResources().getDisplayMetrics().density;
        float corner = 14 * d;

        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setClipChildren(false);
        setClipToPadding(false);
        setContentDescription(app.label);

        box = new FrameLayout(ctx);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        addView(box, new LinearLayout.LayoutParams(w, artH));

        FrameLayout card = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF141414);
        bg.setCornerRadius(corner);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(2 * d);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        box.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (app.banner != null) {
            ImageView art = new ImageView(ctx);
            art.setImageDrawable(app.banner);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(art, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            ImageView icon = new ImageView(ctx);
            int size = Math.max(1, (int) (artH * 0.58f));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            lp.gravity = Gravity.CENTER;
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(icon, lp);
        }

        label = new TextView(ctx);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(11.5f);
        label.setTextColor(0xFFEDEDED);
        label.setPadding(0, (int) (7 * d), 0, 0);
        addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        focusDot = new View(ctx);
        GradientDrawable dotGlow = new GradientDrawable();
        dotGlow.setShape(GradientDrawable.OVAL);
        dotGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        dotGlow.setGradientCenter(0.5f, 0.5f);
        dotGlow.setGradientRadius(8 * d);
        dotGlow.setColors(new int[]{0x99C8FF3D, 0x4DC8FF3D, 0x00C8FF3D});

        GradientDrawable dotCore = new GradientDrawable();
        dotCore.setShape(GradientDrawable.OVAL);
        dotCore.setColor(0xFFC8FF3D);

        LayerDrawable dot = new LayerDrawable(new Drawable[]{dotGlow, dotCore});
        int dotInset = Math.max(1, (int) (5 * d));
        dot.setLayerInset(1, dotInset, dotInset, dotInset, dotInset);
        focusDot.setBackground(dot);
        focusDot.setAlpha(0f);
        focusDot.setClickable(false);
        focusDot.setFocusable(false);
        focusDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                (int) (16 * d), (int) (16 * d));
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        dlp.topMargin = (int) (1 * d);
        addView(focusDot, dlp);
    }

    @Override public void setFocusedState(boolean focused) {
        box.animate().scaleX(focused ? FOCUS_SCALE : 1f)
                .scaleY(focused ? FOCUS_SCALE : 1f)
                .translationZ(focused ? 18f : 0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.7f))
                .start();
        if (focused) startDotBreathing();
        else stopDotBreathing();
        label.setTextColor(focused ? 0xFFFFFFFF : 0xFFEDEDED);
    }

    private void startDotBreathing() {
        if (dotBreath != null) dotBreath.cancel();
        focusDot.setAlpha(0f);
        focusDot.setScaleX(0.94f);
        focusDot.setScaleY(0.94f);
        dotBreath = ObjectAnimator.ofPropertyValuesHolder(focusDot,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.94f, 1.06f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.94f, 1.06f));
        dotBreath.setDuration(1800);
        dotBreath.setRepeatCount(ValueAnimator.INFINITE);
        dotBreath.setRepeatMode(ValueAnimator.REVERSE);
        dotBreath.setInterpolator(new AccelerateDecelerateInterpolator());
        dotBreath.start();
    }

    private void stopDotBreathing() {
        if (dotBreath != null) {
            dotBreath.cancel();
            dotBreath = null;
        }
        focusDot.animate().cancel();
        focusDot.animate().alpha(0f).scaleX(1f).scaleY(1f)
                .setDuration(160).start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (dotBreath != null) {
            dotBreath.cancel();
            dotBreath = null;
        }
    }
}
