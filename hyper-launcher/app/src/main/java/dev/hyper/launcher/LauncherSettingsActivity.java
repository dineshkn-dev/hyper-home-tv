package dev.hyper.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * In-launcher settings page — a simple TV-style row list.
 * D-Pad up/down moves, OK cycles a value, BACK leaves.
 */
public final class LauncherSettingsActivity extends Activity {

    static final String PREF = "hyper";
    static final String K_COMPANION = "companion";   // 0 full, 1 ambient, 2 off
    static final String K_SECONDS = "seconds";
    static final String K_LABELS = "labels";         // true = always show names
    static final String K_WEATHER = "weather";
    static final String K_WIFI = "wifi";

    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (90 * d), (int) (48 * d),
                (int) (90 * d), (int) (40 * d));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("Launcher");
        title.setTextSize(26f);
        title.setTextColor(0xFFE8EDF8);
        title.setPadding(0, 0, 0, (int) (8 * d));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("D-pad moves · OK changes · Back returns");
        sub.setTextSize(13f);
        sub.setTextColor(0xFF69748F);
        sub.setPadding(0, 0, 0, (int) (28 * d));
        root.addView(sub);

        addRow(root, d, "Companion", () -> {
            int m = (prefs.getInt(K_COMPANION, 0) + 1) % 3;
            prefs.edit().putInt(K_COMPANION, m).apply();
        }, () -> {
            switch (prefs.getInt(K_COMPANION, 0)) {
                case 1: return "Ambient";
                case 2: return "Off";
                default: return "Full";
            }
        });
        addRow(root, d, "Companion hint",
                "Full — reacts to the remote · Ambient — just breathes · Off — hidden",
                null);
        addRow(root, d, "Clock seconds", () -> toggle(K_SECONDS),
                () -> onOff(K_SECONDS, true));
        addRow(root, d, "App names", () -> toggle(K_LABELS),
                () -> prefs.getBoolean(K_LABELS, false)
                        ? "Always" : "On focus");
        addRow(root, d, "Weather chip", () -> toggle(K_WEATHER),
                () -> onOff(K_WEATHER, true));
        addRow(root, d, "Wi-Fi chip", () -> toggle(K_WIFI),
                () -> onOff(K_WIFI, true));
    }

    private void toggle(String key) {
        prefs.edit().putBoolean(key, !prefs.getBoolean(key, true)).apply();
    }

    private String onOff(String key, boolean def) {
        return prefs.getBoolean(key, def) ? "On" : "Off";
    }

    private interface Action { void run(); }
    private interface Value { String get(); }

    private void addRow(LinearLayout root, float d, String label,
                        Action act, Value val) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (18 * d), (int) (13 * d),
                (int) (18 * d), (int) (13 * d));
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * d);
        row.setBackground(bg);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15f);
        name.setTextColor(0xFFC9D2E8);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(name, np);

        TextView value = new TextView(this);
        value.setTextSize(14f);
        value.setTextColor(0xFF5B9DFF);
        if (val != null) value.setText(val.get());
        row.addView(value);

        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x1FFFFFFF : 0x00000000);
            name.setTextColor(has ? 0xFFFFFFFF : 0xFFC9D2E8);
        });
        if (act != null) row.setOnClickListener(v -> {
            act.run();
            if (val != null) value.setText(val.get());
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (520 * d), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * d);
        root.addView(row, lp);
    }

    /** non-interactive footnote row */
    private void addRow(LinearLayout root, float d, String label,
                        String note, Action ignored) {
        TextView t = new TextView(this);
        t.setText(note);
        t.setTextSize(12f);
        t.setTextColor(0xFF525B70);
        t.setPadding((int) (18 * d), 0, 0, (int) (16 * d));
        root.addView(t);
    }
}
