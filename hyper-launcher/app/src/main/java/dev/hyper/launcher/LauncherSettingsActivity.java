package dev.hyper.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LauncherSettingsActivity extends Activity {

    static final String PREF = "hyper";
    static final String K_CONTINUE = "continueWatching";
    static final String K_SECONDS = "clockSeconds";

    private static final int TXT = 0xFFF5F5F5;
    private static final int DIM = 0xFF9A9A9A;
    private static final int FAINT = 0xFF5A5A5A;
    private static final int ACCENT = 0xFFC8FF3D;
    private static final int LINE = 0xFF242424;

    private SharedPreferences prefs;
    private float d;
    private LinearLayout railList, paneContent;
    private ScrollView paneScroll;
    private TextView version;
    private final List<Rail> rails = new ArrayList<>();
    private Rail active;
    private int activeRailId = View.NO_ID;
    private View firstPaneRow;
    private int paneRows;

    private static final class Rail {
        final int id, icon, tint;
        final String label, subtitle;
        View row;
        Rail(int id, int icon, int tint, String label, String subtitle) {
            this.id = id;
            this.icon = icon;
            this.tint = tint;
            this.label = label;
            this.subtitle = subtitle;
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;
        setContentView(R.layout.activity_settings);
        railList = findViewById(R.id.railList);
        paneContent = findViewById(R.id.paneContent);
        paneScroll = findViewById(R.id.paneScroll);
        version = findViewById(R.id.version);

        version.setText("HYPER OS  ·  v" + versionName());
        updateDate();
        android.widget.TextClock clock = findViewById(R.id.clock);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("h:mm a");

        buildRails();
        selectRail(rails.get(0));
        railList.post(() -> {
            if (!rails.isEmpty()) rails.get(0).row.requestFocus();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        updateDate();
        if (active != null) {
            showCategory(active);
            if (active.row != null)
                active.row.post(active.row::requestFocus);
        }
    }

    private void updateDate() {
        TextView date = findViewById(R.id.date);
        if (date != null)
            date.setText(new java.text.SimpleDateFormat(
                    "EEEE, MMMM d", Locale.getDefault())
                    .format(new java.util.Date())
                    .toUpperCase(Locale.getDefault()));
    }

    private String versionName() {
        try {
            return getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { return "1.0"; }
    }

    // ---------- left rail ----------

    private void buildRails() {
        rails.add(new Rail(0, R.drawable.ic_bolt, ACCENT,
                "Launcher", "Home screen, clock, and performance."));
        rails.add(new Rail(1, R.drawable.ic_info, 0xFF7EE787,
                "About", "Launcher version and package."));
        for (Rail r : rails) railList.addView(buildRailRow(r));
        styleRail();
    }

    private View buildRailRow(Rail r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setId(View.generateViewId());
        row.setTag(r);
        r.row = row;

        View accent = new View(this);
        accent.setTag("accent");
        GradientDrawable ab = new GradientDrawable();
        ab.setColor(r.tint);
        ab.setCornerRadius(2 * d);
        accent.setBackground(ab);
        row.addView(accent, new LinearLayout.LayoutParams(
                (int) (3 * d), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding((int) (10 * d), (int) (5 * d),
                (int) (8 * d), (int) (5 * d));

        ImageView icon = new ImageView(this);
        icon.setImageResource(r.icon);
        icon.setTag("icon");
        content.addView(icon, new LinearLayout.LayoutParams(
                (int) (18 * d), (int) (18 * d)));

        TextView label = new TextView(this);
        label.setText(r.label);
        label.setTextSize(14f);
        label.setTag("label");
        label.setSingleLine(true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginStart((int) (12 * d));
        content.addView(label, llp);

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setTag("chevron");
        content.addView(chevron, new LinearLayout.LayoutParams(
                (int) (13 * d), (int) (13 * d)));

        row.addView(content, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnFocusChangeListener((v, has) -> {
            if (has) selectRail(r);
            styleRail();
        });
        row.setOnClickListener(v -> focusPane());
        row.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN
                    && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focusPane();
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (2 * d);
        row.setLayoutParams(lp);
        return row;
    }

    private void selectRail(Rail r) {
        if (r == null) return;
        boolean changed = active != r;
        active = r;
        activeRailId = r.row.getId();
        styleRail();
        if (changed) showCategory(r);
    }

    private void styleRail() {
        for (Rail r : rails) {
            if (r.row == null) continue;
            boolean selected = r == active;
            boolean focused = r.row.hasFocus();
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(8 * d);
            bg.setColor(focused ? 0x22FFFFFF
                    : selected ? 0x1C1C1C : 0x00000000);
            r.row.setBackground(bg);
            View accent = r.row.findViewWithTag("accent");
            if (accent != null) accent.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            ImageView icon = r.row.findViewWithTag("icon");
            TextView label = r.row.findViewWithTag("label");
            ImageView chevron = r.row.findViewWithTag("chevron");
            if (icon != null)
                icon.setColorFilter(selected || focused ? r.tint : dim(r.tint));
            if (label != null)
                label.setTextColor(selected || focused ? TXT : DIM);
            if (chevron != null)
                chevron.setColorFilter(selected || focused ? DIM : FAINT);
        }
    }

    private static int dim(int color) {
        return (color & 0x00FFFFFF) | 0x8A000000;
    }

    private void focusPane() {
        if (firstPaneRow != null) firstPaneRow.requestFocus();
    }

    // ---------- right pane ----------

    private void showCategory(Rail r) {
        paneContent.removeAllViews();
        paneRows = 0;
        firstPaneRow = null;
        paneContent.addView(header(r));

        if (r.id == 0) launcherPanel();
        else aboutPanel();
        paneScroll.post(() -> paneScroll.scrollTo(0, 0));
    }

    private View header(Rail r) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.TOP);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView crumb = new TextView(this);
        crumb.setText("SETTINGS  /  " + r.label.toUpperCase(Locale.US));
        crumb.setTextSize(9.5f);
        crumb.setLetterSpacing(0.18f);
        crumb.setTextColor(DIM);
        texts.addView(crumb);

        TextView title = new TextView(this);
        title.setText(r.label);
        title.setTextSize(30f);
        title.setTextColor(TXT);
        title.setPadding(0, (int) (8 * d), 0, 0);
        texts.addView(title);

        TextView sub = new TextView(this);
        sub.setText(r.subtitle);
        sub.setTextSize(12.5f);
        sub.setTextColor(DIM);
        sub.setPadding(0, (int) (6 * d), 0, 0);
        texts.addView(sub);

        h.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView icon = new ImageView(this);
        icon.setImageResource(r.icon);
        icon.setColorFilter(r.tint);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                (int) (24 * d), (int) (24 * d));
        ilp.setMarginStart((int) (16 * d));
        h.addView(icon, ilp);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(h);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * d));
        lp.topMargin = (int) (18 * d);
        lp.bottomMargin = (int) (4 * d);
        wrap.addView(line, lp);
        return wrap;
    }

    private void addDivider() {
        View line = new View(this);
        line.setBackgroundColor(LINE);
        paneContent.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * d)));
    }

    private LinearLayout baseRow(int iconRes, String title, String subtitle,
                                 int tint, boolean focusable) {
        if (paneRows++ > 0) addDivider();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(focusable);
        row.setClickable(focusable);
        row.setPadding((int) (4 * d), (int) (13 * d),
                (int) (6 * d), (int) (13 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8 * d);
        bg.setColor(0x00000000);
        row.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(tint);
        row.addView(icon, new LinearLayout.LayoutParams(
                (int) (20 * d), (int) (20 * d)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(15f);
        name.setTextColor(TXT);
        texts.addView(name);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(11.5f);
            sub.setTextColor(DIM);
            sub.setPadding(0, (int) (2 * d), 0, 0);
            texts.addView(sub);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart((int) (12 * d));
        row.addView(texts, tlp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setTag("right");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMarginStart((int) (10 * d));
        row.addView(right, rlp);

        if (focusable) {
            row.setOnFocusChangeListener((v, has) -> {
                bg.setColor(has ? 0x14FFFFFF : 0x00000000);
                name.setTextColor(has ? 0xFFFFFFFF : TXT);
            });
            row.setNextFocusLeftId(activeRailId);
            if (firstPaneRow == null) firstPaneRow = row;
        }
        paneContent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout right(LinearLayout row) {
        return row.findViewWithTag("right");
    }

    private void chevron(LinearLayout right) {
        ImageView c = new ImageView(this);
        c.setImageResource(R.drawable.ic_chevron_right);
        c.setColorFilter(DIM);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (13 * d), (int) (13 * d));
        lp.setMarginStart((int) (8 * d));
        right.addView(c, lp);
    }

    private void actionRow(int icon, String title, String subtitle,
                           int tint, Runnable action) {
        LinearLayout row = baseRow(icon, title, subtitle, tint, true);
        chevron(right(row));
        row.setOnClickListener(v -> action.run());
    }

    private void infoRow(int icon, String title, String subtitle, String value) {
        LinearLayout row = baseRow(icon, title, subtitle, DIM, false);
        if (value != null && !value.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(value);
            t.setTextSize(11.5f);
            t.setTextColor(DIM);
            t.setSingleLine(true);
            right(row).addView(t);
        }
    }

    private void switchRow(int icon, String title, String subtitle, int tint,
                           boolean on, Runnable toggle) {
        LinearLayout row = baseRow(icon, title, subtitle, tint, true);
        FrameLayout sw = makeSwitch(on);
        right(row).addView(sw, new LinearLayout.LayoutParams(
                (int) (50 * d), (int) (28 * d)));
        row.setOnClickListener(v -> {
            toggle.run();
            setSwitchState(sw, !on);
        });
    }

    private FrameLayout makeSwitch(boolean on) {
        FrameLayout sw = new FrameLayout(this);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(20 * d);
        track.setColor(on ? ACCENT : 0xFF2A2A2A);
        sw.setBackground(track);

        View knob = new View(this);
        GradientDrawable kb = new GradientDrawable();
        kb.setShape(GradientDrawable.OVAL);
        kb.setColor(0xFFFFFFFF);
        knob.setBackground(kb);
        knob.setTag("knob");
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(
                (int) (22 * d), (int) (22 * d), Gravity.CENTER_VERTICAL);
        kp.leftMargin = (int) (3 * d);
        knob.setTranslationX(on ? 22 * d : 0);
        sw.addView(knob, kp);
        return sw;
    }

    private void setSwitchState(FrameLayout sw, boolean on) {
        View knob = sw.findViewWithTag("knob");
        if (knob != null)
            knob.animate().translationX(on ? 22 * d : 0)
                    .setDuration(160)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                    .start();
        GradientDrawable track = (GradientDrawable) sw.getBackground();
        if (track != null) track.setColor(on ? ACCENT : 0xFF2A2A2A);
    }

    // ---------- panels ----------

    private void launcherPanel() {
        switchRow(R.drawable.ic_play, "Continue watching",
                "Show your Play Next queue above the apps", DIM,
                prefs.getBoolean(K_CONTINUE, false),
                () -> prefs.edit().putBoolean(K_CONTINUE,
                        !prefs.getBoolean(K_CONTINUE, false)).apply());
        switchRow(R.drawable.ic_clock, "Clock seconds", "Show seconds in the clock",
                DIM, prefs.getBoolean(K_SECONDS, false),
                () -> prefs.edit().putBoolean(K_SECONDS,
                        !prefs.getBoolean(K_SECONDS, false)).apply());
        actionRow(R.drawable.ic_bolt, "Optimize memory", "Clear cached background apps",
                DIM, () -> MemoryOptimizer.sweep(this, freed -> Toast.makeText(this,
                        freed > 0 ? "Freed " + freed + " MB" : "Memory already optimized",
                        Toast.LENGTH_SHORT).show()));
    }

    private void aboutPanel() {
        infoRow(R.drawable.ic_info, "Launcher", "Hyper OS", "v" + versionName());
        infoRow(R.drawable.ic_settings, "Package", "Application id", getPackageName());
    }
}
