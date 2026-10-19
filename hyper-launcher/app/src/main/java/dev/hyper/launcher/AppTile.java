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
 * (icon centered when no banner exists), a label, and a focus ring.
 */
final class AppTile extends LinearLayout {

    private static final int FOCUS_MS = 180;
    private static final float FOCUS_SCALE = 1.14f;
    static boolean labelsAlways;   // settings: always show app names

    private final FrameLayout box;
    private FrameLayout card;
    private final View sheen;
    private final TextView label;
    private final int bright;

    AppTile(Context ctx, AppEntry app, int w, int h) {
        super(ctx);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        float d = getResources().getDisplayMetrics().density;
        float corner = 14 * d;
        bright = 0xFFE8EDF8;

        box = new FrameLayout(ctx);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        addView(box, new LinearLayout.LayoutParams(w, h));

        FrameLayout card = new FrameLayout(ctx);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1A2030);
        cardBg.setCornerRadius(corner);
        card.setBackground(cardBg);
        card.setClipToOutline(true);
        card.setElevation(2 * d);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        this.card = card;
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

        // faint brighten sheen over the art on focus
        sheen = new View(ctx);
        GradientDrawable sheenBg = new GradientDrawable();
        sheenBg.setColor(0xFFFFFFFF);
        sheenBg.setCornerRadius(corner);
        sheen.setBackground(sheenBg);
        sheen.setAlpha(0f);
        card.addView(sheen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        label = new TextView(ctx);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(12f);
        label.setTextColor(bright);
        label.setAlpha(labelsAlways ? 1f : 0f);
        label.setPadding(0, (int) (8 * d), 0, 0);
        addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    void setFocusedState(boolean focused) {
        box.animate()
                .scaleX(focused ? FOCUS_SCALE : 1f)
                .scaleY(focused ? FOCUS_SCALE : 1f)
                .translationZ(focused ? 24f : 0f)
                .setDuration(FOCUS_MS)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
        sheen.animate().alpha(focused ? 0.10f : 0f).setDuration(FOCUS_MS).start();
        card.animate().translationZ(focused ? 14f : 0f)
                .setDuration(FOCUS_MS).start();
        label.animate().alpha(focused || labelsAlways ? 1f : 0f)
                .setDuration(FOCUS_MS).start();
    }
}
