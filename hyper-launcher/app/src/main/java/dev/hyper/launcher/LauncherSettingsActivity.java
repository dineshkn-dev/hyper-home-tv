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
 * In-launcher settings — a two-column layout. Left: grouped glass
 * cards of tinted icon rows with real toggle switches and an inline
 * segment control. Right: a live miniature of the home screen that
 * reacts to every toggle as you press it, plus the System card.
 * All framework views.
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

    private SharedPreferences prefs;
    private float d;
    private View firstFocusable;

    // live-preview pieces
    private View pvHero, pvWeather, pvWifi, pvMouse, pvHole;
    private final java.util.List<View> pvLabelBars =
            new java.util.ArrayList<>();
    private BuddyView pvCat;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setClipChildren(false);
        root.setPadding((int) (64 * d), (int) (40 * d),
                (int) (64 * d), (int) (32 * d));
        setContentView(root);

        // ================= left — controls =================
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.05f);
        svp.setMarginEnd((int) (28 * d));
        root.addView(sv, svp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        sv.addView(col, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
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

        LinearLayout g1 = section(col, "Personalize");
        segmentRow(g1, R.drawable.ic_paw, "Companion",
                "The little cat on the bottom edge", 0xFF8A7CFF,
                new String[]{"Full", "Ambient", "Off"},
                prefs.getInt(K_COMPANION, 0),
                i -> prefs.edit().putInt(K_COMPANION, i).apply());
        switchRow(g1, R.drawable.ic_mouse, "Mouse chase",
                "Jerry pops out while the cat sleeps", 0xFF5BC8AF,
                K_MOUSE, true);
        switchRow(g1, R.drawable.ic_gauge, "Hero strip",
                "RAM · CPU · storage · temperature", 0xFF6EA8FF,
                K_HERO, true);
        switchRow(g1, R.drawable.ic_clock, "Clock seconds",
                "Show seconds in the top-bar clock", 0xFFF0B84A,
                K_SECONDS, true);
        switchRow(g1, R.drawable.ic_tag, "Always show app names",
                "Labels under every tile, or only on focus", 0xFFE86A92,
                K_LABELS, false);

        LinearLayout g2 = section(col, "Status bar");
        switchRow(g2, R.drawable.ic_cloud, "Weather chip",
                "Weather + AQI in the top bar", 0xFF5BC8AF,
                K_WEATHER, true);
        switchRow(g2, R.drawable.ic_wifi, "Wi-Fi chip",
                "Network status in the top bar", 0xFF6EA8FF,
                K_WIFI, true);

        TextView hint = new TextView(this);
        hint.setText("OK toggles · D-Pad ◀ ▶ adjusts · Back exits");
        hint.setTextSize(12f);
        hint.setTextColor(DIM);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = (int) (18 * d);
        col.addView(hint, hlp);

        // ================= right — live preview + system =================
        ScrollView rsv = new ScrollView(this);
        rsv.setVerticalScrollBarEnabled(false);
        rsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(rsv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 0.95f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setClipChildren(false);
        rsv.addView(right, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView pvH = new TextView(this);
        pvH.setText("LIVE PREVIEW");
        pvH.setTextSize(12f);
        pvH.setTextColor(DIM);
        pvH.setLetterSpacing(0.14f);
        pvH.setPadding((int) (12 * d), 0, 0, (int) (10 * d));
        LinearLayout.LayoutParams pvHp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pvHp.topMargin = (int) (36 * d);
        right.addView(pvH, pvHp);

        right.addView(previewMock(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (225 * d)));
        right.addView(systemCard());

        // entrance — columns glide in from opposite sides
        animateIn(col, -30 * d, 0);
        animateIn(right, 30 * d, 90);

        if (firstFocusable != null)
            firstFocusable.post(() -> firstFocusable.requestFocus());
    }

    private void animateIn(View v, float dx, long delay) {
        v.setAlpha(0f);
        v.setTranslationX(dx);
        v.animate().alpha(1f).translationX(0)
                .setStartDelay(delay).setDuration(340)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    // ================= live preview mock =================

    private FrameLayout previewMock() {
        FrameLayout mock = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B0D14, 0xFF05060B});
        bg.setCornerRadius(20 * d);
        bg.setStroke((int) d, 0x20FFFFFF);
        mock.setBackground(bg);
        mock.setClipChildren(false);

        int pad = (int) (14 * d);

        // mini hero strip — one slim pill
        pvHero = new View(this);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0x1FFFFFFF);
        hb.setCornerRadius(8 * d);
        pvHero.setBackground(hb);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (int) (16 * d));
        hp.topMargin = pad;
        hp.leftMargin = pad; hp.rightMargin = pad;
        mock.addView(pvHero, hp);

        // mini top-bar chips
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        pvWeather = miniChip(chips, 0xFF5BC8AF);
        pvWifi = miniChip(chips, 0xFF6EA8FF);
        FrameLayout.LayoutParams chp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        chp.topMargin = pad + (int) (24 * d);
        chp.rightMargin = pad;
        mock.addView(chips, chp);

        // mini tile grid — two rows of glass squares + label bars
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER_HORIZONTAL);
        for (int r = 0; r < 2; r++) {
            LinearLayout rowTiles = new LinearLayout(this);
            rowTiles.setGravity(Gravity.CENTER_HORIZONTAL);
            for (int i = 0; i < 5; i++) {
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                View t = new View(this);
                GradientDrawable tb = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{0x2EFFFFFF, 0x10FFFFFF});
                tb.setCornerRadius(6 * d);
                tb.setStroke((int) d, 0x22FFFFFF);
                t.setBackground(tb);
                cell.addView(t, new LinearLayout.LayoutParams(
                        (int) (34 * d), (int) (34 * d)));
                if (r == 0 && i < 4) {
                    View lab = new View(this);
                    pvLabelBars.add(lab);
                    lab.setBackgroundColor(0x22FFFFFF);
                    LinearLayout.LayoutParams ll =
                            new LinearLayout.LayoutParams(
                                    (int) (26 * d), (int) (3 * d));
                    ll.topMargin = (int) (5 * d);
                    cell.addView(lab, ll);
                }
                rowTiles.addView(cell);
                LinearLayout.LayoutParams cl =
                        (LinearLayout.LayoutParams) cell.getLayoutParams();
                cl.leftMargin = cl.rightMargin = (int) (7 * d);
            }
            grid.addView(rowTiles);
            LinearLayout.LayoutParams rp =
                    (LinearLayout.LayoutParams) rowTiles.getLayoutParams();
            if (r == 1) rp.topMargin = (int) (10 * d);
        }
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        gp.topMargin = (int) (6 * d);
        mock.addView(grid, gp);

        // mini ground strip
        View ground = new View(this);
        GradientDrawable gb = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF3E8C60, 0xFF2E6B4C});
        ground.setBackground(gb);
        FrameLayout.LayoutParams gr = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (int) (14 * d),
                Gravity.BOTTOM);
        mock.addView(ground, gr);

        // mouse hole + mouse dot
        pvHole = new View(this);
        GradientDrawable hole = new GradientDrawable();
        hole.setShape(GradientDrawable.OVAL);
        hole.setColor(0xFF04060C);
        pvHole.setBackground(hole);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                (int) (18 * d), (int) (9 * d), Gravity.BOTTOM | Gravity.LEFT);
        hlp.leftMargin = (int) (70 * d);
        hlp.bottomMargin = (int) (12 * d);
        mock.addView(pvHole, hlp);

        pvMouse = new View(this);
        GradientDrawable md = new GradientDrawable();
        md.setShape(GradientDrawable.OVAL);
        md.setColor(0xFFB98A63);
        pvMouse.setBackground(md);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(
                (int) (7 * d), (int) (7 * d), Gravity.BOTTOM | Gravity.LEFT);
        mp.leftMargin = (int) (76 * d);
        mp.bottomMargin = (int) (17 * d);
        mock.addView(pvMouse, mp);

        // the real cat, scaled down, living on the mini ground
        pvCat = new BuddyView(this);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                (int) (130 * d), (int) (78 * d),
                Gravity.BOTTOM | Gravity.RIGHT);
        cp.rightMargin = (int) (60 * d);
        cp.bottomMargin = (int) (8 * d);
        mock.addView(pvCat, cp);

        refreshPreview();
        return mock;
    }

    private View miniChip(LinearLayout parent, int tint) {
        View c = new View(this);
        GradientDrawable b = new GradientDrawable();
        b.setColor(0x33000000 | (tint & 0x00FFFFFF));
        b.setCornerRadius(7 * d);
        c.setBackground(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (24 * d), (int) (13 * d));
        lp.leftMargin = (int) (5 * d);
        parent.addView(c, lp);
        return c;
    }

    /** Push current prefs into the live preview. */
    private void refreshPreview() {
        if (pvHero == null) return;
        pvHero.setVisibility(prefs.getBoolean(K_HERO, true)
                ? View.VISIBLE : View.GONE);
        pvWeather.setVisibility(prefs.getBoolean(K_WEATHER, true)
                ? View.VISIBLE : View.GONE);
        pvWifi.setVisibility(prefs.getBoolean(K_WIFI, true)
                ? View.VISIBLE : View.GONE);
        for (View lab : pvLabelBars)
            lab.setVisibility(prefs.getBoolean(K_LABELS, false)
                    ? View.VISIBLE : View.GONE);
        boolean mouse = prefs.getBoolean(K_MOUSE, true);
        pvMouse.setVisibility(mouse ? View.VISIBLE : View.GONE);
        pvHole.setVisibility(mouse ? View.VISIBLE : View.GONE);
        int mode = prefs.getInt(K_COMPANION, 0);
        pvCat.setMode(mode);
        pvCat.setVisibility(mode == 2 ? View.GONE : View.VISIBLE);
        pvCat.animate().alpha(mode == 1 ? 0.45f : 1f)
                .setDuration(200).start();
    }

    // ================= cards & rows =================

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
        lp.leftMargin = (int) (78 * d);
        lp.rightMargin = (int) (20 * d);
        card.addView(line, lp);
    }

    private LinearLayout rowBase(LinearLayout card, int icon,
                                 String label, String desc, int tint,
                                 boolean focusable) {
        if (card.getChildCount() > 0) hairline(card);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (18 * d), (int) (15 * d),
                (int) (18 * d), (int) (15 * d));
        row.setFocusable(focusable);
        row.setClickable(focusable);
        row.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * d);
        bg.setColor(0x00000000);
        row.setBackground(bg);

        // tinted icon tile
        FrameLayout tile = new FrameLayout(this);
        GradientDrawable tb = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x40000000 | (tint & 0x00FFFFFF),
                        0x1A000000 | (tint & 0x00FFFFFF)});
        tb.setCornerRadius(12 * d);
        tile.setBackground(tb);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(0xCC000000 | (tint & 0x00FFFFFF));
        ic.setTag("icon");
        tile.addView(ic, new FrameLayout.LayoutParams(
                (int) (20 * d), (int) (20 * d), Gravity.CENTER));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                (int) (40 * d), (int) (40 * d));
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
                ic.setColorFilter(has ? tint
                        : 0xCC000000 | (tint & 0x00FFFFFF));
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
                           String desc, int tint, String key, boolean def) {
        LinearLayout row = rowBase(card, icon, label, desc, tint, true);
        FrameLayout sw = makeSwitch(prefs.getBoolean(key, def));
        row.addView(sw, new LinearLayout.LayoutParams(
                (int) (50 * d), (int) (30 * d)));
        row.setOnClickListener(v -> {
            boolean on = !prefs.getBoolean(key, def);
            prefs.edit().putBoolean(key, on).apply();
            animateSwitch(sw, on);
            refreshPreview();
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
                            String desc, int tint, String[] options, int cur,
                            java.util.function.IntConsumer pick) {
        LinearLayout row = rowBase(card, icon, label, desc, tint, true);
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
            refreshPreview();
        });
        row.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && sel[0] > 0) {
                sel[0]--; pick.accept(sel[0]); style.run(); refreshPreview();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT
                    && sel[0] < options.length - 1) {
                sel[0]++; pick.accept(sel[0]); style.run(); refreshPreview();
                return true;
            }
            return false;
        });
    }

    // ---------- system card ----------

    private LinearLayout systemCard() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        TextView h = new TextView(this);
        h.setText("SYSTEM");
        h.setTextSize(12f);
        h.setTextColor(DIM);
        h.setLetterSpacing(0.14f);
        h.setPadding((int) (12 * d), 0, 0, (int) (10 * d));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = (int) (16 * d);
        wrap.addView(h, hp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x14FFFFFF, 0x09FFFFFF});
        bg.setCornerRadius(22 * d);
        bg.setStroke((int) d, 0x18FFFFFF);
        card.setBackground(bg);
        wrap.addView(card);

        LinearLayout row = rowBase(card, R.drawable.ic_bolt,
                "Optimize memory", "Clear cached background apps",
                0xFFF0B84A, true);
        TextView pill = new TextView(this);
        pill.setText("Optimize");
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
                pill.setText("Optimize");
                pill.setAlpha(1f);
                row.setClickable(true);
                Toast.makeText(this,
                        freed > 0 ? "Freed " + freed + " MB"
                                : "Memory already optimized",
                        Toast.LENGTH_SHORT).show();
            });
        });

        LinearLayout about = rowBase(card, R.drawable.ic_info,
                "About this TV", null, 0xFF7B86A2, false);
        TextView v = new TextView(this);
        v.setText(Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        v.setTextSize(13.5f);
        v.setTextColor(DIM);
        about.addView(v);
        return wrap;
    }
}
