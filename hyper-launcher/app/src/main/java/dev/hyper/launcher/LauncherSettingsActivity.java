package dev.hyper.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
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

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private WifiManager wm;
    private ConnectivityManager cm;

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
        wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        version.setText("HYPER OS  ·  v" + versionName());
        updateDate();
        android.widget.TextClock clock = findViewById(R.id.clock);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("h:mm a");

        setupTopBar();
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

    // ---------- top bar ----------

    private void setupTopBar() {
        setupAction(findViewById(R.id.searchBtn), () ->
                startActivity(new Intent(this, ManageAppsActivity.class)
                        .putExtra(ManageAppsActivity.EXTRA_SEARCH, true)));
        setupAction(findViewById(R.id.wifiBtn), () -> {
            selectRail(rails.get(0));
            focusPane();
        });
        setupAction(findViewById(R.id.settingsBtn), () ->
                safeStart(new Intent(Settings.ACTION_SETTINGS)));
        ImageView brand = findViewById(R.id.brandBtn);
        if (brand != null) {
            brand.setOnFocusChangeListener((v, has) ->
                    FocusGlow.apply(v, has, 1.12f));
        }
    }

    private void setupAction(ImageView v, Runnable action) {
        if (v == null) return;
        v.setColorFilter(DIM);
        v.setOnClickListener(x -> action.run());
        v.setOnFocusChangeListener((x, has) -> {
            FocusGlow.apply(x, has, 1.16f);
            ((ImageView) x).setColorFilter(has ? TXT : DIM);
        });
    }

    // ---------- left rail ----------

    private void buildRails() {
        rails.add(new Rail(0, R.drawable.ic_wifi, 0xFFC8FF3D,
                "Network & Internet", "Manage connections for this device."));
        rails.add(new Rail(1, R.drawable.ic_display, 0xFF5BC8AF,
                "Display & Sound", "Picture, sound, and screen saver."));
        rails.add(new Rail(2, R.drawable.ic_user, 0xFFF0B84A,
                "Accounts & Sign-in", "Add and manage accounts."));
        rails.add(new Rail(3, R.drawable.ic_apps, 0xFFE86A92,
                "Apps", "Manage installed apps and defaults."));
        rails.add(new Rail(4, R.drawable.ic_shield, 0xFFFF4D9D,
                "Privacy", "Control permissions and data."));
        rails.add(new Rail(5, R.drawable.ic_accessibility, 0xFFB58CFF,
                "Accessibility", "Make the TV easier to use."));
        rails.add(new Rail(6, R.drawable.ic_settings, 0xFF9A9A9A,
                "Device Preferences", "Launcher layout, clock, and order."));
        rails.add(new Rail(7, R.drawable.ic_info, 0xFF7EE787,
                "About", "Device and software information."));
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

        switch (r.id) {
            case 0: networkPanel(); break;
            case 1: displayPanel(); break;
            case 2: accountsPanel(); break;
            case 3: appsPanel(); break;
            case 4: privacyPanel(); break;
            case 5: accessibilityPanel(); break;
            case 6: devicePrefsPanel(); break;
            default: aboutPanel(); break;
        }
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

    private void section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(9.5f);
        t.setLetterSpacing(0.18f);
        t.setTextColor(DIM);
        t.setPadding(0, (int) (18 * d), 0, (int) (8 * d));
        paneContent.addView(t);
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

    private void value(LinearLayout right, String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11.5f);
        t.setTextColor(color);
        t.setSingleLine(true);
        right.addView(t);
    }

    private void actionRow(int icon, String title, String subtitle,
                           int tint, Runnable action) {
        LinearLayout row = baseRow(icon, title, subtitle, tint, true);
        chevron(right(row));
        row.setOnClickListener(v -> action.run());
    }

    private void infoRow(int icon, String title, String subtitle, String value) {
        LinearLayout row = baseRow(icon, title, subtitle, DIM, false);
        if (value != null && !value.isEmpty()) value(right(row), value, DIM);
    }

    private void switchRow(int icon, String title, String subtitle, int tint,
                           boolean on, int rightIcon, Runnable toggle) {
        LinearLayout row = baseRow(icon, title, subtitle, tint, true);
        LinearLayout right = right(row);
        if (rightIcon != 0) {
            ImageView ri = new ImageView(this);
            ri.setImageResource(rightIcon);
            ri.setColorFilter(DIM);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (18 * d), (int) (18 * d));
            lp.setMarginEnd((int) (12 * d));
            right.addView(ri, lp);
        }
        FrameLayout sw = makeSwitch(on);
        right.addView(sw, new LinearLayout.LayoutParams(
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

    private void networkPanel() {
        boolean wifiOn = wm != null && wm.isWifiEnabled();
        String ssid = readSsid();
        String wifiSub = wifiOn
                ? (ssid.isEmpty() ? "Connected" : ssid) : "Off";
        switchRow(R.drawable.ic_wifi, "Wi-Fi", wifiSub, DIM, wifiOn,
                R.drawable.ic_wifi, this::toggleWifi);

        section("AVAILABLE NETWORKS");
        List<ScanResult> nets = scanResults();
        if (nets.isEmpty()) {
            infoRow(R.drawable.ic_wifi, "No networks found",
                    "Open Wi-Fi settings to scan again", null);
            actionRow(R.drawable.ic_settings, "Wi-Fi settings",
                    "Connect to a network", DIM, () ->
                            safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        } else {
            for (ScanResult sr : nets) {
                boolean locked = isSecured(sr);
                LinearLayout row = baseRow(R.drawable.ic_wifi, sr.SSID, null, DIM, true);
                LinearLayout right = right(row);
                if (locked) {
                    ImageView lock = new ImageView(this);
                    lock.setImageResource(R.drawable.ic_lock);
                    lock.setColorFilter(DIM);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            (int) (13 * d), (int) (13 * d));
                    lp.setMarginEnd((int) (12 * d));
                    right.addView(lock, lp);
                }
                SignalBars bars = new SignalBars(this);
                bars.setLevel(WifiManager.calculateSignalLevel(sr.level, 5));
                right.addView(bars, new LinearLayout.LayoutParams(
                        (int) (22 * d), (int) (15 * d)));
                row.setOnClickListener(v ->
                        safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS)));
            }
        }

        boolean ethernet = hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        LinearLayout eth = baseRow(R.drawable.ic_ethernet, "Ethernet", null, DIM, true);
        value(right(eth), ethernet ? "Connected" : "Not connected", DIM);
        eth.setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_WIRELESS_SETTINGS)));

        boolean vpn = hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        LinearLayout vpnRow = baseRow(R.drawable.ic_vpn, "VPN", null, DIM, true);
        value(right(vpnRow), vpn ? "Connected" : "Set up", vpn ? DIM : ACCENT);
        vpnRow.setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_VPN_SETTINGS)));

        switchRow(R.drawable.ic_airplane, "Airplane Mode", null, DIM,
                airplaneOn(), 0, () -> safeStart(
                        new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)));
    }

    private void displayPanel() {
        actionRow(R.drawable.ic_display, "Picture", "Picture mode and display options",
                DIM, () -> safeStart(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
        actionRow(R.drawable.ic_thermo, "Sound", "Volume and sound output",
                DIM, () -> safeStart(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        actionRow(R.drawable.ic_cloud, "Screen saver", "Ambient and screensaver settings",
                DIM, () -> safeStart(new Intent(Settings.ACTION_DREAM_SETTINGS)));
        actionRow(R.drawable.ic_clock, "Sleep timer", "Turn the display off after inactivity",
                DIM, () -> safeStart(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
    }

    private void accountsPanel() {
        actionRow(R.drawable.ic_user, "Add account", "Sign in to a service",
                DIM, () -> safeStart(new Intent(Settings.ACTION_ADD_ACCOUNT)));
        actionRow(R.drawable.ic_cloud, "Accounts", "Sync and manage accounts",
                DIM, () -> safeStart(new Intent(Settings.ACTION_SYNC_SETTINGS)));
        actionRow(R.drawable.ic_user, "Google", "Add a Google account",
                DIM, () -> {
                    Intent i = new Intent(Settings.ACTION_ADD_ACCOUNT);
                    i.putExtra(Settings.EXTRA_ACCOUNT_TYPES,
                            new String[]{"com.google"});
                    safeStart(i);
                });
    }

    private void appsPanel() {
        String count = installedAppCount() + " apps installed";
        actionRow(R.drawable.ic_apps, "Manage apps", count,
                DIM, () -> startActivity(new Intent(this, ManageAppsActivity.class)));
        actionRow(R.drawable.ic_settings, "Default apps", "Home, browser, and other defaults",
                DIM, () -> safeStart(new Intent(
                        Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)));
        actionRow(R.drawable.ic_shield, "App permissions", "Review app permissions",
                DIM, () -> safeStart(new Intent(
                        Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        actionRow(R.drawable.ic_delete, "Uninstall apps", "Free up space",
                DIM, () -> startActivity(new Intent(this, ManageAppsActivity.class)));
    }

    private void privacyPanel() {
        actionRow(R.drawable.ic_shield, "Permissions", "Review app permissions",
                DIM, () -> safeStart(new Intent(
                        Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        actionRow(R.drawable.ic_location, "Location", "Location access for apps",
                DIM, () -> safeStart(new Intent(
                        Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        actionRow(R.drawable.ic_info, "Usage & diagnostics", "Share usage information",
                DIM, () -> safeStart(new Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        infoRow(R.drawable.ic_shield, "No telemetry or ad tracking",
                "Hyper OS keeps launcher data on this device", null);
    }

    private void accessibilityPanel() {
        actionRow(R.drawable.ic_accessibility, "TalkBack", "Screen reader",
                DIM, () -> safeStart(new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        actionRow(R.drawable.ic_tag, "Captions", "Subtitle and caption settings",
                DIM, () -> safeStart(new Intent(
                        Settings.ACTION_CAPTIONING_SETTINGS)));
        actionRow(R.drawable.ic_display, "Display & text", "Size, contrast, and motion",
                DIM, () -> safeStart(new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    private void devicePrefsPanel() {
        switchRow(R.drawable.ic_play, "Continue watching",
                "Show your Play Next queue above the apps", DIM,
                prefs.getBoolean(K_CONTINUE, false), 0,
                () -> prefs.edit().putBoolean(K_CONTINUE,
                        !prefs.getBoolean(K_CONTINUE, false)).apply());
        switchRow(R.drawable.ic_clock, "Clock seconds", "Show seconds in the clock",
                DIM, prefs.getBoolean(K_SECONDS, false), 0,
                () -> prefs.edit().putBoolean(K_SECONDS,
                        !prefs.getBoolean(K_SECONDS, false)).apply());
        actionRow(R.drawable.ic_bolt, "Optimize memory", "Clear cached background apps",
                DIM, () -> MemoryOptimizer.sweep(this, freed -> Toast.makeText(this,
                        freed > 0 ? "Freed " + freed + " MB" : "Memory already optimized",
                        Toast.LENGTH_SHORT).show()));
        infoRow(R.drawable.ic_info, "Launcher version", "Hyper OS build",
                "v" + versionName());
    }

    private void aboutPanel() {
        infoRow(R.drawable.ic_display, "Device", Build.MODEL,
                Build.MANUFACTURER + " " + Build.MODEL);
        infoRow(R.drawable.ic_settings, "Android", Build.VERSION.RELEASE,
                "API " + Build.VERSION.SDK_INT + " · " + Build.DISPLAY);
        infoRow(R.drawable.ic_clock, "Build", Build.ID, null);
        infoRow(R.drawable.ic_storage, "Storage", usedStorage(), freeStorage() + " free");
        infoRow(R.drawable.ic_wifi, "IP address", ipAddress(), null);
        infoRow(R.drawable.ic_info, "Launcher", "Hyper OS", "v" + versionName());
    }

    // ---------- network helpers ----------

    private String readSsid() {
        try {
            if (wm == null) return "";
            String s = wm.getConnectionInfo().getSSID();
            if (s == null || s.equals("<unknown ssid>")) return "";
            return s.replace("\"", "");
        } catch (Exception e) { return ""; }
    }

    private List<ScanResult> scanResults() {
        List<ScanResult> out = new ArrayList<>();
        if (wm == null) return out;
        try {
            wm.startScan();
            String current = readSsid();
            Set<String> seen = new HashSet<>();
            for (ScanResult sr : wm.getScanResults()) {
                if (sr == null || sr.SSID == null || sr.SSID.isEmpty()) continue;
                if (sr.SSID.equals(current)) continue;
                if (!seen.add(sr.SSID)) continue;
                out.add(sr);
            }
            Collections.sort(out, (a, b) -> Integer.compare(b.level, a.level));
            if (out.size() > 6) return new ArrayList<>(out.subList(0, 6));
        } catch (Exception ignored) {}
        return out;
    }

    private static boolean isSecured(ScanResult sr) {
        String c = sr.capabilities;
        return c != null && (c.contains("WPA") || c.contains("WEP")
                || c.contains("PSK") || c.contains("EAP"));
    }

    private void toggleWifi() {
        if (wm == null) return;
        boolean next = !wm.isWifiEnabled();
        boolean changed = false;
        if (Build.VERSION.SDK_INT < 29) {
            try { changed = wm.setWifiEnabled(next); } catch (Exception ignored) {}
        }
        if (!changed) {
            safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } else {
            showCategory(active);
            focusPane();
        }
    }

    private boolean airplaneOn() {
        try {
            return Settings.Global.getInt(getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        } catch (Exception e) { return false; }
    }

    private boolean hasTransport(int transport) {
        try {
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            return c != null && c.hasTransport(transport);
        } catch (Exception e) { return false; }
    }

    private String ipAddress() {
        try {
            Network n = cm.getActiveNetwork();
            if (n == null) return "—";
            for (android.net.LinkAddress la : cm.getLinkProperties(n).getLinkAddresses())
                if (la.getAddress() instanceof Inet4Address)
                    return la.getAddress().getHostAddress();
        } catch (Exception ignored) {}
        return "—";
    }

    private void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) {}
        }
    }

    // ---------- app/storage helpers ----------

    private int installedAppCount() {
        try {
            Set<String> pkgs = new HashSet<>();
            android.content.pm.PackageManager pm = getPackageManager();
            for (String cat : new String[]{
                    Intent.CATEGORY_LEANBACK_LAUNCHER,
                    Intent.CATEGORY_LAUNCHER}) {
                Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for (android.content.pm.ResolveInfo ri
                        : pm.queryIntentActivities(probe, 0))
                    pkgs.add(ri.activityInfo.packageName);
            }
            pkgs.remove(getPackageName());
            pkgs.remove("com.android.tv.settings");
            return pkgs.size();
        } catch (Exception e) { return 0; }
    }

    private String usedStorage() {
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            long used = sf.getTotalBytes() - sf.getAvailableBytes();
            return fmtBytes(used);
        } catch (Exception e) { return "—"; }
    }

    private String freeStorage() {
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            return fmtBytes(sf.getAvailableBytes());
        } catch (Exception e) { return "—"; }
    }

    private static String fmtBytes(long b) {
        if (b >= 1_000_000_000L)
            return String.format(Locale.US, "%.1f GB", b / 1e9);
        return (b / 1_000_000L) + " MB";
    }
}
