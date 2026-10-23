package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Folder card in the Manage Apps grid. */
final class ManagedFolderTile extends FrameLayout implements ManageTile {

    private final View ring;
    private final ImageView more;
    private final TextView name;

    ManagedFolderTile(Context ctx, Folder folder, List<AppEntry> apps, int w, int h) {
        super(ctx);
        float d = ctx.getResources().getDisplayMetrics().density;
        setBackgroundResource(R.drawable.bg_app_tile);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 10 * d);
            }
        });

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) (8 * d), (int) (8 * d),
                (int) (8 * d), (int) (8 * d));

        FrameLayout iconBox = new FrameLayout(ctx);
        iconBox.setBackgroundResource(R.drawable.bg_app_icon);
        LinearLayout preview = new LinearLayout(ctx);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding((int) (3 * d), (int) (3 * d),
                (int) (3 * d), (int) (3 * d));
        int gap = (int) (1 * d);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                ImageView icon = new ImageView(ctx);
                if (index < apps.size()) icon.setImageDrawable(apps.get(index).icon);
                else icon.setVisibility(INVISIBLE);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (col == 0) lp.setMarginEnd(gap);
                line.addView(icon, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            if (row > 0) lp.topMargin = gap;
            preview.addView(line, lp);
        }
        iconBox.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(iconBox, new LinearLayout.LayoutParams(
                (int) (30 * d), (int) (30 * d)));

        content.addView(new View(ctx), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        name = new TextView(ctx);
        name.setText(folder.name);
        name.setTextSize(11.5f);
        name.setTextColor(0xFFEDEDED);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(name);

        TextView count = new TextView(ctx);
        count.setText(apps.size() + (apps.size() == 1 ? " app" : " apps"));
        count.setTextSize(9.5f);
        count.setTextColor(0xFF9A9A9A);
        count.setSingleLine(true);
        count.setPadding(0, (int) (2 * d), 0, 0);
        content.addView(count);

        addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ring = new View(ctx);
        GradientDrawable rg = new GradientDrawable();
        rg.setColor(0x00000000);
        rg.setCornerRadius(10 * d);
        rg.setStroke((int) (2 * d), 0xFFC8FF3D);
        ring.setBackground(rg);
        ring.setAlpha(0f);
        addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        more = new ImageView(ctx);
        more.setImageResource(R.drawable.ic_more);
        more.setColorFilter(0xFF9A9A9A);
        more.setAlpha(0f);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                (int) (14 * d), (int) (14 * d));
        mlp.gravity = Gravity.TOP | Gravity.END;
        mlp.topMargin = (int) (7 * d);
        mlp.rightMargin = (int) (7 * d);
        addView(more, mlp);
    }

    @Override public void setSelectedState(boolean selected) {
        ring.animate().alpha(selected ? 1f : 0f).setDuration(120).start();
        more.animate().alpha(selected ? 1f : 0f).setDuration(120).start();
        name.setTextColor(selected ? 0xFFFFFFFF : 0xFFEDEDED);
    }
}
