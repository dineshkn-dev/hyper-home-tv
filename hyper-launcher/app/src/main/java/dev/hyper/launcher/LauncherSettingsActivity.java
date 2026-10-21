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
 * In-launcher settings — iOS-style grouped list. Flat dark group
 * cards with inset hairline separators, tinted icon tiles on the
 * left, iOS-green toggle switches, a segmented control for the
 * companion mode, and values/actions on the right edge.
 * "Mouse chase" lives inside the Companion group and greys out
 * when the cat itself is Off. All framework views.
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
    private static final int ACCENT = 0xFF6EA8FF;
    private static final int GREEN  = 0xFF30D158;    // iOS switch-on green

    private SharedPreferences prefs;
    private float d;
    private View firstFocusable;
    private LinearLayout mouseRow;   // companion sub-setting
    private FrameLayout mouseSw;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        sv.setPadding((int) (64 * d), (int) (40 * d),
                (int) (64 * d), (int) (40 * d));
        setContentView(sv);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        sv.addView(col, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // ---------- header — iOS large title ----------
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(34f);
        title.setTextColor(TXT);
        col.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Hyper Launcher");
        sub.setTextSize(13f);
        sub.setTextColor(DIM);
        sub.setPadding((int) (2 * d), (int) (2 * d), 0, 0);
        col.addView(sub);

        // ---------- Companion ----------
        LinearLayout gCat = section(col, "Companion");
        segmentRow(gCat, R.drawable.ic_paw, "Cat",
                "Full plays, Ambient rests", 0xFF8A7CFF,
                new String[]{"Full", "Ambient", "Off"},
                prefs.getInt(K_COMPANION, 0),
                i -> prefs.edit().putInt(K_COMPANION, i).apply());
        mouseRow = switchRow(gCat, R.drawable.ic_mouse, "Mouse chase",
                "Jerry pops out while the cat sleeps", 0xFF5BC8AF,
                K_MOUSE, true, true);
        updateMouseEnabled();

        // ---------- Personalize ----------
        LinearLayout g1 = section(col, "Personalize");
        switchRow(g1, R.drawable.ic_gauge, "Hero strip",
                "RAM · CPU · storage · temperature", 0xFF6EA8FF,
                K_HERO, true, false);
        switchRow(g1, R.drawable.ic_clock, "Clock seconds",
                "Show seconds in the top-bar clock", 0xFFF0B84A,
                K_SECONDS, true, false);
        switchRow(g1, R.drawable.ic_tag, "Always show app names",
                "Labels under every tile, or only on focus", 0xFFE86A92,
                K_LABELS, false, false);

        // ---------- Status bar ----------
        LinearLayout g2 = section(col, "Status bar");
        switchRow(g2, R.drawable.ic_cloud, "Weather chip",
                "Weather + AQI in the top bar", 0xFF5BC8AF,
                K_WEATHER, true, false);
        switchRow(g2, R.drawable.ic_wifi, "Wi-Fi chip",
                "Network status in the top bar", 0xFF6EA8FF,
                K_WIFI, true, false);

        // ---------- System ----------
        LinearLayout g3 = section(col, "System");
        actionRow(g3, R.drawable.ic_bolt, "Optimize memory",
                "Clear cached background apps", 0xFFF0B84A);
        infoRow(g3, R.drawable.ic_info, "About this TV", 0xFF7B86A2,
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
        hlp.topMargin = (int) (16 * d);
        col.addView(hint, hlp);

        // staggered entrance — sections rise and fade
        int i = 0;
        for (int k = 0; k < col.getChildCount(); k++) {
            View v = col.getChildAt(k);
            v.setAlpha(0f);
            v.setTranslationY(18 * d);
            v.animate().alpha(1f).translationY(0)
                    .setStartDelay(60 + 45L * i++)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
        if (firstFocusable != null)
            firstFocusable.post(() -> firstFocusable.requestFocus());
    }

    /** Mouse chase only makes sense while the cat exists. */
    private void updateMouseEnabled() {
        if (mouseRow == null) return;
        boolean catOn = prefs.getInt(K_COMPANION, 0) != 2;
        float a = catOn ? 1f : 0.38f;
        mouseRow.setAlpha(a);
        mouseRow.setFocusable(catOn);
        mouseRow.setClickable(catOn);
    }

    // ---------- group card ----------

    private LinearLayout section(LinearLayout col, String title) {
        TextView h = new TextView(this);
        h.setText(title.toUpperCase(Locale.US));
        h.setTextSize(12f);
        h.setTextColor(DIM);
        h.setLetterSpacing(0.12f);
        h.setPadding((int) (18 * d), 0, 0, (int) (8 * d));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = (int) (26 * d);
        col.addView(h, hp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF15171F);                    // iOS grouped-cell grey
        bg.setCornerRadius(16 * d);
        card.setBackground(bg);
        col.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void hairline(LinearLayout card) {
        View line = new View(this);
        line.setBackgroundColor(0x14FFFFFF);        // iOS separator
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = (int) (74 * d);             // align with row labels
        lp.rightMargin = (int) (18 * d);
        card.addView(line, lp);
    }

    // ---------- row scaffold ----------

    /** Icon tile + label/description; returns the row for the caller
     *  to append a right-edge control. */
    private LinearLayout rowBase(LinearLayout card, int icon,
                                 String label, String desc, int tint,
                                 boolean focusable) {
        if (card.getChildCount() > 0) hairline(card);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (18 * d), (int) (14 * d),
                (int) (16 * d), (int) (14 * d));
        row.setFocusable(focusable);
        row.setClickable(focusable);
        row.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(14 * d);
        bg.setColor(0x00000000);
        row.setBackground(bg);

        // tinted icon tile — iOS coloured setting icons
        FrameLayout tile = new FrameLayout(this);
        GradientDrawable tb = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x3D000000 | (tint & 0x00FFFFFF),
                        0x22000000 | (tint & 0x00FFFFFF)});
        tb.setCornerRadius(11 * d);
        tile.setBackground(tb);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(tint);
        ic.setTag("icon");
        tile.addView(ic, new FrameLayout.LayoutParams(
                (int) (19 * d), (int) (19 * d), Gravity.CENTER));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                (int) (38 * d), (int) (38 * d));
        tp.setMarginEnd((int) (16 * d));
        row.addView(tile, tp);

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
            sub.setPadding(0, (int) (2 * d), 0, 0);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (focusable) {
            row.setOnFocusChangeListener((v, has) -> {
                bg.setColor(has ? 0x1FFFFFFF : 0x00000000);
                name.setTextColor(has ? 0xFFFFFFFF : TXT);
                v.animate().scaleX(has ? 1.012f : 1f).scaleY(has ? 1.012f : 1f)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .setDuration(140).start();
            });
            if (firstFocusable == null) firstFocusable = row;
        }
        card.addView(row);
        return row;
    }

    // ---------- controls ----------

    /** @param refMouse when true this is the companion sub-row we can disable */
    private LinearLayout switchRow(LinearLayout card, int icon, String label,
                                   String desc, int tint, String key,
                                   boolean def, boolean refMouse) {
        LinearLayout row = rowBase(card, icon, label, desc, tint, true);
        FrameLayout sw = makeSwitch(prefs.getBoolean(key, def));
        row.addView(sw, new LinearLayout.LayoutParams(
                (int) (50 * d), (int) (30 * d)));
        row.setOnClickListener(v -> {
            boolean on = !prefs.getBoolean(key, def);
            prefs.edit().putBoolean(key, on).apply();
            animateSwitch(sw, on);
        });
        if (refMouse) mouseSw = sw;
        return row;
    }

    private FrameLayout makeSwitch(boolean on) {
        FrameLayout sw = new FrameLayout(this);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(30 * d);
        track.setColor(on ? GREEN : 0x33FFFFFF);
        sw.setBackground(track);

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
                on ? 0x33FFFFFF : GREEN, on ? GREEN : 0x33FFFFFF);
        c.setDuration(180);
        c.addUpdateListener(a -> track.setColor((int) a.getAnimatedValue()));
        c.start();
    }

    private void segmentRow(LinearLayout card, int icon, String label,
                            String desc, int tint, String[] options, int cur,
                            java.util.function.IntConsumer pick) {
        LinearLayout row = rowBase(card, icon, label, desc, tint, true);
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setGravity(Gravity.CENTER_VERTICAL);
        seg.setPadding((int) (3 * d), (int) (3 * d),
                (int) (3 * d), (int) (3 * d));
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(0x26FFFFFF);                    // iOS segment track
        sb.setCornerRadius(17 * d);
        seg.setBackground(sb);

        TextView[] cells = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            TextView t = new TextView(this);
            t.setText(options[i]);
            t.setTextSize(12.5f);
            t.setGravity(Gravity.CENTER);
            t.setPadding((int) (13 * d), (int) (6 * d),
                    (int) (13 * d), (int) (6 * d));
            GradientDrawable tb = new GradientDrawable();
            tb.setCornerRadius(14 * d);
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
                // iOS: light pill on dark track
                tb.setColor(i == sel[0] ? 0xFFE8EDF8 : 0x00000000);
                cells[i].setTextColor(i == sel[0] ? 0xFF14181F : DIM);
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
            updateMouseEnabled();
        });
        row.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && sel[0] > 0) {
                sel[0]--; pick.accept(sel[0]); style.run(); updateMouseEnabled();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT
                    && sel[0] < options.length - 1) {
                sel[0]++; pick.accept(sel[0]); style.run(); updateMouseEnabled();
                return true;
            }
            return false;
        });
    }

    private void actionRow(LinearLayout card, int icon, String label,
                           String desc, int tint) {
        LinearLayout row = rowBase(card, icon, label, desc, tint, true);
        TextView pill = new TextView(this);
        pill.setText("Optimize");
        pill.setTextSize(13.5f);
        pill.setTextColor(ACCENT);
        row.addView(pill);

        row.setOnClickListener(v -> {
            pill.setText("Running…");
            pill.setAlpha(0.55f);
            row.setClickable(false);
            HeroStrip.sweep(this, freed -> {
                pill.setText("Optimize");
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
                         String label, int tint, String value) {
        LinearLayout row = rowBase(card, icon, label, null, tint, false);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13.5f);
        v.setTextColor(DIM);
        row.addView(v);
    }
}
