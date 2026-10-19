package dev.hyper.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * In-launcher settings page — a TV-style card list.
 * OK opens a small option picker under the row; BACK leaves.
 */
public final class LauncherSettingsActivity extends Activity {

    static final String PREF = "hyper";
    static final String K_COMPANION = "companion";   // 0 full, 1 ambient, 2 off
    static final String K_SECONDS = "seconds";
    static final String K_LABELS = "labels";         // true = always show names
    static final String K_WEATHER = "weather";
    static final String K_WIFI = "wifi";

    private SharedPreferences prefs;
    private float d;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding((int) (90 * d), (int) (32 * d),
                (int) (90 * d), (int) (40 * d));
        setContentView(root);

        // ---- header ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, (int) (20 * d));
        root.addView(head);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_paw);
        logo.setColorFilter(0xFF5B9DFF);
        logo.setAlpha(0.9f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (26 * d), (int) (26 * d));
        lp.setMarginEnd((int) (14 * d));
        head.addView(logo, lp);

        TextView title = new TextView(this);
        title.setText("Launcher settings");
        title.setTextSize(24f);
        title.setTextColor(0xFFE8EDF8);
        head.addView(title);

        TextView hint = new TextView(this);
        hint.setText("D-pad to move · OK for options · Back to return");
        hint.setTextSize(13f);
        hint.setTextColor(0xFF69748F);
        hint.setPadding(0, 0, 0, (int) (22 * d));
        root.addView(hint);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        root.addView(col, new LinearLayout.LayoutParams(
                (int) (560 * d), LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- rows ----
        addRow(col, R.drawable.ic_paw, "Companion",
                "The little cat on the bottom edge",
                () -> new String[]{"Full", "Ambient", "Off"},
                () -> prefs.getInt(K_COMPANION, 0),
                i -> prefs.edit().putInt(K_COMPANION, i).apply());
        addRow(col, R.drawable.ic_clock, "Clock seconds",
                "Show seconds in the top-bar clock",
                () -> new String[]{"On", "Off"},
                () -> prefs.getBoolean(K_SECONDS, true) ? 0 : 1,
                i -> prefs.edit().putBoolean(K_SECONDS, i == 0).apply());
        addRow(col, R.drawable.ic_tag, "App names",
                "Labels under the tiles",
                () -> new String[]{"On focus", "Always"},
                () -> prefs.getBoolean(K_LABELS, false) ? 1 : 0,
                i -> prefs.edit().putBoolean(K_LABELS, i == 1).apply());
        addRow(col, R.drawable.ic_cloud, "Weather chip",
                "Weather + AQI in the top bar",
                () -> new String[]{"On", "Off"},
                () -> prefs.getBoolean(K_WEATHER, true) ? 0 : 1,
                i -> prefs.edit().putBoolean(K_WEATHER, i == 0).apply());
        addRow(col, R.drawable.ic_wifi, "Wi-Fi chip",
                "Network status in the top bar",
                () -> new String[]{"On", "Off"},
                () -> prefs.getBoolean(K_WIFI, true) ? 0 : 1,
                i -> prefs.edit().putBoolean(K_WIFI, i == 0).apply());
    }

    private interface Options { String[] get(); }
    private interface Current { int get(); }
    private interface Pick { void set(int i); }

    private void addRow(LinearLayout col, int icon, String label,
                        String desc, Options opts, Current cur, Pick pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (16 * d), (int) (10 * d),
                (int) (16 * d), (int) (10 * d));
        row.setFocusable(true);
        row.setClickable(true);
        row.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x12FFFFFF);
        bg.setCornerRadius(14 * d);
        row.setBackground(bg);
        row.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v,
                    android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 14 * d);
            }
        });

        // icon in a subtle chip
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(0x1FFFFFFF);
        chipBg.setCornerRadius(10 * d);
        chip.setBackground(chipBg);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(0xFF9FB4D8);
        chip.addView(ic, new LinearLayout.LayoutParams(
                (int) (18 * d), (int) (18 * d)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                (int) (34 * d), (int) (34 * d));
        cp.setMarginEnd((int) (16 * d));
        row.addView(chip, cp);

        // label + description column
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15.5f);
        name.setTextColor(0xFFE8EDF8);
        texts.addView(name);
        TextView sub = new TextView(this);
        sub.setText(desc);
        sub.setTextSize(12f);
        sub.setTextColor(0xFF69748F);
        sub.setPadding(0, (int) (2 * d), 0, 0);
        texts.addView(sub);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(texts, tp);

        // value chip
        TextView value = new TextView(this);
        value.setTextSize(13f);
        value.setTextColor(0xFF5B9DFF);
        value.setPadding((int) (10 * d), (int) (4 * d),
                (int) (10 * d), (int) (4 * d));
        GradientDrawable vb = new GradientDrawable();
        vb.setColor(0x1F5B9DFF);
        vb.setCornerRadius(8 * d);
        value.setBackground(vb);
        value.setText(opts.get()[cur.get()]);
        row.addView(value);

        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x24FFFFFF : 0x12FFFFFF);
            name.setTextColor(has ? 0xFFFFFFFF : 0xFFE8EDF8);
            v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                    .setDuration(140).start();
        });
        row.setOnClickListener(v ->
                showOptions(row, opts.get(), cur.get(), i -> {
                    pick.set(i);
                    value.setText(opts.get()[cur.get()]);
                }));

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.bottomMargin = (int) (8 * d);
        col.addView(row, lp2);
    }

    private interface OnPick { void onPick(int i); }

    /** small option picker — blurred card anchored under the row */
    private void showOptions(View anchor, String[] options,
                             int current, OnPick onPick) {
        Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding((int) (8 * d), (int) (8 * d),
                (int) (8 * d), (int) (8 * d));
        GradientDrawable mb = new GradientDrawable();
        mb.setColor(0xFF171C28);
        mb.setCornerRadius(16 * d);
        mb.setStroke((int) d, 0x2AFFFFFF);
        menu.setBackground(mb);

        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding((int) (16 * d), (int) (10 * d),
                    (int) (16 * d), (int) (10 * d));
            item.setFocusable(true);
            item.setClickable(true);
            GradientDrawable ib = new GradientDrawable();
            ib.setCornerRadius(10 * d);
            ib.setColor(i == current ? 0x2E5B9DFF : 0x00000000);
            item.setBackground(ib);

            TextView t = new TextView(this);
            t.setText(options[i]);
            t.setTextSize(14.5f);
            t.setTextColor(i == current ? 0xFF8FB9FF : 0xFFC9D2E8);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            item.addView(t, tlp);

            // dot marker on the selected option
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(8f);
            dot.setTextColor(0xFF8FB9FF);
            dot.setVisibility(i == current ? View.VISIBLE : View.INVISIBLE);
            item.addView(dot);

            item.setOnFocusChangeListener((v, has) -> {
                ib.setColor(has ? 0x2EFFFFFF
                        : idx == current ? 0x2E5B9DFF : 0x00000000);
                t.setTextColor(has ? 0xFFFFFFFF
                        : idx == current ? 0xFF8FB9FF : 0xFFC9D2E8);
            });
            item.setOnClickListener(v -> { onPick.onPick(idx); dlg.dismiss(); });
            menu.addView(item);
        }
        final int sel = current;

        dlg.setContentView(menu);
        Window w = dlg.getWindow();
        if (w != null) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams a = w.getAttributes();
            a.setBlurBehindRadius(40);
            a.dimAmount = 0.45f;
            a.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            int[] xy = new int[2];
            anchor.getLocationOnScreen(xy);
            a.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            a.y = xy[1] + anchor.getHeight() + (int) (6 * d);
            a.width = (int) (300 * d);
            w.setAttributes(a);
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dlg.show();
        // focus the current option
        menu.getChildAt(sel).post(() -> menu.getChildAt(sel).requestFocus());
    }
}
