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

/**
 * One app tile: a 16:9 rounded card filled with the app's banner art
 * (icon centered when no banner exists) and a label.
 */
final class AppTile extends LinearLayout implements FocusedTile {

    private static final float FOCUS_SCALE = 1.14f;

    private final FrameLayout box;
    private final TextView label;

    AppTile(Context ctx, AppEntry app, int w, int h) {
        super(ctx);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        float d = getResources().getDisplayMetrics().density;
        float corner = 16 * d;

        box = new FrameLayout(ctx);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        addView(box, new LinearLayout.LayoutParams(w, h));

        FrameLayout card = new FrameLayout(ctx);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF141414);          // flat card for icon tiles
        cardBg.setCornerRadius(corner);
        card.setBackground(cardBg);
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
            ImageView art = new ImageView(ctx);
            int s = (int) (h * 0.52f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s);
            lp.gravity = Gravity.CENTER;
            art.setImageDrawable(app.icon);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(art, lp);
        }

        label = new TextView(ctx);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(12f);
        label.setTextColor(0xFFEDEDED);
        label.setPadding(0, (int) (8 * d), 0, 0);
        addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override public void setFocusedState(boolean focused) {
        box.animate().scaleX(focused ? FOCUS_SCALE : 1f)
                .scaleY(focused ? FOCUS_SCALE : 1f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
        label.setTextColor(focused ? 0xFFFFFFFF : 0xFFEDEDED);
    }
}
