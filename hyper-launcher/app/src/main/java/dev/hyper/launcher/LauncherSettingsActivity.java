package dev.hyper.launcher;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * In-launcher settings — grouped glass cards in a single scrollable
 * column. Booleans get animated toggle switches, the multi-state
 * companion option is an inline segmented control (D-Pad left/right),
 * and actions run inline. No dialogs, no panes — everything changes
 * where you press it. All framework views.
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

    private static final int TXT    = 0xFFEDF1FA;
    private static final int DIM    = 0xFF7B86A2;
    private static final int ICON   = 0xFFA9BCE4;
    private static final int ACCENT = 0xFF6EA8FF;

    private SharedPreferences prefs;
    private float d;
    private View firstFocusable;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setPadding((int) (72 * d), (int) (42 * d),
                (int) (72 * d), 0);
        setContentView(root);

        // ---------- scrollable column (header scrolls with content) ----------
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        // app icon in a glass tile
        FrameLayout badge = new FrameLayout(this);
        GradientDrawable badgeBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x22FFFFFF, 0x0CFFFFFF});
        badgeBg.setCornerRadius(14 * d);
        badgeBg.setStroke((int) d, 0x26FFFFFF);
        badge.setBackground(badgeBg);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        badge.addView(logo, new FrameLayout.LayoutParams(
                (int) (30 * d), (int) (30 * d), Gravity.CENTER));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                (int) (52 * d), (int) (52 * d));
        bp.setMarginEnd((int) (18 * d));
        head.addView(badge, bp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView over = new TextView(this);
        over.setText("HYPER LAUNCHER");
        over.setTextSize(11.5f);
        over.setTextColor(ACCENT);
        over.setLetterSpacing(0.16f);
        titles.addView(over);
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(30f);
        title.setTextColor(TXT);
        titles.addView(title);
        head.addView(titles);
        col.addView(head);

        // Personalize
        LinearLayout g1 = section(col, "Personalize");
        segmentRow(g1, R.drawable.ic_paw, "Companion",
                "The little cat on the bottom edge",
                new String[]{"Full", "Ambient", "Off"},
                prefs.getInt(K_COMPANION, 0),
                i -> prefs.edit().putInt(K_COMPANION, i).apply());
        switchRow(g1, R.drawable.ic_mouse, "Mouse chase",
                "Jerry pops out while the cat sleeps",
                K_MOUSE, true);
        switchRow(g1, R.drawable.ic_gauge, "Hero strip",
                "RAM · CPU · storage · temperature",
                K_HERO, true);
        switchRow(g1, R.drawable.ic_clock, "Clock seconds",
                "Show seconds in the top-bar clock",
                K_SECONDS, true);
        switchRow(g1, R.drawable.ic_tag, "Always show app names",
                "Labels under every tile, or only on focus",
                K_LABELS, false);

        // Status bar
        LinearLayout g2 = section(col, "Status bar");
        switchRow(g2, R.drawable.ic_cloud, "Weather chip",
                "Weather + AQI in the top bar",
                K_WEATHER, true);
        switchRow(g2, R.drawable.ic_wifi, "Wi-Fi chip",
                "Network status in the top bar",
                K_WIFI, true);

        // System
        LinearLayout g3 = section(col, "System");
        actionRow(g3, R.drawable.ic_bolt, "Optimize memory",
                "Clear cached background apps",
                "Optimize");
        infoRow(g3, R.drawable.ic_info, "About this TV",
                Build.MODEL + " · Android " + Build.VERSION.RELEASE);

        // footer hint
        TextView hint = new TextView(this);
        hint.setText("OK toggles · D-Pad ◀ ▶ adjusts · Back exits");
        hint.setTextSize(12f);
        hint.setTextColor(DIM);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = (int) (18 * d);
        hlp.bottomMargin = (int) (18 * d);
        col.addView(hint, hlp);

        // staggered entrance — groups rise and fade in
        int i = 0;
        for (int k = 0; k < col.getChildCount(); k++) {
            View v = col.getChildAt(k);
            v.setAlpha(0f);
            v.setTranslationY(22 * d);
            v.animate().alpha(1f).translationY(0)
                    .setStartDelay(90 + 60L * i++)
                    .setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
        if (firstFocusable != null)
            firstFocusable.post(() -> firstFocusable.requestFocus());
    }

    // ---------- group card ----------

    private LinearLayout section(LinearLayout col, String title) {
        TextView h = new TextView(this);
        h.setText(title.toUpperCase(Locale.US));
        h.setTextSize(12f);
        h.setTextColor(DIM);
        h.setLetterSpacing(0.14f);
        h.setPadding((int) (12 * d), 0, 0, (int) (10 * d));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = (int) (24 * d);
        col.addView(h, hp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x14FFFFFF, 0x09FFFFFF});
        bg.setCornerRadius(22 * d);
        bg.setStroke((int) d, 0x18FFFFFF);
        card.setBackground(bg);
        col.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void hairline(LinearLayout card) {
        View line = new View(this);
        line.setBackgroundColor(0x0CFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = (int) (78 * d);   // align with row labels
        lp.rightMargin = (int) (20 * d);
        card.addView(line, lp);
    }

    // ---------- row scaffold ----------

    /** Shared row chrome: icon tile + label/description, returns the row
     *  with a `right` slot tagged for the caller's control. */
    private LinearLayout rowBase(LinearLayout card, int icon,
                                 String label, String desc, boolean focusable) {
        if (card.getChildCount() > 0) hairline(card);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (20 * d), (int) (16 * d),
                (int) (20 * d), (int) (16 * d));
        row.setFocusable(focusable);
        row.setClickable(focusable);
        row.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * d);
        bg.setColor(0x00000000);
        row.setBackground(bg);

        // icon tile
        FrameLayout tile = new FrameLayout(this);
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(0x14FFFFFF);
        tb.setCornerRadius(12 * d);
        tile.setBackground(tb);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(ICON);
        ic.setTag("icon");
        tile.addView(ic, new FrameLayout.LayoutParams(
                (int) (20 * d), (int) (20 * d), Gravity.CENTER));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                (int) (40 * d), (int) (40 * d));
        tp.setMarginEnd((int) (18 * d));
        row.addView(tile, tp);

        // label + description
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(16f);
        name.setTextColor(TXT);
        name.setTag("name");
        texts.addView(name);
        if (desc != null) {
            TextView sub = new TextView(this);
            sub.setText(desc);
            sub.setTextSize(12.5f);
            sub.setTextColor(DIM);
            sub.setPadding(0, (int) (3 * d), 0, 0);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (focusable) {
            row.setOnFocusChangeListener((v, has) -> {
                bg.setColor(has ? 0x1EFFFFFF : 0x00000000);
                bg.setStroke(has ? (int) d : 0, 0x406EA8FF);
                name.setTextColor(has ? 0xFFFFFFFF : TXT);
                ic.setColorFilter(has ? 0xFFD8E5FF : ICON);
                v.animate().scaleX(has ? 1.015f : 1f).scaleY(has ? 1.015f : 1f)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .setDuration(150).start();
            });
            if (firstFocusable == null) firstFocusable = row;
        }
        card.addView(row);
        return row;
    }

    // ---------- controls ----------

    private void switchRow(LinearLayout card, int icon, String label,
                           String desc, String key, boolean def) {
        LinearLayout row = rowBase(card, icon, label, desc, true);
        FrameLayout sw = makeSwitch(prefs.getBoolean(key, def));
        row.addView(sw, new LinearLayout.LayoutParams(
                (int) (50 * d), (int) (30 * d)));
        row.setOnClickListener(v -> {
            boolean on = !prefs.getBoolean(key, def);
            prefs.edit().putBoolean(key, on).apply();
            animateSwitch(sw, on);
        });
    }

    private FrameLayout makeSwitch(boolean on) {
        FrameLayout sw = new FrameLayout(this);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(30 * d);
        track.setColor(on ? ACCENT : 0x2AFFFFFF);
        sw.setBackground(track);
        sw.setTag("switch");

        View knob = new View(this);
        GradientDrawable kb = new GradientDrawable();
        kb.setShape(GradientDrawable.OVAL);
        kb.setColor(0xFFFFFFFF);
        knob.setBackground(kb);
        knob.setTag("knob");
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(
                (int) (22 * d), (int) (22 * d), Gravity.CENTER_VERTICAL);
        kp.leftMargin = (int) (4 * d);
        knob.setTranslationX(on ? 20 * d : 0);
        sw.addView(knob, kp);
        return sw;
    }

    private void animateSwitch(View sw, boolean on) {
        View knob = sw.findViewWithTag("knob");
        if (knob != null)
            knob.animate().translationX(on ? 20 * d : 0)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        GradientDrawable track = (GradientDrawable) sw.getBackground();
        ValueAnimator c = ValueAnimator.ofArgb(
                on ? 0x2AFFFFFF : ACCENT, on ? ACCENT : 0x2AFFFFFF);
        c.setDuration(180);
        c.addUpdateListener(a -> track.setColor((int) a.getAnimatedValue()));
        c.start();
    }

    private void segmentRow(LinearLayout card, int icon, String label,
                            String desc, String[] options, int cur,
                            java.util.function.IntConsumer pick) {
        LinearLayout row = rowBase(card, icon, label, desc, true);
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setGravity(Gravity.CENTER_VERTICAL);
        seg.setPadding((int) (3 * d), (int) (3 * d),
                (int) (3 * d), (int) (3 * d));
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(0x10FFFFFF);
        sb.setCornerRadius(18 * d);
        seg.setBackground(sb);

        TextView[] cells = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            TextView t = new TextView(this);
            t.setText(options[i]);
            t.setTextSize(12.5f);
            t.setGravity(Gravity.CENTER);
            t.setPadding((int) (12 * d), (int) (6 * d),
                    (int) (12 * d), (int) (6 * d));
            GradientDrawable tb = new GradientDrawable();
            tb.setCornerRadius(15 * d);
            t.setBackground(tb);
            seg.addView(t);
            cells[i] = t;
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) t.getLayoutParams();
            if (i > 0) lp.leftMargin = (int) (2 * d);
        }
        final int[] sel = {cur};
        Runnable style = () -> {
            for (int i = 0; i < cells.length; i++) {
                GradientDrawable tb = (GradientDrawable) cells[i].getBackground();
                tb.setColor(i == sel[0] ? ACCENT : 0x00000000);
                cells[i].setTextColor(i == sel[0] ? 0xFF0B1220 : DIM);
                cells[i].setTypeface(null, i == sel[0]
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
            }
        };
        style.run();
        row.addView(seg);

        row.setOnClickListener(v -> {
            sel[0] = (sel[0] + 1) % options.length;
            pick.accept(sel[0]);
            style.run();
        });
        // D-Pad left/right slides the segment directly — feels physical
        row.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && sel[0] > 0) {
                sel[0]--; pick.accept(sel[0]); style.run();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT
                    && sel[0] < options.length - 1) {
                sel[0]++; pick.accept(sel[0]); style.run();
                return true;
            }
            return false;
        });
    }

    private void actionRow(LinearLayout card, int icon, String label,
                           String desc, String actionLabel) {
        LinearLayout row = rowBase(card, icon, label, desc, true);
        TextView pill = new TextView(this);
        pill.setText(actionLabel);
        pill.setTextSize(13f);
        pill.setTextColor(ACCENT);
        pill.setPadding((int) (18 * d), (int) (8 * d),
                (int) (18 * d), (int) (8 * d));
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(0x226EA8FF);
        pb.setCornerRadius(20 * d);
        pill.setBackground(pb);
        row.addView(pill);

        row.setOnClickListener(v -> {
            pill.setText("Running…");
            pill.setAlpha(0.55f);
            row.setClickable(false);
            HeroStrip.sweep(this, freed -> {
                pill.setText(actionLabel);
                pill.setAlpha(1f);
                row.setClickable(true);
                Toast.makeText(this,
                        freed > 0 ? "Freed " + freed + " MB"
                                : "Memory already optimized",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void infoRow(LinearLayout card, int icon,
                         String label, String value) {
        LinearLayout row = rowBase(card, icon, label, null, false);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13.5f);
        v.setTextColor(DIM);
        row.addView(v);
    }
}
