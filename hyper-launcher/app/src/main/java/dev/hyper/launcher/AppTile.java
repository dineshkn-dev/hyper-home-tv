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
    private final View sheen, specular, rim;
    private final TextView label;
    private final int bright;

    AppTile(Context ctx, AppEntry app, int w, int h) {
        super(ctx);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        float d = getResources().getDisplayMetrics().density;
        float corner = 16 * d;
        bright = 0xFFE8EDF8;

        box = new FrameLayout(ctx);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        addView(box, new LinearLayout.LayoutParams(w, h));

        FrameLayout card = new FrameLayout(ctx);
        // frosted-glass body — light falls off top-left → bottom-right
        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x30FFFFFF, 0x1CFFFFFF, 0x10FFFFFF});
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

            // depth wash: faint light top, gentle darkening at the base
            View wash = new View(ctx);
            GradientDrawable wg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x12FFFFFF, 0x00FFFFFF, 0x33000000});
            wg.setCornerRadius(corner);
            wash.setBackground(wg);
            card.addView(wash, new FrameLayout.LayoutParams(
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

        // specular highlight — light catching the top edge of the glass
        specular = new View(ctx);
        GradientDrawable sg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40FFFFFF, 0x14FFFFFF, 0x00FFFFFF});
        sg.setCornerRadius(corner);
        specular.setBackground(sg);
        specular.setAlpha(0.7f);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (h * 0.42f));
        sl.gravity = Gravity.TOP;
        card.addView(specular, sl);

        // glass rim — a hairline of light around the edge
        rim = new View(ctx);
        GradientDrawable rg = new GradientDrawable();
        rg.setColor(0x00FFFFFF);
        rg.setCornerRadius(corner);
        rg.setStroke(Math.max(1, (int) d), 0x33FFFFFF);
        rim.setBackground(rg);
        rim.setAlpha(0.8f);
        card.addView(rim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

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
        specular.animate().alpha(focused ? 1f : 0.7f)
                .setDuration(FOCUS_MS).start();
        rim.animate().alpha(focused ? 1f : 0.8f).setDuration(FOCUS_MS).start();
        card.animate().translationZ(focused ? 14f : 0f)
                .setDuration(FOCUS_MS).start();
        label.animate().alpha(focused || labelsAlways ? 1f : 0f)
                .setDuration(FOCUS_MS).start();
    }
}
