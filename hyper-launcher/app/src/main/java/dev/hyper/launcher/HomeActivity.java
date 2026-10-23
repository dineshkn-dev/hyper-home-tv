package dev.hyper.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


public final class HomeActivity extends Activity implements HomeFocusRouter.Host {

    private static final String SETTINGS_PKG = "com.android.tv.settings";
    private static final int REQ_TV_LISTINGS = 41;
    private static final String PERM_READ_TV_LISTINGS =
            "android.permission.READ_TV_LISTINGS";
    private static final int[] TOP_ACTION_IDS = {
            R.id.searchBtn, R.id.wifiBtn, R.id.settingsBtn
    };


    private LinearLayout pinnedGrid;
    private TextView manageAppsLink;
    private ImageView brandBtn;
    private FeatureStackView heroStack;
    private List<String> pinnedPkgs = new ArrayList<>();
    private List<AppEntry> pinnedResolved = new ArrayList<>();
    private List<WatchNextItem> continueItems = new ArrayList<>();
    private boolean askedTvListings, continueLoading;
    private List<AppEntry> orderedApps = new ArrayList<>();
    private List<HomeItem> allItems = new ArrayList<>();
    private final Set<String> hiddenPkgs = new HashSet<>();
    private View lastSelectedTile;
    private View barFocus;   // top-bar element that last held focus
    private String lastPkg;
    private int restoreFocusId = View.NO_ID;
    private boolean barFocusRestored;
    private HomeFocusRouter focusRouter;
    private HomeDialogs dialogs;
    private ConnectivityManager cm;
    private boolean interactedSinceLoad;
    private int appFocusRequest;
    private final ConnectivityManager.NetworkCallback netCb =
            new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { updateWifi(); }
        @Override public void onLost(Network n) { updateWifi(); }
        @Override public void onCapabilitiesChanged(Network n,
                NetworkCapabilities c) { updateWifi(); }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            loadApps();
            loadContinueWatching();
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_home);
        for (String p : prefs().getString("hidden", "").split("\n")) {
            p = p.trim();
            if (!p.isEmpty()) hiddenPkgs.add(p);
        }
        lastPkg = prefs().getString("last", null);
        updateDate();

        pinnedGrid = findViewById(R.id.pinnedGrid);
        manageAppsLink = findViewById(R.id.manageAppsLink);
        brandBtn = findViewById(R.id.brandBtn);
        heroStack = findViewById(R.id.heroStack);
        pinnedPkgs = PinnedApps.load(prefs());

        setupAction(findViewById(R.id.searchBtn), () -> dialogs.showSearch());
        setupAction(findViewById(R.id.wifiBtn), () -> dialogs.showWifiDialog());
        setupAction(findViewById(R.id.settingsBtn), () -> {
            restoreFocusId = R.id.settingsBtn;
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) {}
        });

        if (brandBtn != null) {
            brandBtn.setOnClickListener(v -> {
                restoreFocusId = R.id.brandBtn;
                startActivity(new Intent(this, LauncherSettingsActivity.class));
            });
            brandBtn.setOnFocusChangeListener((v, has) -> {
                FocusGlow.apply(v, has, 1.12f);
                if (has) barFocus = null;
            });
        }

        styleLink(manageAppsLink);
        manageAppsLink.setOnClickListener(v -> {
            restoreFocusId = R.id.manageAppsLink;
            startActivity(new Intent(this, AppDrawerActivity.class));
        });

        heroStack.setOnClickListener(v -> {
            FeatureItem item = heroStack.getCurrent();
            if (item != null) launchFeature(item);
        });

        dialogs = new HomeDialogs(this, new HomeDialogs.Host() {
            @Override public List<AppEntry> visibleApps() {
                List<AppEntry> apps = new ArrayList<>();
                for (AppEntry app : orderedApps)
                    if (!hiddenPkgs.contains(app.pkg)
                            && !app.pkg.equals(SETTINGS_PKG)) apps.add(app);
                return apps;
            }
            @Override public void launch(AppEntry app) {
                HomeActivity.this.launch(app);
            }
            @Override public void openAppInfo(String pkg) {
                HomeActivity.this.openAppInfo(pkg);
            }
            @Override public void uninstall(AppEntry app) {
                HomeActivity.this.uninstall(app);
            }
            @Override public boolean isPinned(String pkg) {
                return isPinnedOnHome(pkg);
            }
            @Override public void pin(AppEntry app) { pinApp(app.pkg); }
            @Override public void unpin(AppEntry app) { unpinApp(app.pkg); }
            @Override public void focusBack() { HomeActivity.this.focusBack(); }
            @Override public String ssid() { return readSsid(); }
            @Override public ConnectivityManager cm() { return cm; }
        });

        focusRouter = new HomeFocusRouter(this);
    }

    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_TV_LISTINGS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContinueWatching();
            } else {
                clearContinueDeck();
            }
        }
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("hyper", MODE_PRIVATE);
    }

    @Override protected void onResume() {
        super.onResume();
        barFocusRestored = false;
        updateDate();
        // Settings and Manage Apps can change pins and hidden apps.
        pinnedPkgs = PinnedApps.load(prefs());
        hiddenPkgs.clear();
        for (String p : prefs().getString("hidden", "").split("\n")) {
            p = p.trim();
            if (!p.isEmpty()) hiddenPkgs.add(p);
        }
        lastPkg = prefs().getString("last", lastPkg);
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
    }

    /** settings from the Launcher page — applied each time we resume */
    private void applyPrefs() {
        android.content.SharedPreferences p = prefs();
        boolean seconds = p.getBoolean(
                LauncherSettingsActivity.K_SECONDS, false);
        android.widget.TextClock clock = findViewById(R.id.clock);
        clock.setFormat12Hour(seconds ? "h:mm:ss a" : "h:mm a");
        clock.setFormat24Hour(seconds ? "h:mm:ss a" : "h:mm a");

        if (p.getBoolean(LauncherSettingsActivity.K_CONTINUE, false))
            loadContinueWatching();
        else
            clearContinueDeck();
    }

    private void updateWifi() {
        ImageView ic = findViewById(R.id.wifiBtn);
        if (ic == null || cm == null) return;
        NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
        runOnUiThread(() -> ic.setColorFilter(
                c == null ? 0xFFFF6B6B : 0xFF9A9A9A));
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
        View focused = getCurrentFocus();
        if (focused != null && focused.getId() != View.NO_ID)
            restoreFocusId = focused.getId();
        if (dialogs != null && dialogs.isOpen()) dialogs.close();
        try { unregisterReceiver(pkgReceiver); } catch (IllegalArgumentException ignored) {}
        if (cm != null) cm.unregisterNetworkCallback(netCb);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && (restoreFocusId != View.NO_ID || getCurrentFocus() == null))
            pinnedGrid.postDelayed(this::focusHome, 40);
    }

    @Override public void onBackPressed() {
        if (dialogs != null && dialogs.isOpen()) dialogs.close();
        // otherwise home shouldn't go back
    }

    /** return focus to the bar icon if one was used, else the active tile */
    private void focusBack() {
        if (barFocus != null && barFocus.isShown()) {
            barFocus.requestFocus();
            return;
        }
        barFocus = null;
        focusHome();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            interactedSinceLoad = true;
            appFocusRequest++;
        }

        // while a dialog is open, trap horizontal nav inside it; BACK dismisses
        if (dialogs != null && dialogs.isOpen()) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK
                    && e.getAction() == KeyEvent.ACTION_DOWN) {
                dialogs.close();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        if (focusRouter != null && focusRouter.handle(e)) return true;

        if (e.getAction() == KeyEvent.ACTION_DOWN
                && e.getKeyCode() == KeyEvent.KEYCODE_MENU
                && lastSelectedTile instanceof PinnedAppTile) {
            PinnedAppTile tile = (PinnedAppTile) lastSelectedTile;
            dialogs.showAppMenu(tile, tile.app);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /* ---------------- home focus routing ---------------- */

    private boolean focusView(View v) {
        if (v == null || v.getVisibility() != View.VISIBLE || !v.isShown())
            return false;
        return v.requestFocus();
    }

    // ---------- HomeFocusRouter.Host ----------

    @Override public View brand() { return brandBtn; }
    @Override public View hero() { return heroStack; }
    @Override public View manage() { return manageAppsLink; }
    @Override public int topActionCount() { return TOP_ACTION_IDS.length; }
    @Override public int pinnedCount() { return pinnedResolved.size(); }
    @Override public boolean focus(View v) { return focusView(v); }
    @Override public boolean deckForward() { return heroStack != null && heroStack.goForward(); }
    @Override public boolean deckBack() { return heroStack != null && heroStack.goBack(); }

    @Override public View topAction(int index) {
        if (index < 0 || index >= TOP_ACTION_IDS.length) return null;
        return findViewById(TOP_ACTION_IDS[index]);
    }

    @Override public int topActionIndex(View v) {
        if (v == null) return -1;
        for (int i = 0; i < TOP_ACTION_IDS.length; i++)
            if (v.getId() == TOP_ACTION_IDS[i]) return i;
        return -1;
    }

    @Override public View pinnedTile(int index) {
        if (index < 0 || index >= pinnedResolved.size()) return null;
        return findPinnedTile(pinnedResolved.get(index).pkg);
    }

    @Override public int pinnedIndex(View v) {
        if (v == null) return -1;
        for (int i = 0; i < pinnedResolved.size(); i++)
            if (findPinnedTile(pinnedResolved.get(i).pkg) == v) return i;
        return -1;
    }

    @Override public void focusFirstApp() {
        final int request = ++appFocusRequest;
        focusFirstAppAttempt(request, 0, getCurrentFocus());
    }

    private void focusFirstAppAttempt(int request, int attempt, View origin) {
        if (request != appFocusRequest) return;
        View current = getCurrentFocus();
        if (current != null && current != origin) return;

        View first = firstPinnedTile();
        if (focusView(first)) return;

        if (attempt >= 7) {
            if (current == null || current == origin)
                focusView(topAction(0));
            return;
        }
        pinnedGrid.postDelayed(() -> {
            if (request != appFocusRequest) return;
            focusFirstAppAttempt(request, attempt + 1, origin);
        }, 70);
    }

    /* ---------------- continue watching ---------------- */

    private void loadContinueWatching() {
        if (!prefs().getBoolean(LauncherSettingsActivity.K_CONTINUE, false)) {
            clearContinueDeck();
            return;
        }
        if (checkSelfPermission(PERM_READ_TV_LISTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            clearContinueDeck();
            if (!askedTvListings && !isFinishing()) {
                askedTvListings = true;
                requestPermissions(new String[]{
                        PERM_READ_TV_LISTINGS}, REQ_TV_LISTINGS);
            }
            return;
        }
        if (continueLoading) return;
        continueLoading = true;
        new Thread(() -> {
            List<WatchNextItem> items = WatchNextRepository.load(this);
            runOnUiThread(() -> {
                continueLoading = false;
                if (isFinishing() || isDestroyed()) return;
                if (items.isEmpty()) {
                    clearContinueDeck();
                } else {
                    continueItems = new ArrayList<>(items);
                    bindFeaturedStack();
                }
            });
        }, "watch-next").start();
    }

    private void clearContinueDeck() {
        continueItems.clear();
        bindFeaturedStack();
    }

    private void launchWatchNext(WatchNextItem item) {
        try {
            Intent intent = item.intentUri.isEmpty()
                    ? getPackageManager().getLaunchIntentForPackage(item.packageName)
                    : Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME);
            if (intent == null) return;
            startActivity(intent);
            if (!item.packageName.isEmpty()) {
                lastPkg = item.packageName;
                prefs().edit().putString("last", lastPkg).apply();
                recordUse(item.packageName);
            }
        } catch (Exception ignored) {}
    }

    private void setupAction(ImageView v, Runnable action) {
        if (v == null) return;
        v.setColorFilter(0xFF9A9A9A);
        v.setOnClickListener(x -> action.run());
        v.setOnFocusChangeListener((x, has) -> {
            FocusGlow.apply(x, has, 1.16f);
            ((ImageView) x).setColorFilter(has ? 0xFFF5F5F5 : 0xFF9A9A9A);
            if (has) {
                barFocus = x;
                if (lastSelectedTile instanceof FocusedTile)
                    ((FocusedTile) lastSelectedTile).setFocusedState(false);
            }
        });
    }

    private void styleLink(TextView v) {
        if (v == null) return;
        v.setOnFocusChangeListener((x, has) -> {
            v.setTextColor(has ? 0xFFC8FF3D : 0xFF9A9A9A);
            v.animate().scaleX(has ? 1.06f : 1f)
                    .scaleY(has ? 1.06f : 1f)
                    .setDuration(160).start();
            if (has) {
                barFocus = null;
                if (lastSelectedTile instanceof FocusedTile)
                    ((FocusedTile) lastSelectedTile).setFocusedState(false);
            }
        });
    }

    private void refreshPinnedApps() {
        ensurePinsSeeded();
        pinnedResolved = PinnedApps.resolvePinned(allItems, pinnedPkgs);
        if (pinnedGrid == null) return;
        float d = getResources().getDisplayMetrics().density;
        int contentW = getResources().getDisplayMetrics().widthPixels
                - (int) (128 * d);
        int gap = (int) (16 * d);

        pinnedGrid.removeAllViews();
        pinnedGrid.setGravity(Gravity.CENTER_HORIZONTAL);

        int count = pinnedResolved.size();
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("Pin apps from View all apps");
            empty.setTextColor(0xFF5A5A5A);
            empty.setTextSize(13f);
            empty.setPadding(0, (int) (14 * d), 0, (int) (14 * d));
            pinnedGrid.addView(empty);
            return;
        }

        int preferredW = (contentW - gap * (count - 1)) / count;
        int tileW = Math.min(preferredW, contentW / 4);
        int artH = (int) (tileW * 9f / 16f);

        for (int i = 0; i < pinnedResolved.size(); i++) {
            AppEntry app = pinnedResolved.get(i);
            PinnedAppTile tile = new PinnedAppTile(this, app, tileW, artH);
            tile.setOnClickListener(v -> launch(app));
            tile.setOnLongClickListener(v -> {
                dialogs.showAppMenu(tile, app);
                return true;
            });
            tile.setOnFocusChangeListener((v, has) -> {
                tile.setFocusedState(has);
                if (has) {
                    barFocus = null;
                    if (lastSelectedTile instanceof FocusedTile
                            && lastSelectedTile != tile)
                        ((FocusedTile) lastSelectedTile).setFocusedState(false);
                    lastSelectedTile = tile;
                    lastPkg = app.pkg;
                    prefs().edit().putString("last", lastPkg).apply();
                } else if (lastSelectedTile == tile) {
                    lastSelectedTile = null;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    tileW, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(gap);
            pinnedGrid.addView(tile, lp);
        }

        if (continueItems.isEmpty()) bindFeaturedStack();
    }

    private void ensurePinsSeeded() {
        if (prefs().getBoolean("pinsSeeded", false) || allItems.isEmpty()) return;
        if (PinnedApps.load(prefs()).isEmpty()) {
            List<AppEntry> initial = PinnedApps.resolve(
                    allItems, new ArrayList<>(), loadUses());
            List<String> pkgs = new ArrayList<>();
            for (AppEntry app : initial) pkgs.add(app.pkg);
            if (!pkgs.isEmpty()) {
                PinnedApps.save(prefs(), pkgs);
                pinnedPkgs = pkgs;
            }
        }
        prefs().edit().putBoolean("pinsSeeded", true).apply();
    }

    private void focusHome() {
        if (barFocusRestored && getCurrentFocus() != null) return;
        if (restoreFocusId != View.NO_ID) {
            View restore = findViewById(restoreFocusId);
            if (focusView(restore)) {
                restoreFocusId = View.NO_ID;
                barFocusRestored = true;
                barFocus = topActionIndex(restore) >= 0 ? restore : null;
                return;
            }
            return;
        }
        if (barFocus != null && barFocus.isShown()) {
            barFocus.requestFocus();
            return;
        }
        View target = findPinnedTile(lastPkg);
        if (target == null) target = firstPinnedTile();
        if (target == null) target = heroStack;
        if (target != null) target.requestFocus();
    }

    private View firstPinnedTile() {
        for (int i = 0; i < pinnedGrid.getChildCount(); i++)
            if (pinnedGrid.getChildAt(i) instanceof PinnedAppTile)
                return pinnedGrid.getChildAt(i);
        return null;
    }

    private View findPinnedTile(String pkg) {
        if (pkg == null) return null;
        for (int i = 0; i < pinnedGrid.getChildCount(); i++) {
            View v = pinnedGrid.getChildAt(i);
            if (v instanceof PinnedAppTile
                    && pkg.equals(((PinnedAppTile) v).app.pkg)) return v;
        }
        return null;
    }

    private boolean isPinnedOnHome(String pkg) {
        for (AppEntry app : pinnedResolved)
            if (app.pkg.equals(pkg)) return true;
        return false;
    }

    private void pinApp(String pkg) {
        if (!pinnedPkgs.contains(pkg) && pinnedPkgs.size() >= PinnedApps.MAX) {
            Toast.makeText(this, "Home is full (6)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pinnedPkgs.contains(pkg)) pinnedPkgs.add(pkg);
        PinnedApps.save(prefs(), pinnedPkgs);
        refreshPinnedApps();
        focusHome();
    }

    private void unpinApp(String pkg) {
        if (!pinnedPkgs.contains(pkg)) {
            List<String> explicit = new ArrayList<>();
            for (AppEntry app : pinnedResolved) explicit.add(app.pkg);
            pinnedPkgs = explicit;
        }
        pinnedPkgs.remove(pkg);
        PinnedApps.save(prefs(), pinnedPkgs);
        refreshPinnedApps();
        focusHome();
    }

    private void bindFeaturedStack() {
        if (heroStack == null) return;
        List<FeatureItem> deck = FeatureItems.deck(this, continueItems, pinnedResolved);

        String previous = heroStack.getCurrent() == null
                ? null : heroStack.getCurrent().title;
        int start = 0;
        if (previous != null) {
            for (int i = 0; i < deck.size(); i++)
                if (deck.get(i).title.equals(previous)) { start = i; break; }
        }
        heroStack.setItems(deck, start);
    }

    private void launchFeature(FeatureItem item) {
        if (item == null) return;
        if (item.watchNext != null) launchWatchNext(item.watchNext);
        else if (item.app != null) launch(item.app);
    }

    /* ---------------- apps ---------------- */

    private void updateDate() {
        ((TextView) findViewById(R.id.date)).setText(
                new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                        .format(new Date()).toUpperCase(Locale.getDefault()));
    }

    private void launch(AppEntry app) {
        try { startActivity(app.intent); } catch (Exception ignored) {}
        recordUse(app.pkg);
    }

    private void recordUse(String pkg) {
        Map<String, Integer> uses = loadUses();
        Integer n = uses.get(pkg);
        uses.put(pkg, n == null ? 1 : n + 1);
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

    private void uninstall(AppEntry app) {
        try {
            startActivityForResult(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                    Uri.fromParts("package", app.pkg, null)), 0);
        } catch (Exception ignored) {}
    }

    private void loadApps() {
        interactedSinceLoad = false;
        new Thread(() -> {
            List<AppEntry> apps = AppCatalog.launchable(this);
            runOnUiThread(() -> {
                orderedApps = apps;
                populate();
            });
        }, "app-loader").start();
    }


    private void populate() {
        allItems = buildItems();
        findViewById(R.id.settingsBtn).setVisibility(
                hiddenPkgs.contains(SETTINGS_PKG) ? View.GONE : View.VISIBLE);
        refreshPinnedApps();
        pinnedGrid.postDelayed(() -> {
            if (dialogs != null && dialogs.isOpen()) return;
            if (restoreFocusId != View.NO_ID) {
                focusHome();
                return;
            }
            View current = getCurrentFocus();
            if (!interactedSinceLoad && !barFocusRestored
                    && (current == null || current == brandBtn))
                focusHome();
        }, 60);
    }

    private List<HomeItem> buildItems() {
        List<HomeItem> items = new ArrayList<>();
        for (AppEntry a : orderedApps) {
            if (hiddenPkgs.contains(a.pkg) || a.pkg.equals(SETTINGS_PKG))
                continue;
            items.add(HomeItem.app(a));
        }
        return items;
    }
}
