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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-launcher settings — macOS System Settings layout. A left
 * sidebar of category items keeps its selection highlighted while
 * the right pane shows that category's iOS-style group cards.
 * Focusing a sidebar item populates the pane live; D-Pad right or
 * OK hops focus into the pane. All framework views.
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
    static final String K_SORT = "sort";             // 0 a-z, 1 most used, 2 manual

    private static final int TXT    = 0xFFEDF1FA;
    private static final int DIM    = 0xFF7B86A2;
    private static final int ACCENT = 0xFF6EA8FF;
    private static final int GREEN  = 0xFF30D158;    // iOS switch-on green

    private SharedPreferences prefs;
    private float d;
    private LinearLayout pane;
    private final List<View> railItems = new ArrayList<>();
    private final List<Cat> cats = new ArrayList<>();
    private Cat active;
    private LinearLayout mouseRow;

    private interface OnPick { void set(int i); }

    private static final class Cat {
        final int icon; final String label; final int tint;
        Cat(int icon, String label, int tint) {
            this.icon = icon; this.label = label; this.tint = tint;
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;
        buildCats();

        FrameLayout frame = new FrameLayout(this);
        setContentView(frame);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setClipChildren(false);
        root.setPadding((int) (56 * d), (int) (40 * d),
                (int) (56 * d), (int) (40 * d));
        frame.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // ================= sidebar =================
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setClipChildren(false);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                (int) (300 * d), LinearLayout.LayoutParams.MATCH_PARENT);
        rp.setMarginEnd((int) (36 * d));
        root.addView(rail, rp);

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(28f);
        title.setTextColor(TXT);
        rail.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Hyper Launcher");
        sub.setTextSize(12.5f);
        sub.setTextColor(DIM);
        sub.setPadding(0, (int) (3 * d), 0, (int) (30 * d));
        rail.addView(sub);

        // sticky header stays; only the category list can scroll
        ScrollView rsv = new ScrollView(this);
        rsv.setVerticalScrollBarEnabled(false);
        rsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rsv.setClipToPadding(false);
        LinearLayout.LayoutParams rsvp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        rail.addView(rsv, rsvp);
        LinearLayout railList = new LinearLayout(this);
        railList.setOrientation(LinearLayout.VERTICAL);
        railList.setClipChildren(false);
        rsv.addView(railList);
        for (Cat c : cats) railList.addView(railItem(c));

        TextView hint = new TextView(this);
        hint.setText("OK / ▶ enters pane · ◀ returns · Back exits");
        hint.setTextSize(11.5f);
        hint.setTextColor(DIM);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = (int) (20 * d);
        rail.addView(hint, hlp);

        // ================= right pane =================
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        root.addView(sv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setClipChildren(false);
        sv.addView(pane, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // quiet version tag, bottom-right — the only place it shows
        TextView ver = new TextView(this);
        ver.setText("v" + versionName());
        ver.setTextSize(11.5f);
        ver.setTextColor(0xFF4A5468);
        ver.setLetterSpacing(0.12f);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.RIGHT);
        vp.rightMargin = (int) (30 * d);
        vp.bottomMargin = (int) (22 * d);
        frame.addView(ver, vp);

        // sidebar entrance, then focus the first item
        int i = 0;
        for (View v : railItems) {
            v.setAlpha(0f);
            v.setTranslationX(-20 * d);
            v.animate().alpha(1f).translationX(0)
                    .setStartDelay(60 + 40L * i++).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
        railItems.get(0).post(() -> railItems.get(0).requestFocus());
    }

    private void buildCats() {
        cats.add(new Cat(R.drawable.ic_paw, "Companion", 0xFF8A7CFF));
        cats.add(new Cat(R.drawable.ic_tag, "Personalize", 0xFFE86A92));
        cats.add(new Cat(R.drawable.ic_wifi, "Status bar", 0xFF6EA8FF));
        cats.add(new Cat(R.drawable.ic_settings, "System", 0xFF7B86A2));
    }

    // ---------- sidebar ----------

    private View railItem(Cat c) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.HORIZONTAL);
        it.setGravity(Gravity.CENTER_VERTICAL);
        it.setPadding((int) (14 * d), (int) (10 * d),
                (int) (14 * d), (int) (10 * d));
        it.setFocusable(true);
        it.setClickable(true);
        it.setTag(c);
        it.setId(View.generateViewId());

        FrameLayout tile = new FrameLayout(this);
        GradientDrawable tb = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x3D000000 | (c.tint & 0x00FFFFFF),
                        0x22000000 | (c.tint & 0x00FFFFFF)});
        tb.setCornerRadius(8 * d);
        tile.setBackground(tb);
        tile.setTag("tile");
        ImageView ic = new ImageView(this);
        ic.setImageResource(c.icon);
        ic.setColorFilter(c.tint);
        ic.setTag("icon");
        tile.addView(ic, new FrameLayout.LayoutParams(
                (int) (15 * d), (int) (15 * d), Gravity.CENTER));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                (int) (30 * d), (int) (30 * d));
        tp.setMarginEnd((int) (13 * d));
        it.addView(tile, tp);

        TextView t = new TextView(this);
        t.setText(c.label);
        t.setTextSize(15f);
        t.setTag("label");
        it.addView(t);

        it.setOnFocusChangeListener((v, has) -> {
            if (has) populate(c);
            styleRailItem(v, c, has);
        });
        // OK or D-Pad right hops into the pane
        it.setOnClickListener(v -> focusPane());
        it.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN
                    && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focusPane();
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (4 * d);
        it.setLayoutParams(lp);
        railItems.add(it);
        styleRailItem(it, c, false);
        return it;
    }

    /** macOS sidebar: focused item gets the strong pill, the active
     *  category keeps a subtle pill while you're in the pane. */
    private void styleRailItem(View v, Cat c, boolean focused) {
        boolean isActive = c == active;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * d);
        bg.setColor(focused ? 0x2EFFFFFF : isActive ? 0x14FFFFFF
                : 0x00000000);
        v.setBackground(bg);
        int fg = focused ? 0xFFFFFFFF : isActive ? TXT : DIM;
        ImageView ic = v.findViewWithTag("icon");
        TextView t = v.findViewWithTag("label");
        if (ic != null) ic.setColorFilter(focused || isActive
                ? c.tint : dim(c.tint));
        if (t != null) t.setTextColor(fg);
    }

    private static int dim(int c) {
        return (c & 0x00FFFFFF) | 0x99FFFFFF;
    }

    private void styleRail() {
        for (View v : railItems)
            styleRailItem(v, (Cat) v.getTag(), v.hasFocus());
    }

    private void focusPane() {
        for (int i = 0; i < pane.getChildCount(); i++) {
            View row = pane.getChildAt(i);
            if (row.isFocusable()) { row.requestFocus(); return; }
            if (row instanceof LinearLayout) {
                LinearLayout g = (LinearLayout) row;
                for (int k = 0; k < g.getChildCount(); k++)
                    if (g.getChildAt(k).isFocusable()) {
                        g.getChildAt(k).requestFocus();
                        return;
                    }
            }
        }
    }

    // ---------- pane ----------

    private void populate(Cat c) {
        if (c == active) return;
        active = c;
        styleRail();
        pane.removeAllViews();
        LinearLayout card = card();

        if (c.label.equals("Companion")) {
            segmentRow(card, R.drawable.ic_paw, "Cat",
                    "Full plays, Ambient rests", 0xFF8A7CFF,
                    new String[]{"Full", "Ambient", "Off"},
                    prefs.getInt(K_COMPANION, 0),
                    i -> prefs.edit().putInt(K_COMPANION, i).apply());
            mouseRow = switchRow(card, R.drawable.ic_mouse, "Mouse chase",
                    "Jerry pops out while the cat sleeps", 0xFF5BC8AF,
                    K_MOUSE, true, true);
            updateMouseEnabled();
        } else if (c.label.equals("Personalize")) {
            segmentRow(card, R.drawable.ic_sort, "App order",
                    "How tiles are arranged", 0xFFE86A92,
                    new String[]{"A–Z", "Most used", "Manual"},
                    prefs.getInt(K_SORT, 2),
                    i -> prefs.edit().putInt(K_SORT, i).apply());
            switchRow(card, R.drawable.ic_gauge, "Hero strip",
                    "RAM · CPU · storage · temperature", 0xFF6EA8FF,
                    K_HERO, true, false);
            switchRow(card, R.drawable.ic_clock, "Clock seconds",
                    "Show seconds in the top-bar clock", 0xFFF0B84A,
                    K_SECONDS, true, false);
            switchRow(card, R.drawable.ic_tag, "Always show app names",
                    "Labels under every tile, or only on focus",
                    0xFFE86A92, K_LABELS, false, false);
        } else if (c.label.equals("Status bar")) {
            switchRow(card, R.drawable.ic_cloud, "Weather chip",
                    "Weather + AQI in the top bar", 0xFF5BC8AF,
                    K_WEATHER, true, false);
            switchRow(card, R.drawable.ic_wifi, "Wi-Fi chip",
                    "Network status in the top bar", 0xFF6EA8FF,
                    K_WIFI, true, false);
        } else {
            actionRow(card, R.drawable.ic_bolt, "Optimize memory",
                    "Clear cached background apps", 0xFFF0B84A);
            infoRow(card, R.drawable.ic_info, "About this TV", 0xFF7B86A2,
                    Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        }
        pane.addView(card);

        // vertical focus stays inside the card — NO_ID is the default,
        // so loop the edge rows onto themselves instead; LEFT always
        // returns to the ACTIVE sidebar item, not the nearest one
        int railId = View.NO_ID;
        for (View v : railItems)
            if (v.getTag() == c) railId = v.getId();
        View first = null, last = null;
        for (int i = 0; i < card.getChildCount(); i++) {
            View v = card.getChildAt(i);
            if (!v.isFocusable()) continue;
            if (first == null) first = v;
            last = v;
            v.setNextFocusLeftId(railId);
        }
        if (first != null) {
            if (first.getId() == View.NO_ID) first.setId(View.generateViewId());
            first.setNextFocusUpId(first.getId());
        }
        if (last != null) {
            if (last.getId() == View.NO_ID) last.setId(View.generateViewId());
            last.setNextFocusDownId(last.getId());
        }

        // rows slide in staggered
        for (int i = 0; i < card.getChildCount(); i++) {
            View v = card.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationX(24 * d);
            v.animate().alpha(1f).translationX(0)
                    .setStartDelay(50 + 45L * i).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    private String versionName() {
        try {
            return getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { return "1.0"; }
    }

    /** Mouse chase only makes sense while the cat exists. */
    private void updateMouseEnabled() {
        if (mouseRow == null) return;
        boolean catOn = prefs.getInt(K_COMPANION, 0) != 2;
        mouseRow.setAlpha(catOn ? 1f : 0.38f);
        mouseRow.setFocusable(catOn);
        mouseRow.setClickable(catOn);
    }

    // ---------- group card ----------

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF15171F);                    // iOS grouped-cell grey
        bg.setCornerRadius(16 * d);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (6 * d);
        card.setLayoutParams(lp);
        return card;
    }

    private void hairline(LinearLayout card) {
        View line = new View(this);
        line.setBackgroundColor(0x14FFFFFF);        // iOS separator
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = (int) (74 * d);
        lp.rightMargin = (int) (18 * d);
        card.addView(line, lp);
    }

    // ---------- row scaffold ----------

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
        }
        card.addView(row);
        return row;
    }

    // ---------- controls ----------

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
                            OnPick pick) {
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
            pick.set(sel[0]);
            style.run();
            updateMouseEnabled();
        });
        row.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && sel[0] > 0) {
                sel[0]--; pick.set(sel[0]); style.run(); updateMouseEnabled();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT
                    && sel[0] < options.length - 1) {
                sel[0]++; pick.set(sel[0]); style.run(); updateMouseEnabled();
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
