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

import java.util.List;

/**
 * A folder tile: a 2x2 preview of its first four apps when closed,
 * with the same focus ring / lift behaviour as an app tile.
 */
final class FolderTile extends LinearLayout implements FocusedTile {

    private static final int FOCUS_MS = 180;
    private static final float FOCUS_SCALE = 1.14f;

    private final FrameLayout box;
    private FrameLayout card;
    private final View ring, sheen;
    private final TextView label;

    FolderTile(Context ctx, Folder folder, List<AppEntry> apps, int w, int h) {
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

        LinearLayout preview = new LinearLayout(ctx);
        preview.setOrientation(VERTICAL);
        int pad = (int) (w * 0.055f);
        preview.setPadding(pad, pad, pad, pad);
        int gap = (int) (3 * d);
        int iconPad = (int) (2 * d);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(HORIZONTAL);
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                FrameLayout cell = new FrameLayout(ctx);
                GradientDrawable cellBg = new GradientDrawable();
                cellBg.setColor(0xFF141A26);
                cellBg.setCornerRadius(8 * d);
                cell.setBackground(cellBg);
                cell.setClipToOutline(true);

                if (index < apps.size()) {
                    AppEntry app = apps.get(index);
                    ImageView art = new ImageView(ctx);
                    if (app.banner != null) {
                        art.setImageDrawable(app.banner);
                        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    } else {
                        art.setImageDrawable(app.icon);
                        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        art.setPadding(iconPad, iconPad, iconPad, iconPad);
                    }
                    cell.addView(art, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                } else {
                    cell.setVisibility(INVISIBLE);
                }

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (col == 0) lp.setMarginEnd(gap);
                if (row > 0) lp.topMargin = gap;
                line.addView(cell, lp);
            }
            preview.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        card.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // focus ring — invisible until focused
        ring = new View(ctx);
        GradientDrawable rg = new GradientDrawable();
        rg.setColor(0x00FFFFFF);
        rg.setCornerRadius(corner);
        rg.setStroke((int) (2 * d), 0xE6FFFFFF);
        ring.setBackground(rg);
        ring.setAlpha(0f);
        card.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // faint brighten over the preview on focus
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
        label.setText(folder.name);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(12f);
        label.setTextColor(0xFFE8EDF8);
        label.setAlpha(AppTile.labelsAlways ? 1f : 0f);
        label.setPadding(0, (int) (8 * d), 0, 0);
        addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override public void setFocusedState(boolean focused) {
        box.animate()
                .scaleX(focused ? FOCUS_SCALE : 1f)
                .scaleY(focused ? FOCUS_SCALE : 1f)
                .translationZ(focused ? 24f : 0f)
                .setDuration(FOCUS_MS)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
        ring.animate().alpha(focused ? 1f : 0f).setDuration(FOCUS_MS).start();
        sheen.animate().alpha(focused ? 0.10f : 0f).setDuration(FOCUS_MS).start();
        card.animate().translationZ(focused ? 14f : 0f)
                .setDuration(FOCUS_MS).start();
        label.animate().alpha(focused || AppTile.labelsAlways ? 1f : 0f)
                .setDuration(FOCUS_MS).start();
    }
}
