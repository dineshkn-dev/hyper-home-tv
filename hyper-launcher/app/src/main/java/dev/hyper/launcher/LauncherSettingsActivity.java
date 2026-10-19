package dev.hyper.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * In-launcher settings — PS5-style split pane. A left rail of categories
 * previews the right-hand option list live as focus moves; OK opens a
 * small anchored picker under the row. All framework views.
 */
public final class LauncherSettingsActivity extends Activity {

    static final String PREF = "hyper";
    static final String K_COMPANION = "companion";   // 0 full, 1 ambient, 2 off
    static final String K_MOUSE = "mouse";
    static final String K_HERO = "hero";             // stats strip under the top bar
    static final String K_SECONDS = "seconds";
    static final String K_LABELS = "labels";         // true = always show names
    static final String K_WEATHER = "weather";
    static final String K_WIFI = "wifi";

    private static final int TXT = 0xFFE8EDF8;
    private static final int DIM = 0xFF69748F;
    private static final int ICON = 0xFF9FB4D8;
    private static final int ACCENT = 0xFF8FB9FF;

    private SharedPreferences prefs;
    private float d;
    private LinearLayout pane;
    private final List<View> railViews = new ArrayList<>();
    private final List<Cat> cats = new ArrayList<>();
    private Cat active;

    private interface Options { String[] get(); }
    private interface Current { int get(); }
    private interface Pick { void set(int i); }
    private interface OnPick { void onPick(int i); }

    private static final class Row {
        int icon; String label, desc;
        Options opts; Current cur; Pick pick;        // option row
        Runnable action;                             // action row
        String value;                                // static info row
    }

    private static final class Cat {
        final int icon; final String label;
        final List<Row> rows = new ArrayList<>();
        Cat(int icon, String label) { this.icon = icon; this.label = label; }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;
        buildCats();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setClipChildren(false);
        root.setPadding((int) (70 * d), (int) (48 * d),
                (int) (70 * d), (int) (44 * d));
        setContentView(root);

        // ---- left rail ----
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setClipChildren(false);
        root.addView(rail, new LinearLayout.LayoutParams(
                (int) (300 * d), LinearLayout.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(30f);
        title.setTextColor(TXT);
        rail.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Hyper Launcher");
        sub.setTextSize(13f);
        sub.setTextColor(DIM);
        sub.setPadding(0, (int) (4 * d), 0, (int) (34 * d));
        rail.addView(sub);

        for (Cat c : cats) rail.addView(railItem(c));

        // ---- hairline divider ----
        View div = new View(this);
        div.setBackgroundColor(0x12FFFFFF);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                1, LinearLayout.LayoutParams.MATCH_PARENT);
        dl.setMargins((int) (30 * d), (int) (8 * d), (int) (30 * d), 0);
        root.addView(div, dl);

        // ---- right pane ----
        pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setClipChildren(false);
        pane.setClipToPadding(false);
        root.addView(pane, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // entrance: rail items slide in staggered, then first pane populates
        int i = 0;
        for (View v : railViews) {
            v.setAlpha(0f);
            v.setTranslationX(-26 * d);
            v.animate().alpha(1f).translationX(0)
                    .setStartDelay(80 + 45L * i++).setDuration(240).start();
        }
        railViews.get(0).post(() -> railViews.get(0).requestFocus());
    }

    // ---------- categories ----------

    private void buildCats() {
        Cat pers = new Cat(R.drawable.ic_paw, "Personalize");
        pers.rows.add(opt(R.drawable.ic_paw, "Companion",
                "The little cat on the bottom edge",
                () -> new String[]{"Full", "Ambient", "Off"},
                () -> prefs.getInt(K_COMPANION, 0),
                i -> prefs.edit().putInt(K_COMPANION, i).apply()));
        pers.rows.add(opt(R.drawable.ic_mouse, "Mouse chase",
                "Jerry pops out while the cat sleeps",
                () -> new String[]{"On", "Off"},
                () -> prefs.getBoolean(K_MOUSE, true) ? 0 : 1,
                i -> prefs.edit().putBoolean(K_MOUSE, i == 0).apply()));
        pers.rows.add(opt(R.drawable.ic_gauge, "Hero strip",
                "RAM · CPU · storage · temperature",
                () -> new String[]{"On", "Off"},
                () -> prefs.getBoolean(K_HERO, true) ? 0 : 1,
                i -> prefs.edit().putBoolean(K_HERO, i == 0).apply()));
        pers.rows.add(opt(R.drawable.ic_clock, "Clock seconds",
                "Show seconds in the top-bar clock",
                () -> new String[]{"On", "Off"},
                () -> prefs.getBoolean(K_SECONDS, true) ? 0 : 1,
                i -> prefs.edit().putBoolean(K_SECONDS, i == 0).apply()));
        pers.rows.add(opt(R.drawable.ic_tag, "App names",
                "Labels under the tiles",
                () -> new String[]{"On focus", "Always"},
                () -> prefs.getBoolean(K_LABELS, false) ? 1 : 0,
                i -> prefs.edit().putBoolean(K_LABELS, i == 1).apply()));
        cats.add(pers);

        Cat bar = new Cat(R.drawable.ic_wifi, "Status bar");
        bar.rows.add(opt(R.drawable.ic_cloud, "Weather chip",
                "Weather + AQI in the top bar",
                () -> new String[]{"On", "Off"},
                () -> prefs.getBoolean(K_WEATHER, true) ? 0 : 1,
                i -> prefs.edit().putBoolean(K_WEATHER, i == 0).apply()));
        bar.rows.add(opt(R.drawable.ic_wifi, "Wi-Fi chip",
                "Network status in the top bar",
                () -> new String[]{"On", "Off"},
                () -> prefs.getBoolean(K_WIFI, true) ? 0 : 1,
                i -> prefs.edit().putBoolean(K_WIFI, i == 0).apply()));
        cats.add(bar);

        Cat sys = new Cat(R.drawable.ic_settings, "System");
        sys.rows.add(act(R.drawable.ic_bolt, "Optimize memory",
                "Clear cached background apps",
                () -> HeroStrip.sweep(this, freed ->
                        Toast.makeText(this,
                                freed > 0 ? "Freed " + freed + " MB"
                                        : "Memory already optimized",
                                Toast.LENGTH_SHORT).show())));
        sys.rows.add(info(R.drawable.ic_info, "About this TV",
                Build.MODEL + " · Android " + Build.VERSION.RELEASE));
        cats.add(sys);
    }

    private static Row opt(int icon, String label, String desc,
                           Options o, Current c, Pick p) {
        Row r = new Row();
        r.icon = icon; r.label = label; r.desc = desc;
        r.opts = o; r.cur = c; r.pick = p;
        return r;
    }

    private static Row act(int icon, String label, String desc, Runnable a) {
        Row r = new Row();
        r.icon = icon; r.label = label; r.desc = desc; r.action = a;
        return r;
    }

    private static Row info(int icon, String label, String value) {
        Row r = new Row();
        r.icon = icon; r.label = label; r.value = value;
        return r;
    }

    // ---------- rail ----------

    private View railItem(Cat c) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.HORIZONTAL);
        it.setGravity(Gravity.CENTER_VERTICAL);
        it.setPadding((int) (16 * d), (int) (13 * d),
                (int) (16 * d), (int) (13 * d));
        it.setFocusable(true);
        it.setClickable(true);
        it.setTag(c);

        ImageView ic = new ImageView(this);
        ic.setImageResource(c.icon);
        ic.setTag("icon");
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(
                (int) (19 * d), (int) (19 * d));
        il.setMarginEnd((int) (14 * d));
        it.addView(ic, il);

        TextView t = new TextView(this);
        t.setText(c.label);
        t.setTextSize(16.5f);
        t.setTag("label");
        it.addView(t);

        it.setOnFocusChangeListener((v, has) -> {
            if (has) populate(c);
            styleRailItem(v, c, has);
            v.animate().translationX(has ? 5 * d : 0)
                    .setDuration(160).start();
        });
        // OK on the rail hops into the pane
        it.setOnClickListener(v -> {
            if (pane.getChildCount() > 0)
                pane.getChildAt(0).requestFocus();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (6 * d);
        it.setLayoutParams(lp);
        railViews.add(it);
        styleRailItem(it, c, false);
        return it;
    }

    private void styleRailItem(View v, Cat c, boolean focused) {
        boolean isActive = c == active;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(14 * d);
        bg.setColor(focused ? 0x1FFFFFFF : isActive ? 0x12FFFFFF : 0x00000000);
        v.setBackground(bg);
        int fg = focused ? 0xFFFFFFFF : isActive ? TXT : DIM;
        ImageView ic = v.findViewWithTag("icon");
        TextView t = v.findViewWithTag("label");
        if (ic != null) ic.setColorFilter(fg);
        if (t != null) t.setTextColor(fg);
    }

    private void styleRail() {
        for (View v : railViews)
            styleRailItem(v, (Cat) v.getTag(), v.hasFocus());
    }

    // ---------- right pane ----------

    private void populate(Cat c) {
        boolean same = c == active && pane.getChildCount() > 0;
        active = c;
        styleRail();
        if (same) return;
        pane.removeAllViews();

        int i = 0;
        for (Row r : c.rows) {
            View row = buildRow(r);
            row.setAlpha(0f);
            row.setTranslationX(30 * d);
            pane.addView(row);
            row.animate().alpha(1f).translationX(0)
                    .setStartDelay(70 + 40L * i++).setDuration(240).start();
        }
    }

    private View buildRow(Row r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (18 * d), (int) (14 * d),
                (int) (18 * d), (int) (14 * d));
        row.setFocusable(true);
        row.setClickable(true);
        row.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x10FFFFFF);
        bg.setCornerRadius(18 * d);
        row.setBackground(bg);
        row.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v,
                    android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 18 * d);
            }
        });

        // icon chip
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(0x1AFFFFFF);
        chipBg.setCornerRadius(13 * d);
        chip.setBackground(chipBg);
        ImageView ic = new ImageView(this);
        ic.setImageResource(r.icon);
        ic.setColorFilter(ICON);
        chip.addView(ic, new LinearLayout.LayoutParams(
                (int) (21 * d), (int) (21 * d)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                (int) (44 * d), (int) (44 * d));
        cp.setMarginEnd((int) (18 * d));
        row.addView(chip, cp);

        // label + description
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(r.label);
        name.setTextSize(16.5f);
        name.setTextColor(TXT);
        texts.addView(name);
        TextView sub = new TextView(this);
        sub.setText(r.desc);
        sub.setTextSize(12.5f);
        sub.setTextColor(DIM);
        sub.setPadding(0, (int) (3 * d), 0, 0);
        texts.addView(sub);
        if (r.desc == null) texts.getChildAt(1).setVisibility(View.GONE);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(texts, tp);

        // current value (option rows) or static text (info rows)
        TextView value = new TextView(this);
        value.setTextSize(14.5f);
        value.setTextColor(ICON);
        value.setSingleLine(true);
        if (r.opts != null) value.setText(r.opts.get()[r.cur.get()]);
        else if (r.value != null) value.setText(r.value);
        row.addView(value);

        // chevron on interactive rows
        if (r.opts != null || r.action != null) {
            TextView chev = new TextView(this);
            chev.setText("›");
            chev.setTextSize(22f);
            chev.setTextColor(DIM);
            chev.setPadding((int) (10 * d), 0, 0, 0);
            row.addView(chev);
        }

        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x24FFFFFF : 0x10FFFFFF);
            name.setTextColor(has ? 0xFFFFFFFF : TXT);
            ic.setColorFilter(has ? 0xFFDCE6FA : ICON);
            v.setTranslationZ(has ? 6 * d : 0);
            v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                    .setInterpolator(new OvershootInterpolator(1.4f))
                    .setDuration(170).start();
        });
        row.setOnClickListener(v -> {
            if (r.opts != null)
                showOptions(row, r.opts.get(), r.cur.get(), i -> {
                    r.pick.set(i);
                    value.setText(r.opts.get()[r.cur.get()]);
                });
            else if (r.action != null)
                r.action.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (10 * d);
        row.setLayoutParams(lp);
        return row;
    }

    // ---------- option picker ----------

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
        mb.setCornerRadius(18 * d);
        mb.setStroke((int) d, 0x2AFFFFFF);
        menu.setBackground(mb);

        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding((int) (18 * d), (int) (12 * d),
                    (int) (18 * d), (int) (12 * d));
            item.setFocusable(true);
            item.setClickable(true);
            GradientDrawable ib = new GradientDrawable();
            ib.setCornerRadius(12 * d);
            ib.setColor(i == current ? 0x2E5B9DFF : 0x00000000);
            item.setBackground(ib);

            TextView t = new TextView(this);
            t.setText(options[i]);
            t.setTextSize(15f);
            t.setTextColor(i == current ? ACCENT : 0xFFC9D2E8);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            item.addView(t, tlp);

            // dot marker on the selected option
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(8f);
            dot.setTextColor(ACCENT);
            dot.setVisibility(i == current ? View.VISIBLE : View.INVISIBLE);
            item.addView(dot);

            item.setOnFocusChangeListener((v, has) -> {
                ib.setColor(has ? 0x2EFFFFFF
                        : idx == current ? 0x2E5B9DFF : 0x00000000);
                t.setTextColor(has ? 0xFFFFFFFF
                        : idx == current ? ACCENT : 0xFFC9D2E8);
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
            a.width = (int) (320 * d);
            w.setAttributes(a);
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dlg.show();
        // focus the current option
        menu.getChildAt(sel).post(() -> menu.getChildAt(sel).requestFocus());
    }
}
