package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
    }

    @Override public void setFocusedState(boolean focused) {
        box.animate().scaleX(focused ? FOCUS_SCALE : 1f)
                .scaleY(focused ? FOCUS_SCALE : 1f)
                .translationZ(focused ? 18f : 0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.7f))
                .start();
        label.setTextColor(focused ? 0xFFFFFFFF : 0xFFEDEDED);
    }
}
