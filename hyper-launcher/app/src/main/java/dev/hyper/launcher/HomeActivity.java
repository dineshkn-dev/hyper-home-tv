package dev.hyper.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HomeActivity extends Activity {

    private static final String SETTINGS_PKG = "com.android.tv.settings";


    private GridView grid;
    private TextView hint;
    private AppAdapter adapter;
    private List<AppEntry> orderedApps = new ArrayList<>();
    private List<HomeItem> allItems = new ArrayList<>();
    private final List<Folder> folders = new ArrayList<>();
    private final Set<String> hiddenPkgs = new HashSet<>();
    private View lastSelectedTile;
    private View barFocus;   // top-bar element that last held focus
    private Dialog menuDlg;
    private boolean menuOpen, moving;
    private String lastPkg, lastItemKey;
    private ConnectivityManager cm;
    // last weather fetch for the details dropdown
    private String wCity = "", wCond = "";
    private double wTemp, wFeels, wHi, wLo;
    private int wHum, wWind, wCode = -1, wAqi = -1, wPm25 = -1;
    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            fetchWeather();
            handler.postDelayed(this, 30 * 60 * 1000);   // 30 min
        }
    };
    private final ConnectivityManager.NetworkCallback netCb =
            new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { updateWifi(); }
        @Override public void onLost(Network n) { updateWifi(); }
        @Override public void onCapabilitiesChanged(Network n,
                NetworkCapabilities c) { updateWifi(); }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_home);
        grid = findViewById(R.id.grid);
        hint = findViewById(R.id.hint);

        // top scrim only while content is meaningfully scrolled up —
        // a micro ensure-visible offset (a few px) must not trigger it
        final View topFade = findViewById(R.id.gridFadeTop);
        final int fadeSlack =
                (int) (24 * getResources().getDisplayMetrics().density);
        final Runnable syncTopFade = () -> {
            View fc = grid.getChildAt(0);
            boolean up = grid.getFirstVisiblePosition() > 0
                    || (fc != null && fc.getTop() < -fadeSlack);
            if ((topFade.getAlpha() > 0.5f) != up)
                topFade.animate().alpha(up ? 1f : 0f)
                        .setDuration(220).start();
        };
        grid.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(
                    android.widget.AbsListView v, int s) {
                if (s == SCROLL_STATE_IDLE) v.post(syncTopFade);
            }
            @Override public void onScroll(android.widget.AbsListView v,
                    int first, int count, int total) {
                syncTopFade.run();
            }
        });
        // relayouts / the restore-scroll can leave it stuck — resync after
        grid.addOnLayoutChangeListener(
                (v, l, t, r, bo, ol, ot, or, ob) -> syncTopFade.run());
        grid.postDelayed(syncTopFade::run, 600);
        for (String p : prefs().getString("hidden", "").split("\n")) {
            p = p.trim();
            if (!p.isEmpty()) hiddenPkgs.add(p);
        }
        lastPkg = prefs().getString("last", null);
        lastItemKey = prefs().getString("lastItem", null);
        loadFolders();
        updateDate();

        ImageView gear = findViewById(R.id.settingsBtn);
        gear.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) {}
        });
        gear.setOnFocusChangeListener((v, has) -> {
            GradientDrawable gb = new GradientDrawable();
            gb.setShape(GradientDrawable.OVAL);
            gb.setColor(has ? 0x2EFFFFFF : 0x00000000);
            v.setBackground(gb);
            v.animate().scaleX(has ? 1.18f : 1f).scaleY(has ? 1.18f : 1f)
                    .setDuration(160).start();
            ((ImageView) v).setColorFilter(has ? 0xFFE8EDF8 : 0xFF8A94AA);
            // drop the tile's focused look while the gear holds focus
            if (has) {
                barFocus = v;
                if (lastSelectedTile instanceof FocusedTile)
                    ((FocusedTile) lastSelectedTile).setFocusedState(false);
            }
        });

        ImageView paw = findViewById(R.id.launcherBtn);
        paw.setOnClickListener(v ->
                startActivity(new Intent(this, LauncherSettingsActivity.class)));
        paw.setOnFocusChangeListener((v, has) -> {
            GradientDrawable pb = new GradientDrawable();
            pb.setShape(GradientDrawable.OVAL);
            pb.setColor(has ? 0x2EFFFFFF : 0x00000000);
            v.setBackground(pb);
            v.animate().scaleX(has ? 1.18f : 1f).scaleY(has ? 1.18f : 1f)
                    .setDuration(160).start();
            ((ImageView) v).setColorFilter(has ? 0xFFE8EDF8 : 0xFF8A94AA);
            if (has) {
                barFocus = v;
                if (lastSelectedTile instanceof FocusedTile)
                    ((FocusedTile) lastSelectedTile).setFocusedState(false);
            }
        });

        View date = findViewById(R.id.date);
        date.setOnClickListener(v -> showCalendarDialog());
        date.setOnFocusChangeListener((v, has) -> {
            GradientDrawable db = new GradientDrawable();
            db.setColor(has ? 0x2EFFFFFF : 0x00000000);
            db.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            v.setBackground(db);
            ((TextView) v).setTextColor(has ? 0xFFE8EDF8 : 0xFF8A94AA);
            if (has) {
                barFocus = v;
                if (lastSelectedTile instanceof FocusedTile)
                    ((FocusedTile) lastSelectedTile).setFocusedState(false);
            }
        });

        View weather = findViewById(R.id.weather);
        weather.setOnClickListener(v -> showWeatherDialog());
        weather.setOnFocusChangeListener((v, has) -> {
            GradientDrawable tb = new GradientDrawable();
            tb.setColor(has ? 0x2EFFFFFF : 0x00000000);
            tb.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            v.setBackground(tb);
            ((TextView) v).setTextColor(has ? 0xFFE8EDF8 : 0xFF8A94AA);
            if (has) {
                barFocus = v;
                if (lastSelectedTile instanceof FocusedTile)
                    ((FocusedTile) lastSelectedTile).setFocusedState(false);
            }
        });

        View wifi = findViewById(R.id.wifiGroup);
        wifi.setOnClickListener(v -> showWifiDialog());
        wifi.setOnFocusChangeListener((v, has) -> {
            GradientDrawable wb = new GradientDrawable();
            wb.setColor(has ? 0x2EFFFFFF : 0x00000000);
            wb.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            v.setBackground(wb);
            if (has) {
                barFocus = v;
                if (lastSelectedTile instanceof FocusedTile)
                    ((FocusedTile) lastSelectedTile).setFocusedState(false);
            }
        });
        // re-paint focus when it comes back to the grid
        grid.setOnFocusChangeListener((v, has) -> {
            if (has) {
                barFocus = null;
                View sel = grid.getSelectedView();
                if (sel instanceof FocusedTile) {
                    ((FocusedTile) sel).setFocusedState(true);
                    lastSelectedTile = sel;
                }
            }
        });

        grid.setOnItemClickListener((p, v, pos, id) -> {
            if (!moving && !menuOpen && pos >= 0 && pos < allItems.size()) {
                HomeItem item = allItems.get(pos);
                if (item.isFolder()) openFolder(item);
                else launch(item.app);
            }
        });
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            if (!moving && pos >= 0 && pos < allItems.size()) showMenu(v, pos);
            return true;
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (lastSelectedTile instanceof FocusedTile)
                    ((FocusedTile) lastSelectedTile).setFocusedState(false);
                if (v instanceof FocusedTile)
                    ((FocusedTile) v).setFocusedState(true);
                lastSelectedTile = v;
                if (pos >= 0 && pos < allItems.size()) {
                    HomeItem item = allItems.get(pos);
                    lastItemKey = item.key();
                    prefs().edit().putString("lastItem", lastItemKey).apply();
                    if (!item.isFolder()) {
                        lastPkg = item.app.pkg;
                        prefs().edit().putString("last", lastPkg).apply();
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {
                if (lastSelectedTile instanceof FocusedTile)
                    ((FocusedTile) lastSelectedTile).setFocusedState(false);
                lastSelectedTile = null;
            }
        });
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("hyper", MODE_PRIVATE);
    }

    @Override protected void onResume() {
        super.onResume();
        updateDate();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(pkgReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(pkgReceiver, f);
        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        cm.registerDefaultNetworkCallback(netCb);
        updateWifi();
        loadApps();
        applyPrefs();
        BuddyView buddy = findViewById(R.id.buddy);
        buddy.setGround(findViewById(R.id.ground));
        ((ChaseView) findViewById(R.id.chase)).setBuddy(buddy);
        if (prefs().getBoolean(LauncherSettingsActivity.K_WEATHER, true)) {
            restoreWeather();      // paint last known weather instantly
            handler.post(weatherTick);
        }
    }

    /** settings from the Launcher page — applied each time we resume */
    private void applyPrefs() {
        android.content.SharedPreferences p = prefs();
        int mode = p.getInt(LauncherSettingsActivity.K_COMPANION, 0);
        int vis = mode == 2 ? View.GONE : View.VISIBLE;
        findViewById(R.id.buddy).setVisibility(vis);
        findViewById(R.id.ground).setVisibility(vis);
        boolean mouseOn = p.getBoolean(
                LauncherSettingsActivity.K_MOUSE, true);
        findViewById(R.id.chase).setVisibility(mouseOn ? vis : View.GONE);
        ((BuddyView) findViewById(R.id.buddy)).setMode(mode);
        ChaseView chase = findViewById(R.id.chase);
        chase.setMode(mode);
        chase.setMouseEnabled(mouseOn);

        findViewById(R.id.heroStrip).setVisibility(
                p.getBoolean(LauncherSettingsActivity.K_HERO, true)
                        ? View.VISIBLE : View.GONE);

        boolean seconds = p.getBoolean(
                LauncherSettingsActivity.K_SECONDS, true);
        android.widget.TextClock clock = findViewById(R.id.clock);
        clock.setFormat12Hour(seconds ? "h:mm:ss a" : "h:mm a");
        clock.setFormat24Hour(seconds ? "H:mm:ss" : "H:mm");

        AppTile.labelsAlways = p.getBoolean(
                LauncherSettingsActivity.K_LABELS, false);

        if (!p.getBoolean(LauncherSettingsActivity.K_WEATHER, true))
            findViewById(R.id.weather).setVisibility(View.GONE);
    }

    private void updateWifi() {
        View group = findViewById(R.id.wifiGroup);
        if (!prefs().getBoolean(LauncherSettingsActivity.K_WIFI, true)) {
            group.setVisibility(View.GONE);
            return;
        }
        ImageView ic = findViewById(R.id.wifiIcon);
        TextView ssidView = findViewById(R.id.ssid);
        ssidView.setSelected(true);   // marquee always scrolls
        NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
        String ssid = readSsid();
        runOnUiThread(() -> {
            if (c != null && c.hasTransport(
                    NetworkCapabilities.TRANSPORT_ETHERNET)) {
                group.setVisibility(View.GONE);       // wired: nothing to show
            } else if (c != null) {
                group.setVisibility(View.VISIBLE);
                ic.setColorFilter(0xFF8A94AA);        // wifi up
                ssidView.setText(ssid.isEmpty() ? "Wi-Fi" : ssid);
            } else {
                group.setVisibility(View.VISIBLE);
                ic.setColorFilter(0xFFFF6B6B);        // offline
                ssidView.setText("offline");
            }
        });
    }

    private String readSsid() {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    getApplicationContext().getSystemService(WIFI_SERVICE);
            String s = wm.getConnectionInfo().getSSID();
            if (s == null || s.equals("<unknown ssid>")) return "";
            return s.replace("\"", "");
        } catch (Exception e) {
            return "";
        }
    }

    @Override protected void onPause() {
        super.onPause();
        closeMenu();
        try { unregisterReceiver(pkgReceiver); } catch (IllegalArgumentException ignored) {}
        if (cm != null) cm.unregisterNetworkCallback(netCb);
        handler.removeCallbacks(weatherTick);
    }

    /* ---------------- weather ---------------- */

    /**
     * Anonymous weather for the top bar: device location if available,
     * otherwise coarse IP geolocation, then Open-Meteo (no key, no tracking).
     */
    private void fetchWeather() {
        new Thread(() -> {
            try {
                double lat = Double.NaN, lon = Double.NaN;
                LocationManager lm = (LocationManager)
                        getSystemService(LOCATION_SERVICE);
                try {
                    Location loc = lm.getLastKnownLocation(
                            LocationManager.NETWORK_PROVIDER);
                    if (loc == null)
                        loc = lm.getLastKnownLocation(
                                LocationManager.PASSIVE_PROVIDER);
                    if (loc != null) { lat = loc.getLatitude(); lon = loc.getLongitude(); }
                } catch (SecurityException ignored) {}

                String city = "";
                if (Double.isNaN(lat)) {
                    org.json.JSONObject geo = getJson(
                            "https://ipwho.is/?fields=success,latitude,longitude,city");
                    if (!geo.optBoolean("success"))
                        throw new Exception("geo failed");
                    lat = geo.getDouble("latitude");
                    lon = geo.getDouble("longitude");
                    city = geo.optString("city", "");
                }

                org.json.JSONObject w = getJson(
                        "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                        + "&longitude=" + lon
                        + "&current=temperature_2m,relative_humidity_2m,"
                        + "apparent_temperature,weather_code,wind_speed_10m"
                        + "&daily=temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto");
                org.json.JSONObject cur = w.getJSONObject("current");
                wTemp = cur.getDouble("temperature_2m");
                wFeels = cur.getDouble("apparent_temperature");
                wHum = cur.getInt("relative_humidity_2m");
                wWind = (int) cur.getDouble("wind_speed_10m");
                wCode = cur.getInt("weather_code");
                wCond = weatherText(wCode);
                wCity = city;
                org.json.JSONObject daily = w.getJSONObject("daily");
                wHi = daily.getJSONArray("temperature_2m_max").getDouble(0);
                wLo = daily.getJSONArray("temperature_2m_min").getDouble(0);

                try {   // AQI is best-effort; don't fail the whole fetch
                    org.json.JSONObject aq = getJson(
                            "https://air-quality-api.open-meteo.com/v1/"
                            + "air-quality?latitude=" + lat + "&longitude="
                            + lon + "&current=us_aqi,pm2_5&timezone=auto");
                    org.json.JSONObject ac = aq.getJSONObject("current");
                    wAqi = ac.getInt("us_aqi");
                    wPm25 = (int) ac.getDouble("pm2_5");
                } catch (Exception ignored) {}

                saveWeather();
                runOnUiThread(this::renderWeather);
            } catch (Exception e) {
                runOnUiThread(() ->
                        findViewById(R.id.weather).setVisibility(View.GONE));
            }
        }, "weather").start();
    }

    /** paint the bar chip from cached/fetched fields */
    private void renderWeather() {
        TextView v = findViewById(R.id.weather);
        if (wCode < 0) { v.setVisibility(View.GONE); return; }
        v.setText(weatherIcon(wCode) + " " + Math.round(wTemp) + "°"
                + (wAqi >= 0 ? "  ·  AQI " + wAqi : ""));
        v.setVisibility(View.VISIBLE);
    }

    private void saveWeather() {
        try {
            org.json.JSONObject j = new org.json.JSONObject();
            j.put("t", wTemp); j.put("f", wFeels); j.put("h", wHum);
            j.put("w", wWind); j.put("c", wCode); j.put("a", wAqi);
            j.put("p", wPm25); j.put("hi", wHi); j.put("lo", wLo);
            j.put("city", wCity); j.put("cond", wCond);
            prefs().edit().putString("wx", j.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void restoreWeather() {
        try {
            org.json.JSONObject j =
                    new org.json.JSONObject(prefs().getString("wx", ""));
            wTemp = j.getDouble("t"); wFeels = j.getDouble("f");
            wHum = j.getInt("h"); wWind = j.getInt("w");
            wCode = j.getInt("c"); wAqi = j.getInt("a");
            wPm25 = j.getInt("p"); wHi = j.getDouble("hi");
            wLo = j.getDouble("lo");
            wCity = j.optString("city", ""); wCond = j.optString("cond", "");
            renderWeather();
        } catch (Exception ignored) {}
    }

    private static org.json.JSONObject getJson(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try {
            java.io.InputStream in = c.getInputStream();
            java.util.Scanner sc = new java.util.Scanner(in)
                    .useDelimiter("\\A");
            return new org.json.JSONObject(sc.hasNext() ? sc.next() : "{}");
        } finally { c.disconnect(); }
    }

    private static String weatherIcon(int c) {
        if (c == 0) return "☀";
        if (c <= 2) return "⛅";
        if (c == 3) return "☁";
        if (c <= 48) return "🌫";
        if (c <= 57) return "🌦";
        if (c <= 67) return "🌧";
        if (c <= 77) return "❄";
        if (c <= 82) return "🌧";
        if (c <= 86) return "🌨";
        return "⛈";
    }

    private static String weatherText(int c) {
        if (c == 0) return "Clear";
        if (c <= 2) return "Partly cloudy";
        if (c == 3) return "Overcast";
        if (c <= 48) return "Foggy";
        if (c <= 57) return "Drizzle";
        if (c <= 67) return "Rain";
        if (c <= 77) return "Snow";
        if (c <= 82) return "Showers";
        if (c <= 86) return "Snow showers";
        return "Thunderstorm";
    }

    private static String aqiText(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for sensitive";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    private void showWeatherDialog() {
        if (wCode < 0) return;   // nothing fetched yet
        closeMenu();
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(weatherIcon(wCode) + "  " + Math.round(wTemp)
                + "°  " + wCond);
        title.setTextSize(17f);
        title.setTextColor(0xFFE8EDF8);
        title.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d),
                (int) (2 * d));
        panel.addView(title);

        if (!wCity.isEmpty() || wHi != 0) {
            TextView sub = new TextView(this);
            String s = "";
            if (!wCity.isEmpty()) s += wCity;
            if (wHi != 0)
                s += (s.isEmpty() ? "" : "  ·  ") + "H "
                        + Math.round(wHi) + "°  L " + Math.round(wLo) + "°";
            sub.setText(s);
            sub.setTextSize(12.5f);
            sub.setTextColor(0xFF8A94AA);
            sub.setPadding((int) (14 * d), 0, (int) (14 * d), (int) (6 * d));
            panel.addView(sub);
        }

        detailRow(panel, d, "Feels like", Math.round(wFeels) + "°");
        detailRow(panel, d, "Humidity", wHum + "%");
        detailRow(panel, d, "Wind", wWind + " km/h");
        if (wAqi >= 0)
            detailRow(panel, d, "Air quality", wAqi + " · " + aqiText(wAqi));
        if (wPm25 >= 0)
            detailRow(panel, d, "PM2.5", wPm25 + " µg/m³");

        View anchor = findViewById(R.id.weather);
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        buildDialog(panel, 230, loc[0] - (int) (20 * d),
                loc[1] + anchor.getHeight() + (int) (4 * d), 0.5f, 0f);
    }

    @Override public void onBackPressed() {
        if (menuOpen) closeMenu();
        else if (moving) exitMove();
        // otherwise home shouldn't go back
    }

    /* ---------------- context menu ---------------- */

    /**
     * Shared dialog chrome: dark rounded card over a blurred backdrop that
     * grows out of the anchor point (pivotYFrac: 0 = opens below the tile,
     * 1 = opens above it), so it reads as coming from the app.
     */
    private Dialog buildDialog(View content, int widthDp, int x, int y,
                               float pivotXFrac, float pivotYFrac) {
        return buildDialog(content, widthDp, x, y, pivotXFrac, pivotYFrac,
                0.88f, 170, null);
    }

    private Dialog buildDialog(View content, int widthDp, int x, int y,
                               float pivotXFrac, float pivotYFrac,
                               float startScale, int durationMs,
                               View pivotAnchor) {
        float d = getResources().getDisplayMetrics().density;
        // opaque dark card over the blurred backdrop
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF252B3A, 0xFF1C212E});
        bg.setCornerRadius(16 * d);
        bg.setStroke((int) (1 * d), 0x52FFFFFF);
        content.setBackground(bg);
        int pad = (int) (8 * d);
        content.setPadding(pad, pad, pad, pad);

        Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(content);
        Window w = dlg.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width = (int) (widthDp * d);
        if (x >= 0) {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = x;
            lp.y = y;
        }
        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        if (Build.VERSION.SDK_INT >= 31)
            lp.setBlurBehindRadius(48);
        lp.dimAmount = 0.28f;
        w.setAttributes(lp);
        dlg.setOnDismissListener(dd -> {
            // a new dialog may have replaced this one already — don't
            // clear its state when the old dismiss callback finally lands
            if (menuDlg == dlg) {
                menuOpen = false;
                menuDlg = null;
                focusBack();
            }
        });
        menuOpen = true;
        menuDlg = dlg;
        dlg.show();

        // bloom from the anchor point (or the given view) once laid out
        content.setAlpha(0f);
        content.post(() -> {
            if (pivotAnchor != null) {
                int[] panelLoc = new int[2];
                int[] anchorLoc = new int[2];
                content.getLocationOnScreen(panelLoc);
                pivotAnchor.getLocationOnScreen(anchorLoc);
                content.setPivotX(anchorLoc[0] + pivotAnchor.getWidth() / 2f
                        - panelLoc[0]);
                content.setPivotY(anchorLoc[1] + pivotAnchor.getHeight() / 2f
                        - panelLoc[1]);
            } else {
                content.setPivotX(content.getWidth() * pivotXFrac);
                content.setPivotY(content.getHeight() * pivotYFrac);
            }
            content.setScaleX(startScale);
            content.setScaleY(startScale);
            content.setTranslationY(pivotAnchor != null ? 0f
                    : (pivotYFrac > 0 ? 10 * d : -10 * d));
            content.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(durationMs)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();
        });
        return dlg;
    }

    private void showMenu(View tile, int pos) {
        if (pos < 0 || pos >= allItems.size()) return;
        HomeItem item = allItems.get(pos);
        if (item.isFolder()) {
            showFolderMenu(tile, item.folder);
            return;
        }
        AppEntry app = item.app;
        closeMenu();

        float d = getResources().getDisplayMetrics().density;
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);

        // dragging only makes sense under manual ordering
        if (prefs().getInt(LauncherSettingsActivity.K_SORT, 2) == 2)
            addMenuItem(menu, d, R.drawable.ic_move, "Move", () -> {
                closeMenu();
                enterMove(pos);
            });
        addMenuItem(menu, d, R.drawable.ic_folder, "Add to folder", () -> {
            closeMenu();
            showFolderPicker(app);
        });
        if (app.userInstalled) {
            addMenuItem(menu, d, R.drawable.ic_delete, "Uninstall", () -> {
                closeMenu();
                try {
                    // startActivityForResult sets the calling package —
                    // UninstallerActivity finishes instantly without it
                    startActivityForResult(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                            Uri.fromParts("package", app.pkg, null)), 0);
                } catch (Exception ignored) {}
            });
        } else {
            addMenuItem(menu, d, R.drawable.ic_info, "App info", () -> {
                closeMenu();
                openAppInfo(app.pkg);
            });
        }
        addMenuItem(menu, d, R.drawable.ic_hide, "Hide", () -> {
            closeMenu();
            hiddenPkgs.add(app.pkg);
            saveHidden();
            populate();
        });
        if (!hiddenPkgs.isEmpty())
            addMenuItem(menu, d, R.drawable.ic_restore,
                    "Hidden apps (" + hiddenPkgs.size() + ")",
                    () -> { closeMenu(); showHiddenPanel(); });

        showAnchoredMenu(tile, menu);
    }

    /** Position a context menu next to its tile, clamped to the screen. */
    private void showAnchoredMenu(View tile, LinearLayout menu) {
        float d = getResources().getDisplayMetrics().density;
        int[] loc = new int[2];
        tile.getLocationOnScreen(loc);
        int menuW = (int) (195 * d);
        int menuH = (int) (menu.getChildCount() * 52 * d + 16 * d);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int x = Math.min(Math.max(loc[0], (int) (16 * d)),
                screenW - menuW - (int) (16 * d));
        boolean flipped = false;
        int y = loc[1] + tile.getHeight() + (int) (4 * d);
        if (y + menuH > screenH) {
            y = loc[1] - menuH - (int) (4 * d);
            flipped = true;
        }
        if (y < 0) y = (int) (16 * d);

        buildDialog(menu, 195, x, y, 0f, flipped ? 1f : 0f);
        menu.post(() -> menu.getChildAt(0).requestFocus());
    }

    /* ---------------- folders ---------------- */

    private void showFolderMenu(View tile, Folder folder) {
        closeMenu();
        float d = getResources().getDisplayMetrics().density;
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);

        addMenuItem(menu, d, R.drawable.ic_folder, "Open", () -> {
            closeMenu();
            HomeItem item = findFolderItem(folder);
            if (item != null) openFolder(item);
        });
        addMenuItem(menu, d, R.drawable.ic_tag, "Rename", () -> {
            closeMenu();
            showRenameDialog(folder);
        });
        addMenuItem(menu, d, R.drawable.ic_restore, "Ungroup folder", () -> {
            closeMenu();
            ungroupFolder(folder);
        });

        showAnchoredMenu(tile, menu);
    }

    private HomeItem findFolderItem(Folder folder) {
        for (HomeItem item : allItems)
            if (item.isFolder() && item.folder == folder) return item;
        return null;
    }

    /** Pick an existing folder or make a new one for this app. */
    private void showFolderPicker(AppEntry app) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Add \"" + app.label + "\" to…");
        title.setTextSize(13f);
        title.setTextColor(0xFFAAB3C5);
        title.setPadding((int) (16 * d), (int) (6 * d),
                (int) (16 * d), (int) (8 * d));
        panel.addView(title);

        addMenuItem(panel, d, R.drawable.ic_folder, "New folder", () -> {
            closeMenu();
            Folder f = new Folder(java.util.UUID.randomUUID().toString(),
                    nextFolderName());
            f.apps.add(app.pkg);
            folders.add(f);
            groupOrder(app.pkg, f);
            saveFolders();
            selectItem(f.key());
            populate();
        });

        for (Folder f : folders) {
            addMenuItem(panel, d, R.drawable.ic_folder,
                    f.name + " (" + f.apps.size() + ")", () -> {
                        closeMenu();
                        moveIntoFolder(app.pkg, f);
                        saveFolders();
                        selectItem(f.key());
                        populate();
                    });
        }

        buildDialog(panel, 300, -1, 0, 0.5f, 0.5f);
        panel.post(() -> {
            if (panel.getChildCount() > 1) panel.getChildAt(1).requestFocus();
        });
    }

    private void moveIntoFolder(String pkg, Folder target) {
        for (Folder f : folders)
            if (f != target) f.apps.remove(pkg);
        if (!target.apps.contains(pkg)) target.apps.add(pkg);
        groupOrder(pkg, target);
    }

    private String nextFolderName() {
        int n = 1;
        while (hasFolderName("Folder " + n)) n++;
        return "Folder " + n;
    }

    private boolean hasFolderName(String name) {
        for (Folder f : folders)
            if (f.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    private void ungroupFolder(Folder folder) {
        ungroupOrder(folder);
        folders.remove(folder);
        saveFolders();
        if (folder.apps.isEmpty()) selectItem(null);
        else selectItem(folder.apps.get(0));
        populate();
    }

    private void showRenameDialog(Folder folder) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Rename folder");
        title.setTextSize(13f);
        title.setTextColor(0xFFAAB3C5);
        title.setPadding((int) (16 * d), (int) (6 * d),
                (int) (16 * d), (int) (8 * d));
        panel.addView(title);

        EditText input = new EditText(this);
        input.setText(folder.name);
        input.setSingleLine(true);
        input.setTextSize(16f);
        input.setTextColor(0xFFE8EDF8);
        input.setSelectAllOnFocus(true);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(0x1FFFFFFF);
        ibg.setCornerRadius(10 * d);
        input.setBackground(ibg);
        input.setPadding((int) (12 * d), (int) (9 * d),
                (int) (12 * d), (int) (9 * d));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins((int) (10 * d), 0, (int) (10 * d), (int) (8 * d));
        panel.addView(input, ilp);

        addMenuItem(panel, d, R.drawable.ic_tag, "Save name", () -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) folder.name = name;
            saveFolders();
            closeMenu();
            populate();
        });

        Dialog dlg = buildDialog(panel, 340, -1, 0, 0.5f, 0.5f);
        Window win = dlg.getWindow();
        if (win != null) {
            win.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        input.post(() -> {
            input.requestFocus();
            input.setSelection(input.getText().length());
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    /** Open a folder as a floating grid of every app inside it. */
    private void openFolder(HomeItem item) {
        View anchor = grid.getSelectedView();
        closeMenu();
        Folder folder = item.folder;
        List<AppEntry> apps = new ArrayList<>(item.folderApps);
        if (apps.isEmpty()) return;

        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);

        // header: accent folder badge + name + count
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int hp = (int) (18 * d);
        header.setPadding(hp, (int) (18 * d), hp, (int) (14 * d));

        FrameLayout badge = new FrameLayout(this);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x266EA8FF);
        badgeBg.setCornerRadius(13 * d);
        badge.setBackground(badgeBg);
        ImageView badgeIcon = new ImageView(this);
        badgeIcon.setImageResource(R.drawable.ic_folder);
        badgeIcon.setColorFilter(0xFF8FB7FF);
        badge.addView(badgeIcon, new FrameLayout.LayoutParams(
                (int) (24 * d), (int) (24 * d), Gravity.CENTER));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                (int) (46 * d), (int) (46 * d));
        blp.setMarginEnd((int) (14 * d));
        header.addView(badge, blp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(folder.name);
        title.setTextSize(20f);
        title.setTextColor(0xFFF1F4FA);
        texts.addView(title);
        TextView sub = new TextView(this);
        sub.setText(apps.size() + (apps.size() == 1 ? " app" : " apps")
                + "  ·  OK to open  ·  long-press to remove");
        sub.setTextSize(12f);
        sub.setTextColor(0xFF8A94AA);
        sub.setPadding(0, (int) (3 * d), 0, 0);
        texts.addView(sub);
        header.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(0x16FFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * d));
        dlp.setMargins(hp, 0, hp, 0);
        panel.addView(divider, dlp);

        int widthDp = Math.min(660,
                (int) (getResources().getDisplayMetrics().widthPixels / d
                        * 0.88f));
        int widthPx = (int) (widthDp * d);
        int contentPad = (int) (8 * d);   // buildDialog's inner padding
        int gridPad = (int) (18 * d);
        int gap = (int) (12 * d);
        int cols = 4;
        int tileW = (widthPx - contentPad * 2 - gridPad * 2
                - gap * (cols - 1)) / cols;
        int tileH = (int) (tileW * 9f / 16f);

        List<HomeItem> folderItems = new ArrayList<>();
        for (AppEntry a : apps) folderItems.add(HomeItem.app(a));

        GridView fg = new GridView(this);
        fg.setNumColumns(cols);
        fg.setColumnWidth(tileW);
        fg.setHorizontalSpacing(gap);
        fg.setVerticalSpacing(gap);
        fg.setStretchMode(GridView.NO_STRETCH);
        fg.setSelector(new ColorDrawable(Color.TRANSPARENT));
        fg.setVerticalScrollBarEnabled(false);
        fg.setClipToPadding(false);
        fg.setPadding(gridPad, 0, gridPad, 0);
        fg.setAdapter(new AppAdapter(this, folderItems, tileW, tileH));

        final View[] last = {null};
        fg.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v,
                                                 int pos, long id) {
                if (last[0] instanceof FocusedTile)
                    ((FocusedTile) last[0]).setFocusedState(false);
                if (v instanceof FocusedTile)
                    ((FocusedTile) v).setFocusedState(true);
                last[0] = v;
            }
            @Override public void onNothingSelected(AdapterView<?> p) {
                if (last[0] instanceof FocusedTile)
                    ((FocusedTile) last[0]).setFocusedState(false);
                last[0] = null;
            }
        });
        fg.setOnItemClickListener((p, v, pos, id) -> {
            closeMenu();
            launch(folderItems.get(pos).app);
        });
        fg.setOnItemLongClickListener((p, v, pos, id) -> {
            showFolderAppMenu(folder, folderItems.get(pos).app);
            return true;
        });

        int rows = (apps.size() + cols - 1) / cols;
        int labelH = (int) (28 * d);
        int visibleRows = Math.min(rows, 3);
        int gridH = visibleRows * (tileH + labelH)
                + (visibleRows - 1) * gap;
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gridH);
        glp.topMargin = (int) (12 * d);
        panel.addView(fg, glp);

        View bottom = new View(this);
        panel.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (18 * d)));

        Dialog dlg = buildDialog(panel, widthDp, -1, 0, 0.5f, 0.5f,
                0.74f, 280, anchor);

        // roomier, modern card + a deeper blur behind it
        GradientDrawable card = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF2282F40, 0xF2191E2A});
        card.setCornerRadius(24 * d);
        card.setStroke((int) (1 * d), 0x3DFFFFFF);
        panel.setBackground(card);
        Window win = dlg.getWindow();
        if (win != null) {
            WindowManager.LayoutParams lp = win.getAttributes();
            if (Build.VERSION.SDK_INT >= 31) lp.setBlurBehindRadius(84);
            lp.dimAmount = 0.44f;
            win.setAttributes(lp);
        }

        // app tiles stagger in after the card blooms open
        final float enterY = 16 * d;
        fg.postDelayed(() -> {
            for (int i = 0; i < fg.getChildCount(); i++) {
                View child = fg.getChildAt(i);
                child.setAlpha(0f);
                child.setTranslationY(enterY);
                child.animate().alpha(1f).translationY(0f)
                        .setStartDelay(90 + 40L * i)
                        .setDuration(260)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .start();
            }
            fg.requestFocus();
            fg.setSelection(0);
        }, 40);
    }

    private void showFolderAppMenu(Folder folder, AppEntry app) {
        closeMenu();
        float d = getResources().getDisplayMetrics().density;
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);

        addMenuItem(menu, d, R.drawable.ic_restore, "Remove from folder", () -> {
            closeMenu();
            boolean wasLast = folder.apps.size() == 1;
            folder.apps.remove(app.pkg);
            if (wasLast) {
                folders.remove(folder);
                if (folder.key().equals(lastItemKey)) selectItem(null);
            }
            removeFromFolderOrder(app.pkg, folder, wasLast);
            saveFolders();
            populate();
        });
        if (app.userInstalled) {
            addMenuItem(menu, d, R.drawable.ic_delete, "Uninstall", () -> {
                closeMenu();
                try {
                    startActivityForResult(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                            Uri.fromParts("package", app.pkg, null)), 0);
                } catch (Exception ignored) {}
            });
        } else {
            addMenuItem(menu, d, R.drawable.ic_info, "App info", () -> {
                closeMenu();
                openAppInfo(app.pkg);
            });
        }

        buildDialog(menu, 240, -1, 0, 0.5f, 0.5f);
        menu.post(() -> menu.getChildAt(0).requestFocus());
    }

    private void selectItem(String key) {
        lastItemKey = key;
        if (key == null) prefs().edit().remove("lastItem").apply();
        else prefs().edit().putString("lastItem", key).apply();
    }

    /* ---------------- wifi dialog ---------------- */

    private void showWifiDialog() {
        closeMenu();
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                getApplicationContext().getSystemService(WIFI_SERVICE);
        android.net.wifi.WifiInfo wi = wm.getConnectionInfo();
        String ssid = readSsid();

        TextView title = new TextView(this);
        title.setText(ssid.isEmpty() ? "Wi-Fi" : ssid);
        title.setTextSize(16f);
        title.setTextColor(0xFFE8EDF8);
        title.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d),
                (int) (4 * d));
        panel.addView(title);

        String band = wi.getFrequency() >= 5000 ? "5 GHz"
                : wi.getFrequency() > 0 ? "2.4 GHz" : "—";
        detailRow(panel, d, "Signal", wi.getRssi() + " dBm");
        detailRow(panel, d, "Band", band + " · " + wi.getLinkSpeed() + " Mbps");
        String ip = "—";
        try {
            for (android.net.LinkAddress la : cm.getLinkProperties(
                    cm.getActiveNetwork()).getLinkAddresses())
                if (la.getAddress() instanceof java.net.Inet4Address) {
                    ip = la.getAddress().getHostAddress();
                    break;
                }
        } catch (Exception ignored) {}
        detailRow(panel, d, "IP", ip);

        View div = new View(this);
        div.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * d));
        dlp.setMargins((int) (10 * d), (int) (6 * d), (int) (10 * d),
                (int) (6 * d));
        panel.addView(div, dlp);

        addMenuItem(panel, d, R.drawable.ic_settings, "Wi-Fi settings", () -> {
            closeMenu();
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } catch (Exception e) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (Exception ignored) {}
            }
        });
        addMenuItem(panel, d, R.drawable.ic_wifi_off, "Disconnect", () -> {
            closeMenu();
            try { wm.disconnect(); } catch (Exception ignored) {}
        });

        View wifi = findViewById(R.id.wifiGroup);
        int[] loc = new int[2];
        wifi.getLocationOnScreen(loc);
        buildDialog(panel, 230,
                loc[0] - (int) (10 * d),
                loc[1] + wifi.getHeight() + (int) (4 * d), 0f, 0f);
        panel.post(() -> {
            for (int i = 0; i < panel.getChildCount(); i++)
                if (panel.getChildAt(i).isFocusable()) {
                    panel.getChildAt(i).requestFocus();
                    break;
                }
        });
    }

    private void detailRow(LinearLayout panel, float d, String k, String v) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding((int) (14 * d), (int) (3 * d), (int) (14 * d),
                (int) (3 * d));
        TextView key = new TextView(this);
        key.setText(k);
        key.setTextSize(12.5f);
        key.setTextColor(0xFF8A94AA);
        row.addView(key, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView val = new TextView(this);
        val.setText(v);
        val.setTextSize(12.5f);
        val.setTextColor(0xFFE8EDF8);
        row.addView(val);
        panel.addView(row);
    }

    /* ---------------- calendar ---------------- */

    /** static month view — today highlighted, anchored under the date */
    private void showCalendarDialog() {
        closeMenu();
        float d = getResources().getDisplayMetrics().density;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int today = cal.get(java.util.Calendar.DAY_OF_MONTH);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(cal.getTime()));
        title.setTextSize(15f);
        title.setTextColor(0xFFE8EDF8);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, (int) (8 * d), 0, (int) (8 * d));
        panel.addView(title);

        // weekday header
        LinearLayout head = new LinearLayout(this);
        for (char w : new char[]{'S', 'M', 'T', 'W', 'T', 'F', 'S'}) {
            TextView wd = new TextView(this);
            wd.setText(String.valueOf(w));
            wd.setTextSize(11.5f);
            wd.setTextColor(0xFF69748F);
            wd.setGravity(Gravity.CENTER);
            head.addView(wd, new LinearLayout.LayoutParams(
                    (int) (34 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        panel.addView(head);

        java.util.Calendar first = (java.util.Calendar) cal.clone();
        first.set(java.util.Calendar.DAY_OF_MONTH, 1);
        int offset = first.get(java.util.Calendar.DAY_OF_WEEK) - 1; // sun=0
        int days = first.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);

        int dayNum = 1 - offset;
        for (int r = 0; r < 6 && dayNum <= days; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int cIdx = 0; cIdx < 7; cIdx++) {
                TextView cell = new TextView(this);
                int cs = (int) (34 * d);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(13f);
                if (dayNum >= 1 && dayNum <= days) {
                    cell.setText(String.valueOf(dayNum));
                    if (dayNum == today) {
                        cell.setTextColor(0xFF0B0D13);
                        GradientDrawable dot = new GradientDrawable();
                        dot.setShape(GradientDrawable.OVAL);
                        dot.setColor(0xFF7A9BFF);
                        cell.setBackground(dot);
                    } else {
                        cell.setTextColor(0xFFAAB3C5);
                    }
                }
                row.addView(cell, new LinearLayout.LayoutParams(cs, cs));
                dayNum++;
            }
            panel.addView(row);
        }

        View date = findViewById(R.id.date);
        int[] loc = new int[2];
        date.getLocationOnScreen(loc);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int panelW = (int) (260 * d);
        int x = Math.min(loc[0] - (int) (40 * d), screenW - panelW - (int) (16 * d));
        buildDialog(panel, 260, x,
                loc[1] + date.getHeight() + (int) (4 * d), 0.5f, 0f);
    }

    private void addMenuItem(LinearLayout menu, float d, int iconRes,
                             String text, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        int ph = (int) (14 * d), pv = (int) (10 * d);
        item.setPadding(ph, pv, ph, pv);
        item.setFocusable(true);
        item.setClickable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        int is = (int) (19 * d);
        item.addView(icon, new LinearLayout.LayoutParams(is, is));

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14.5f);
        label.setTextColor(0xFFE8EDF8);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMarginStart((int) (13 * d));
        item.addView(label, tlp);

        item.setOnFocusChangeListener((v, has) -> {
            GradientDrawable ibg = new GradientDrawable();
            ibg.setColor(has ? 0x2EFFFFFF : 0x00000000);
            ibg.setCornerRadius(10 * d);
            v.setBackground(ibg);
        });
        item.setOnClickListener(v -> action.run());
        menu.addView(item, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void closeMenu() {
        if (menuDlg != null) menuDlg.dismiss();
        menuOpen = false;
        menuDlg = null;
        focusBack();
    }

    /** return focus to the bar icon if one was used, else the grid */
    private void focusBack() {
        if (barFocus != null && barFocus.isShown()) barFocus.requestFocus();
        else { barFocus = null; grid.requestFocus(); }
    }

    /** centered overlay listing hidden apps — OK on one restores it */
    private void showHiddenPanel() {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Hidden apps — OK to restore");
        title.setTextSize(12f);
        title.setTextColor(0xFFAAB3C5);
        title.setPadding((int) (16 * d), (int) (6 * d), (int) (16 * d),
                (int) (10 * d));
        panel.addView(title);

        for (AppEntry a : orderedApps) {
            if (!hiddenPkgs.contains(a.pkg)) continue;
            addMenuItem(panel, d, R.drawable.ic_restore, a.label.toString(), () -> {
                closeMenu();
                hiddenPkgs.remove(a.pkg);
                saveHidden();
                lastPkg = a.pkg;
                selectItem(a.pkg);
                populate();
            });
        }

        buildDialog(panel, 240, -1, 0, 0.5f, 0.5f);
        panel.post(() -> {
            if (panel.getChildCount() > 1) panel.getChildAt(1).requestFocus();
        });
    }

    /* ---------------- move mode ---------------- */

    private void enterMove(int pos) {
        moving = true;
        hint.setText("◄ ▲ ▼ ► to move · OK to place");
        hint.animate().alpha(1f).setDuration(150).start();
    }

    private void exitMove() {
        moving = false;
        hint.animate().alpha(0f).setDuration(150).start();
        saveOrder();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        // the blob reacts to any interaction
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            BuddyView b = findViewById(R.id.buddy);
            if (b != null) switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:   b.walk(-1);    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:  b.walk(1);     break;
                case KeyEvent.KEYCODE_DPAD_UP:     b.look(0, -1); break;
                case KeyEvent.KEYCODE_DPAD_DOWN:   b.look(0, 1);  break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:       b.hop();       break;
                case KeyEvent.KEYCODE_BACK:        b.boop();      break;
                default: b.look(0, 0);
            }
        }
        // while menu is open, trap horizontal nav inside it; BACK dismisses
        if (menuOpen) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK
                    && e.getAction() == KeyEvent.ACTION_DOWN) {
                closeMenu();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        if (moving) {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            int pos = grid.getSelectedItemPosition();
            int cols = grid.getNumColumns();
            int n = allItems.size();
            int np = -1;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (pos % cols > 0) np = pos - 1; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (pos % cols < cols - 1 && pos + 1 < n) np = pos + 1; break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (pos - cols >= 0) np = pos - cols; break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (pos + cols < n) np = pos + cols; break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    exitMove(); return true;
                default: return true;
            }
            if (np >= 0 && np < n) {
                Collections.swap(allItems, pos, np);
                adapter.notifyDataSetChanged();
                grid.setSelection(np);
            }
            return true;
        }

        if (e.getAction() == KeyEvent.ACTION_DOWN
                && e.getKeyCode() == KeyEvent.KEYCODE_MENU
                && lastSelectedTile != null) {
            showMenu(lastSelectedTile, grid.getSelectedItemPosition());
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /* ---------------- persistence ---------------- */

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        Set<String> written = new HashSet<>();
        for (HomeItem item : allItems) {
            sb.append(item.key()).append('\n');
            written.add(item.key());
            if (!item.isFolder()) written.add(item.app.pkg);
        }
        // keep the saved position of hidden apps too, so restoring them
        // doesn't dump them at the end
        for (AppEntry a : orderedApps)
            if (hiddenPkgs.contains(a.pkg) && !written.contains(a.pkg))
                sb.append(a.pkg).append('\n');
        prefs().edit().putString("order", sb.toString()).apply();
    }

    /** Keep the manual order in sync when an app moves into a folder. */
    private void groupOrder(String pkg, Folder folder) {
        if (prefs().getInt(LauncherSettingsActivity.K_SORT, 2) != 2) return;
        ensureOrderSaved();
        List<String> tokens = orderTokens();
        String fk = folder.key();
        int appIdx = tokens.indexOf(pkg);
        if (!tokens.contains(fk)) {
            if (appIdx >= 0) tokens.set(appIdx, fk);
            else tokens.add(fk);
        } else if (appIdx >= 0) {
            tokens.remove(appIdx);
        }
        saveOrderTokens(tokens);
    }

    /** Put a folder's apps back where the folder sat in the manual order. */
    private void ungroupOrder(Folder folder) {
        if (prefs().getInt(LauncherSettingsActivity.K_SORT, 2) != 2) return;
        ensureOrderSaved();
        List<String> tokens = orderTokens();
        int idx = tokens.indexOf(folder.key());
        if (idx >= 0) tokens.remove(idx);
        List<String> add = new ArrayList<>();
        for (String p : folder.apps)
            if (!tokens.contains(p)) add.add(p);
        if (idx >= 0 && idx <= tokens.size()) tokens.addAll(idx, add);
        else tokens.addAll(add);
        saveOrderTokens(tokens);
    }

    /** Put an app back next to (or in place of) the folder it left. */
    private void removeFromFolderOrder(String pkg, Folder folder,
                                       boolean folderRemoved) {
        if (prefs().getInt(LauncherSettingsActivity.K_SORT, 2) != 2) return;
        ensureOrderSaved();
        List<String> tokens = orderTokens();
        tokens.remove(pkg);
        int idx = tokens.indexOf(folder.key());
        if (folderRemoved) {
            if (idx >= 0) tokens.remove(idx);
            if (idx < 0 || idx > tokens.size()) idx = tokens.size();
        } else {
            idx = idx >= 0 ? idx + 1 : tokens.size();
        }
        tokens.add(idx, pkg);
        saveOrderTokens(tokens);
    }

    private void ensureOrderSaved() {
        if (!prefs().getString("order", "").isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (HomeItem item : allItems) sb.append(item.key()).append('\n');
        prefs().edit().putString("order", sb.toString()).apply();
    }

    private List<String> orderTokens() {
        List<String> tokens = new ArrayList<>();
        for (String raw : prefs().getString("order", "").split("\n")) {
            String t = raw.trim();
            if (!t.isEmpty()) tokens.add(t);
        }
        return tokens;
    }

    private void saveOrderTokens(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) sb.append(t).append('\n');
        prefs().edit().putString("order", sb.toString()).apply();
    }

    private void saveFolders() {
        JSONArray arr = new JSONArray();
        for (Folder f : folders) arr.put(f.toJson());
        prefs().edit().putString("folders", arr.toString()).apply();
    }

    private void loadFolders() {
        folders.clear();
        try {
            JSONArray arr = new JSONArray(prefs().getString("folders", "[]"));
            for (int i = 0; i < arr.length(); i++)
                folders.add(Folder.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
    }

    private void saveHidden() {
        StringBuilder sb = new StringBuilder();
        for (String p : hiddenPkgs) sb.append(p).append('\n');
        prefs().edit().putString("hidden", sb.toString()).apply();
    }

    private List<AppEntry> applySavedOrder(List<AppEntry> sorted) {
        String saved = prefs().getString("order", "");
        if (saved.isEmpty()) return sorted;
        Map<String, AppEntry> byPkg = new LinkedHashMap<>();
        for (AppEntry a : sorted) byPkg.put(a.pkg, a);
        List<AppEntry> out = new ArrayList<>();
        for (String p : saved.split("\n")) {
            AppEntry e = byPkg.remove(p.trim());
            if (e != null) out.add(e);
        }
        out.addAll(byPkg.values());   // newly installed apps go last
        return out;
    }

    /* ---------------- apps ---------------- */

    private void updateDate() {
        ((TextView) findViewById(R.id.date)).setText(
                new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                        .format(new Date()));
    }

    private void launch(AppEntry app) {
        try { startActivity(app.intent); } catch (Exception ignored) {}
        Map<String, Integer> uses = loadUses();
        Integer n = uses.get(app.pkg);
        uses.put(app.pkg, n == null ? 1 : n + 1);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : uses.entrySet())
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        prefs().edit().putString("uses", sb.toString()).apply();
    }

    private Map<String, Integer> loadUses() {
        Map<String, Integer> m = new HashMap<>();
        for (String line : prefs().getString("uses", "").split("\n")) {
            int i = line.lastIndexOf('=');
            if (i < 1) continue;
            try {
                m.put(line.substring(0, i),
                        Integer.parseInt(line.substring(i + 1).trim()));
            } catch (NumberFormatException ignored) {}
        }
        return m;
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", pkg, null)));
        } catch (Exception ignored) {}
    }

    private void loadApps() {
        new Thread(() -> {
            List<AppEntry> apps = queryApps();
            runOnUiThread(() -> {
                orderedApps = apps;
                populate();
            });
        }, "app-loader").start();
    }

    private List<AppEntry> queryApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppEntry> found = new LinkedHashMap<>();
        addCategory(pm, found, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addCategory(pm, found, Intent.CATEGORY_LAUNCHER);

        List<AppEntry> list = new ArrayList<>(found.values());
        list.removeIf(a -> a.pkg.equals(getPackageName()));

        int sort = prefs().getInt(LauncherSettingsActivity.K_SORT, 2);
        if (sort == 0) {            // A–Z
            list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                    a.label.toString(), b.label.toString()));
            return list;
        }
        if (sort == 1) {            // most used — launch counts; ties keep
                                    // user apps first, then alphabetical
            Map<String, Integer> uses = loadUses();
            list.sort((a, b) -> {
                int ua = uses.getOrDefault(a.pkg, 0);
                int ub = uses.getOrDefault(b.pkg, 0);
                if (ua != ub) return ub - ua;
                if (a.userInstalled != b.userInstalled)
                    return a.userInstalled ? -1 : 1;
                return String.CASE_INSENSITIVE_ORDER.compare(
                        a.label.toString(), b.label.toString());
            });
            return list;
        }
        // manual — your apps first alphabetically, then the saved drag order
        list.sort((a, b) -> a.userInstalled != b.userInstalled
                ? (a.userInstalled ? -1 : 1)
                : String.CASE_INSENSITIVE_ORDER.compare(
                        a.label.toString(), b.label.toString()));
        return applySavedOrder(list);
    }

    private void addCategory(PackageManager pm, Map<String, AppEntry> found,
                             String category) {
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (found.containsKey(pkg)) continue;
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.setClassName(pkg, ri.activityInfo.name);
            }
            launch.addCategory(Intent.CATEGORY_LAUNCHER);

            Drawable banner = null, icon = null;
            try { banner = ri.activityInfo.loadBanner(pm); } catch (Throwable ignored) {}
            try { icon = ri.activityInfo.loadIcon(pm); } catch (Throwable ignored) {}

            boolean user = (ri.activityInfo.applicationInfo.flags
                    & ApplicationInfo.FLAG_SYSTEM) == 0;
            found.put(pkg, new AppEntry(pkg, ri.loadLabel(pm), icon, banner,
                    launch, user));
        }
    }


    private void populate() {
        allItems = buildItems();
        findViewById(R.id.settingsBtn).setVisibility(
                hiddenPkgs.contains(SETTINGS_PKG) ? View.GONE : View.VISIBLE);

        float d = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int sidePad = (int) (64 * d) * 2;
        int gap = (int) (18 * d);
        int cols = 6;
        int tileW = (screenW - sidePad - gap * (cols - 1)) / cols;
        int tileH = (int) (tileW * 9f / 16f);

        adapter = new AppAdapter(this, allItems, tileW, tileH);
        grid.setAdapter(adapter);

        int sel = 0;
        boolean found = false;
        if (lastItemKey != null) {
            for (int i = 0; i < allItems.size(); i++) {
                if (allItems.get(i).key().equals(lastItemKey)) {
                    sel = i;
                    found = true;
                    break;
                }
            }
        }
        if (!found && lastPkg != null) {
            for (int i = 0; i < allItems.size(); i++) {
                HomeItem item = allItems.get(i);
                if (!item.isFolder() && item.app.pkg.equals(lastPkg)) {
                    sel = i;
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (int i = 0; i < allItems.size(); i++) {
                    HomeItem item = allItems.get(i);
                    if (item.isFolder() && item.folder.apps.contains(lastPkg)) {
                        sel = i;
                        break;
                    }
                }
            }
        }
        final int fsel = sel;
        grid.postDelayed(() -> {
            grid.setSelection(fsel);
            if (barFocus != null && barFocus.isShown()) {
                barFocus.requestFocus();   // keep bar icons focused on return
            } else {
                grid.requestFocus();
                View v = grid.getSelectedView();
                if (v instanceof FocusedTile) {
                    ((FocusedTile) v).setFocusedState(true);
                    lastSelectedTile = v;
                }
            }
        }, 80);
    }

    /** Merge apps and folders into the ordered list the grid renders. */
    private List<HomeItem> buildItems() {
        Map<String, AppEntry> byPkg = new HashMap<>();
        for (AppEntry a : orderedApps) byPkg.put(a.pkg, a);

        Set<String> grouped = new HashSet<>();
        for (Folder f : folders)
            for (String p : f.apps) grouped.add(p);

        List<HomeItem> items = new ArrayList<>();
        for (AppEntry a : orderedApps) {
            if (hiddenPkgs.contains(a.pkg) || a.pkg.equals(SETTINGS_PKG))
                continue;
            if (grouped.contains(a.pkg)) continue;
            items.add(HomeItem.app(a));
        }

        for (Folder f : folders) {
            List<AppEntry> members = new ArrayList<>();
            for (String p : f.apps) {
                AppEntry a = byPkg.get(p);
                if (a != null && !hiddenPkgs.contains(p)
                        && !p.equals(SETTINGS_PKG))
                    members.add(a);
            }
            if (!members.isEmpty()) items.add(HomeItem.folder(f, members));
        }

        int sort = prefs().getInt(LauncherSettingsActivity.K_SORT, 2);
        if (sort == 0) {
            items.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                    a.label().toString(), b.label().toString()));
        } else if (sort == 1) {
            Map<String, Integer> uses = loadUses();
            items.sort((a, b) -> {
                int ua = a.usage(uses), ub = b.usage(uses);
                if (ua != ub) return ub - ua;
                return String.CASE_INSENSITIVE_ORDER.compare(
                        a.label().toString(), b.label().toString());
            });
        } else {
            items = applySavedItemOrder(items);
        }
        return items;
    }

    /** Manual order tokens: package name for apps, folder:<id> for folders. */
    private List<HomeItem> applySavedItemOrder(List<HomeItem> items) {
        String saved = prefs().getString("order", "");
        if (saved.isEmpty()) return items;
        Map<String, HomeItem> pending = new LinkedHashMap<>();
        for (HomeItem item : items) pending.put(item.key(), item);
        List<HomeItem> out = new ArrayList<>();
        for (String raw : saved.split("\n")) {
            String key = raw.trim();
            if (key.isEmpty()) continue;
            HomeItem item = pending.remove(key);
            if (item != null) out.add(item);
        }
        out.addAll(pending.values());
        return out;
    }
}
