package dev.hyper.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Square, focus-first app tile for the TV app drawer. */
final class DrawerAppTile extends LinearLayout implements FocusedTile {

    private static final float FOCUS_SCALE = 1.06f;

    final AppEntry app;
    private final FrameLayout card;
    private final TextView label;
    private final ImageView pinBadge;
    private final float d;

    DrawerAppTile(Context ctx, AppEntry app, int size) {
        super(ctx);
        this.app = app;
        d = ctx.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        card = new FrameLayout(ctx);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setBackground(cardBackground(false));
        addView(card, new LinearLayout.LayoutParams(size, size));

        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = (int) (size * 0.54f);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                iconSize, iconSize, Gravity.CENTER);
        card.addView(icon, ilp);

        pinBadge = new ImageView(ctx);
        pinBadge.setImageResource(R.drawable.ic_home);
        pinBadge.setColorFilter(0xFFC8FF3D);
        pinBadge.setVisibility(GONE);
        pinBadge.setElevation(4 * d);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                (int) (15 * d), (int) (15 * d));
        plp.gravity = Gravity.TOP | Gravity.END;
        plp.topMargin = (int) (7 * d);
        plp.rightMargin = (int) (7 * d);
        card.addView(pinBadge, plp);

        label = new TextView(ctx);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(11.5f);
        label.setTextColor(0xFFB9B9B9);
        label.setPadding((int) (3 * d), (int) (8 * d),
                (int) (3 * d), 0);
        addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    void setPinned(boolean pinned) {
        pinBadge.setVisibility(pinned ? View.VISIBLE : View.GONE);
    }

    @Override public void setFocusedState(boolean focused) {
        card.animate().cancel();
        card.animate().scaleX(focused ? FOCUS_SCALE : 1f)
                .scaleY(focused ? FOCUS_SCALE : 1f)
                .translationZ(focused ? 16 * d : 0f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
        card.setBackground(cardBackground(focused));
        label.setTextColor(focused ? 0xFFFFFFFF : 0xFFB9B9B9);
    }

    private GradientDrawable cardBackground(boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        float corner = 18 * d;
        bg.setCornerRadius(corner);
        bg.setColor(focused ? 0xFF202020 : 0xFF141414);
        bg.setStroke((int) ((focused ? 2 : 1) * d),
                focused ? 0xFFC8FF3D : 0xFF242424);
        return bg;
    }
}
