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

/** 16:9 app tile for the TV app drawer, styled like the home screen tiles. */
final class DrawerAppTile extends LinearLayout implements FocusedTile {

    private static final float FOCUS_SCALE = 1.08f;

    final AppEntry app;
    private final FrameLayout card;
    private final TextView label;
    private final ImageView pinBadge;
    private final View focusDot;
    private ObjectAnimator dotBreath;
    private final float d;

    DrawerAppTile(Context ctx, AppEntry app, int width) {
        super(ctx);
        this.app = app;
        d = ctx.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        int height = width * 9 / 16;
        float corner = 14 * d;

        card = new FrameLayout(ctx);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        addView(card, new LayoutParams(width, height));

        if (app.banner != null) {
            ImageView art = new ImageView(ctx);
            art.setImageDrawable(app.banner);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(art, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            card.setBackgroundColor(0xFF141414);
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = (int) (height * 0.55f);
            card.addView(icon, new FrameLayout.LayoutParams(
                    iconSize, iconSize, Gravity.CENTER));
        }

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding((int) (4 * d), (int) (10 * d), (int) (4 * d), 0);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        pinBadge = new ImageView(ctx);
        pinBadge.setImageResource(R.drawable.ic_pin);
        pinBadge.setColorFilter(0xFFC8FF3D);
        pinBadge.setVisibility(GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                (int) (14 * d), (int) (14 * d));
        plp.setMarginEnd((int) (6 * d));
        row.addView(pinBadge, plp);

        label = new TextView(ctx);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(13f);
        label.setTextColor(0xFFB9B9B9);
        row.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
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
        focusDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams dlp = new LayoutParams((int) (16 * d), (int) (16 * d));
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        dlp.topMargin = (int) (1 * d);
        addView(focusDot, dlp);
    }

    void setPinned(boolean pinned) {
        pinBadge.setVisibility(pinned ? View.VISIBLE : View.GONE);
    }

    @Override public void setFocusedState(boolean focused) {
        card.animate().cancel();
        card.animate().scaleX(focused ? FOCUS_SCALE : 1f)
                .scaleY(focused ? FOCUS_SCALE : 1f)
                .translationZ(focused ? 12 * d : 0f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
        label.setTextColor(focused ? 0xFFFFFFFF : 0xFFB9B9B9);
        if (focused) startDotBreathing();
        else stopDotBreathing();
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
