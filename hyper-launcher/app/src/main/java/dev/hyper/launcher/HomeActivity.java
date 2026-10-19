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
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HomeActivity extends Activity {

    private static final String SETTINGS_PKG = "com.android.tv.settings";
    private static final String QUICK_PKG = "com.tcl.suspension";

    private GridView grid;
    private TextView hint;
    private AppAdapter adapter;
    private List<AppEntry> orderedApps = new ArrayList<>();
    private List<AppEntry> allApps = new ArrayList<>();
    private final Set<String> hiddenPkgs = new HashSet<>();
    private View lastSelectedTile;
    private Dialog menuDlg;
    private boolean menuOpen, moving;
    private String lastPkg;
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
        for (String p : prefs().getString("hidden", "").split("\n")) {
            p = p.trim();
            if (!p.isEmpty()) hiddenPkgs.add(p);
        }
        lastPkg = prefs().getString("last", null);
        updateDate();

        ImageView gear = findViewById(R.id.settingsBtn);
        gear.setOnClickListener(v -> {
            for (AppEntry a : orderedApps)
                if (a.pkg.equals(QUICK_PKG)) { launch(a); return; }
            for (AppEntry a : orderedApps)
                if (a.pkg.equals(SETTINGS_PKG)) { launch(a); return; }
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
            if (has && lastSelectedTile instanceof AppTile)
                ((AppTile) lastSelectedTile).setFocusedState(false);
        });

        View date = findViewById(R.id.date);
        date.setOnClickListener(v -> showCalendarDialog());
        date.setOnFocusChangeListener((v, has) -> {
            GradientDrawable db = new GradientDrawable();
            db.setColor(has ? 0x2EFFFFFF : 0x00000000);
            db.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            v.setBackground(db);
            ((TextView) v).setTextColor(has ? 0xFFE8EDF8 : 0xFF8A94AA);
            if (has && lastSelectedTile instanceof AppTile)
                ((AppTile) lastSelectedTile).setFocusedState(false);
        });

        View weather = findViewById(R.id.weather);
        weather.setOnClickListener(v -> showWeatherDialog());
        weather.setOnFocusChangeListener((v, has) -> {
            GradientDrawable tb = new GradientDrawable();
            tb.setColor(has ? 0x2EFFFFFF : 0x00000000);
            tb.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            v.setBackground(tb);
            ((TextView) v).setTextColor(has ? 0xFFE8EDF8 : 0xFF8A94AA);
            if (has && lastSelectedTile instanceof AppTile)
                ((AppTile) lastSelectedTile).setFocusedState(false);
        });

        View wifi = findViewById(R.id.wifiGroup);
        wifi.setOnClickListener(v -> showWifiDialog());
        wifi.setOnFocusChangeListener((v, has) -> {
            GradientDrawable wb = new GradientDrawable();
            wb.setColor(has ? 0x2EFFFFFF : 0x00000000);
            wb.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            v.setBackground(wb);
            if (has && lastSelectedTile instanceof AppTile)
                ((AppTile) lastSelectedTile).setFocusedState(false);
        });
        // re-paint focus when it comes back to the grid
        grid.setOnFocusChangeListener((v, has) -> {
            if (has) {
                View sel = grid.getSelectedView();
                if (sel instanceof AppTile) {
                    ((AppTile) sel).setFocusedState(true);
                    lastSelectedTile = sel;
                }
            }
        });

        grid.setOnItemClickListener((p, v, pos, id) -> {
            if (!moving && !menuOpen) launch(allApps.get(pos));
        });
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            if (!moving && pos < allApps.size()) showMenu(v, pos);
            return true;
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (lastSelectedTile instanceof AppTile)
                    ((AppTile) lastSelectedTile).setFocusedState(false);
                if (v instanceof AppTile)
                    ((AppTile) v).setFocusedState(true);
                lastSelectedTile = v;
                if (pos < allApps.size()) {
                    lastPkg = allApps.get(pos).pkg;
                    prefs().edit().putString("last", lastPkg).apply();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {
                if (lastSelectedTile instanceof AppTile)
                    ((AppTile) lastSelectedTile).setFocusedState(false);
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
        restoreWeather();          // paint last known weather instantly
        handler.post(weatherTick);
    }

    private void updateWifi() {
        View group = findViewById(R.id.wifiGroup);
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
        lp.setBlurBehindRadius(48);
        lp.dimAmount = 0.28f;
        w.setAttributes(lp);
        dlg.setOnDismissListener(dd -> {
            menuOpen = false;
            menuDlg = null;
            grid.requestFocus();
        });
        menuOpen = true;
        menuDlg = dlg;
        dlg.show();

        // bloom from the anchor point once the card is laid out
        content.setAlpha(0f);
        content.post(() -> {
            content.setPivotX(content.getWidth() * pivotXFrac);
            content.setPivotY(content.getHeight() * pivotYFrac);
            content.setScaleX(0.88f);
            content.setScaleY(0.88f);
            content.setTranslationY(pivotYFrac > 0 ? 10 * d : -10 * d);
            content.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(170)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();
        });
        return dlg;
    }

    private void showMenu(View tile, int pos) {
        AppEntry app = allApps.get(pos);
        closeMenu();

        float d = getResources().getDisplayMetrics().density;
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);

        addMenuItem(menu, d, R.drawable.ic_move, "Move", () -> {
            closeMenu();
            enterMove(pos);
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

        // anchor near the tile, clamped on screen
        int[] loc = new int[2];
        tile.getLocationOnScreen(loc);
        int menuW = (int) (170 * d);
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
        grid.requestFocus();
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
            int n = allApps.size();
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
                Collections.swap(allApps, pos, np);
                Collections.swap(orderedApps, pos, np);
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
        for (AppEntry a : orderedApps) sb.append(a.pkg).append('\n');
        prefs().edit().putString("order", sb.toString()).apply();
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
        // your apps first, then everything else — both alphabetical
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
        List<AppEntry> display = new ArrayList<>();
        for (AppEntry a : orderedApps)
            if (!hiddenPkgs.contains(a.pkg) && !a.pkg.equals(SETTINGS_PKG))
                display.add(a);
        allApps = display;
        findViewById(R.id.settingsBtn).setVisibility(
                hiddenPkgs.contains(SETTINGS_PKG) ? View.GONE : View.VISIBLE);

        float d = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int sidePad = (int) (64 * d) * 2;
        int gap = (int) (18 * d);
        int cols = 6;
        int tileW = (screenW - sidePad - gap * (cols - 1)) / cols;
        int tileH = (int) (tileW * 9f / 16f);

        adapter = new AppAdapter(this, display, tileW, tileH);
        grid.setAdapter(adapter);

        int sel = 0;
        if (lastPkg != null) {
            for (int i = 0; i < display.size(); i++)
                if (display.get(i).pkg.equals(lastPkg)) { sel = i; break; }
        }
        final int fsel = sel;
        grid.postDelayed(() -> {
            grid.setSelection(fsel);
            grid.requestFocus();
            View v = grid.getSelectedView();
            if (v instanceof AppTile) {
                ((AppTile) v).setFocusedState(true);
                lastSelectedTile = v;
            }
        }, 80);
    }
}
