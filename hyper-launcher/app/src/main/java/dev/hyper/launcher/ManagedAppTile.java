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

/** App card in the Manage Apps grid. */
final class ManagedAppTile extends FrameLayout implements ManageTile {

    private final View ring;
    private final ImageView more;
    private final TextView name;

    ManagedAppTile(Context ctx, ManagedApp app, int w, int h) {
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
        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(app.app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int ip = (int) (4 * d);
        icon.setPadding(ip, ip, ip, ip);
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                (int) (20 * d), (int) (20 * d), Gravity.CENTER));
        content.addView(iconBox, new LinearLayout.LayoutParams(
                (int) (30 * d), (int) (30 * d)));

        content.addView(new View(ctx), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        name = new TextView(ctx);
        name.setText(app.app.label);
        name.setTextSize(11.5f);
        name.setTextColor(0xFFEDEDED);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(name);

        TextView size = new TextView(ctx);
        size.setText(ManagedApp.fmtSize(app.size));
        size.setTextSize(9.5f);
        size.setTextColor(0xFF9A9A9A);
        size.setSingleLine(true);
        size.setPadding(0, (int) (2 * d), 0, 0);
        content.addView(size);

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
