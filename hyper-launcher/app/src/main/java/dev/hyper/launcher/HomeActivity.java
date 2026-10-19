package dev.hyper.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HomeActivity extends Activity {

    private static final long IDLE_MS = 3 * 60 * 1000L;
    private static final long LONG_PRESS_MS = 600L;
    private static final String[] TAB_NAMES = {"Home", "Apps", "Settings"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable idleCheck = this::checkIdle;
    private final Runnable clockTick = this::tickSaverClock;
    private final Runnable menuLongPress = this::showQuickPanel;
    private boolean menuLongFired, menuPressed;

    private AppRepository repo;
    private Sfx sfx;

    private ImageView heroAImg, heroBImg;
    private boolean heroFrontIsA;
    private TextView heroName, heroHint, date;
    private LinearLayout shelves, tabs, overlayPanel;
    private FrameLayout overlay, screensaver;
    private ImageView saverArt;
    private TextView saverClock, saverDate;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppTile> tiles = new ArrayList<>();
    private final List<HorizontalScrollView> rowScrolls = new ArrayList<>();
    private final List<LinearLayout> rowViews = new ArrayList<>();
    private final List<Boolean> rowIsAllApps = new ArrayList<>();
    private int activeTab = 0;
    private int focusedRow = 0, focusedCol = 0;
    private boolean editing;
    private long lastInput = System.currentTimeMillis();
    private boolean saverOn;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_home);
        repo = new AppRepository(this);
        sfx = new Sfx(this);
        heroAImg = findViewById(R.id.heroA);
        heroBImg = findViewById(R.id.heroB);
        heroName = findViewById(R.id.heroName);
        heroHint = findViewById(R.id.heroHint);
        date = findViewById(R.id.date);
        shelves = findViewById(R.id.shelves);
        tabs = findViewById(R.id.tabs);
        overlay = findViewById(R.id.overlay);
        overlayPanel = findViewById(R.id.overlayPanel);
        screensaver = findViewById(R.id.screensaver);
        saverArt = findViewById(R.id.saverArt);
        saverClock = findViewById(R.id.saverClock);
        saverDate = findViewById(R.id.saverDate);
        buildTabs();
        updateDate();
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
        loadApps();
        scheduleIdle();
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(pkgReceiver); } catch (IllegalArgumentException ignored) {}
        main.removeCallbacks(idleCheck);
        main.removeCallbacks(clockTick);
        main.removeCallbacks(menuLongPress);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        sfx.release();
    }

    @Override public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) { hideOverlay(); return; }
        if (editing) exitEdit();
    }

    /* ---------------- input ---------------- */

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_UP && code == KeyEvent.KEYCODE_MENU) {
            return handleMenuUp();
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        lastInput = System.currentTimeMillis();

        if (saverOn) { hideScreensaver(); return true; }
        if (overlay.getVisibility() == View.VISIBLE) return super.dispatchKeyEvent(e);
        if (code == KeyEvent.KEYCODE_MENU) {
            if (e.getRepeatCount() == 0) {
                menuPressed = true;
                menuLongFired = false;
                main.postDelayed(menuLongPress, LONG_PRESS_MS);
            }
            return true;
        }
        if (editing) return handleEditKey(code);
        return super.dispatchKeyEvent(e);
    }

    private boolean handleMenuUp() {
        main.removeCallbacks(menuLongPress);
        boolean wasPressed = menuPressed;
        menuPressed = false;
        if (wasPressed && !menuLongFired && !saverOn && overlay.getVisibility() != View.VISIBLE) {
            if (editing) exitEdit(); else enterEdit();
        }
        return true;
    }

    private boolean handleEditKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveTile(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveTile(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                AppEntry cur = currentApp();
                if (cur != null) showActionMenu(cur);
                return true;
            case KeyEvent.KEYCODE_BACK:
                exitEdit();
                return true;
            default:
                return true;
        }
    }

    /* ---------------- tabs ---------------- */

    private void buildTabs() {
        tabs.removeAllViews();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            TextView t = new TextView(this);
            t.setText(TAB_NAMES[i]);
            t.setTextSize(17f);
            t.setPadding(dp(18), dp(8), dp(18), dp(8));
            t.setFocusable(true);
            final int idx = i;
            t.setOnClickListener(v -> selectTab(idx));
            t.setOnFocusChangeListener((v, has) -> {
                if (has) selectTab(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            tabs.addView(t, lp);
        }
        styleTabs();
    }

    private void selectTab(int idx) {
        if (idx == activeTab && !rowViews.isEmpty()) return;
        activeTab = idx;
        styleTabs();
        if (editing) exitEdit();
        if (idx == 0) buildHome();
        else if (idx == 1) buildAppsGrid();
        else buildSettings();
    }

    private void styleTabs() {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean on = i == activeTab;
            t.setTextColor(on ? 0xFFFFFFFF : 0xFF69748F);
            t.setBackgroundColor(on ? 0x33FFFFFF : 0x00000000);
        }
    }

    /* ---------------- screens ---------------- */

    private void clearShelves() {
        shelves.removeAllViews();
        tiles.clear();
        rowScrolls.clear();
        rowViews.clear();
        rowIsAllApps.clear();
    }

    private void buildHome() {
        clearShelves();
        List<AppEntry> pinned = pinnedApps();
        if (!pinned.isEmpty()) addShelf("Top Shelf", pinned, true, false);
        addShelf("All Apps", allApps, false, true);
        focusFirst();
    }

    private void buildAppsGrid() {
        clearShelves();
        addShelf("Apps", allApps, false, true);
        focusFirst();
    }

    private void buildSettings() {
        clearShelves();
        addSettingsRow("Settings", Settings.ACTION_SETTINGS);
        addSettingsRow("Display", Settings.ACTION_DISPLAY_SETTINGS);
        addSettingsRow("Sound", Settings.ACTION_SOUND_SETTINGS);
        addSettingsRow("Network", Settings.ACTION_WIFI_SETTINGS);
        addSettingsRow("Apps", Settings.ACTION_APPLICATION_SETTINGS);
        addSettingsRow(sfx.isEnabled() ? "Turn off focus sounds" : "Turn on focus sounds", null);
        heroName.setText("Settings");
        heroHint.setText("");
    }

    private void addSettingsRow(String label, String action) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(20f);
        t.setTextColor(0xFFE8EDF8);
        t.setPadding(dp(52), dp(14), dp(52), dp(14));
        t.setFocusable(true);
        t.setOnClickListener(v -> {
            if (action != null) openSettings(action);
            else {
                sfx.setEnabled(!sfx.isEnabled());
                buildSettings();
            }
        });
        t.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? 0x33FFFFFF : 0x00000000));
        shelves.addView(t);
    }

    private void focusFirst() {
        shelves.post(() -> {
            List<AppTile> first = rowTiles(0);
            if (!first.isEmpty()) first.get(0).requestFocus();
        });
    }

    private List<AppEntry> pinnedApps() {
        List<AppEntry> out = new ArrayList<>();
        for (String p : repo.pins())
            for (AppEntry a : allApps) if (a.pkg.equals(p)) out.add(a);
        return out;
    }

    private void addShelf(String title, List<AppEntry> apps, boolean large, boolean reorderable) {
        TextView head = new TextView(this);
        head.setText(title);
        head.setTextSize(20f);
        head.setTextColor(0xFFE8EDF8);
        head.setPadding(dp(52), dp(18), dp(52), dp(16));
        shelves.addView(head);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(52), dp(14), dp(52), dp(16));
        scroll.addView(row);
        shelves.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        rowScrolls.add(scroll);
        rowViews.add(row);
        rowIsAllApps.add(reorderable);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int tileW = (int) (screenW * (large ? 0.30f : 0.24f));
        int tileH = (int) (tileW * 9f / 16f);

        for (AppEntry a : apps) {
            AppTile t = new AppTile(this, a, tileW, tileH);
            t.setTag(a.pkg);
            t.setFocusable(true);
            t.setOnClickListener(v -> launch(a));
            t.setOnLongClickListener(v -> { showActionMenu(a); return true; });
            t.setOnFocusChangeListener((v, has) -> {
                if (has) onTileFocus(t);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(20));
            row.addView(t, lp);
            tiles.add(t);
            if (editing && reorderable) t.startWiggle();
        }
    }

    private void populate(List<AppEntry> apps) {
        allApps.clear();
        allApps.addAll(apps);
        if (activeTab == 0) buildHome();
        else if (activeTab == 1) buildAppsGrid();
    }

    /* ---------------- focus and motion ---------------- */

    private List<AppTile> rowTiles(int r) {
        List<AppTile> out = new ArrayList<>();
        if (r < 0 || r >= rowViews.size()) return out;
        LinearLayout row = rowViews.get(r);
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i) instanceof AppTile) out.add((AppTile) row.getChildAt(i));
        }
        return out;
    }

    private void onTileFocus(AppTile t) {
        for (int r = 0; r < rowViews.size(); r++) {
            int c = rowTiles(r).indexOf(t);
            if (c < 0) continue;
            float dx = (r == focusedRow) ? Math.signum(c - focusedCol) : 0f;
            boolean moved = r != focusedRow || c != focusedCol;
            focusedRow = r;
            focusedCol = c;
            for (AppTile x : tiles) x.setFocusedState(x == t, x == t ? dx : 0f);
            if (moved) sfx.tick();
            glideRow(r, t);
            updateHero(currentApp());
            updateHint();
            return;
        }
    }

    private void glideRow(int r, AppTile t) {
        HorizontalScrollView sv = rowScrolls.get(r);
        int target = t.getLeft() + t.getWidth() / 2 - sv.getWidth() / 2;
        sv.smoothScrollTo(Math.max(0, target), 0);
    }

    private AppEntry currentApp() {
        List<AppTile> row = rowTiles(focusedRow);
        if (focusedCol < 0 || focusedCol >= row.size()) return null;
        Object pkg = row.get(focusedCol).getTag();
        for (AppEntry a : allApps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    /* ---------------- edit mode ---------------- */

    private void enterEdit() {
        if (!rowIsAllApps.get(focusedRow)) return;
        editing = true;
        for (AppTile t : tiles) t.startWiggle();
        shelves.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        updateHint();
    }

    private void exitEdit() {
        editing = false;
        for (AppTile t : tiles) t.stopWiggle();
        repo.saveOrder(allApps);
        updateHint();
    }

    /** Swaps the focused app with its neighbour in the reorderable row and keeps focus on it. */
    private void moveTile(int delta) {
        if (!rowIsAllApps.get(focusedRow)) return;
        List<AppTile> row = rowTiles(focusedRow);
        int target = focusedCol + delta;
        if (target < 0 || target >= row.size()) return;

        LinearLayout rowView = rowViews.get(focusedRow);
        AppTile moved = row.get(focusedCol);
        int destIndex = rowView.indexOfChild(row.get(target));
        rowView.removeView(moved);
        rowView.addView(moved, destIndex, moved.getLayoutParams());

        int ia = indexOfPkg(String.valueOf(moved.getTag()));
        int ib = indexOfPkg(String.valueOf(row.get(target).getTag()));
        if (ia >= 0 && ib >= 0) Collections.swap(allApps, ia, ib);

        focusedCol = target;
        moved.requestFocus();
        updateHint();
        glideRow(focusedRow, moved);
    }

    private int indexOfPkg(String pkg) {
        for (int i = 0; i < allApps.size(); i++) if (allApps.get(i).pkg.equals(pkg)) return i;
        return -1;
    }

    /* ---------------- hero and hint ---------------- */

    private void updateHint() {
        if (editing) {
            heroHint.setText("editing: ◄ ► to move · OK for options · back to save");
        } else {
            List<AppTile> row = rowTiles(focusedRow);
            heroHint.setText(row.isEmpty() ? "" : (focusedCol + 1) + " / " + row.size());
        }
    }

    private void updateHero(AppEntry app) {
        if (app == null) return;
        ImageView back = heroFrontIsA ? heroBImg : heroAImg;
        ImageView front = heroFrontIsA ? heroAImg : heroBImg;
        heroFrontIsA = !heroFrontIsA;
        Drawable art = app.banner != null ? app.banner : app.icon;
        back.setImageDrawable(art);
        back.animate().alpha(app.banner != null ? 0.30f : 0.12f)
                .setDuration(Motion.HERO_MS).setInterpolator(Motion.EASE_OUT_EXPO).start();
        front.animate().alpha(0f).setDuration(Motion.HERO_MS).start();
        heroName.setText(app.label);
    }

    /* ---------------- overlays and actions ---------------- */

    private void showActionMenu(AppEntry app) {
        sfx.click();
        overlayPanel.removeAllViews();
        addOverlayTitle(app.label.toString());

        boolean pinned = repo.pins().contains(app.pkg);
        addOverlayItem(pinned ? "Unpin from Top Shelf" : "Pin to Top Shelf", () -> {
            repo.togglePin(app.pkg);
            hideOverlay();
            loadApps();
        });
        addOverlayItem("Move", () -> { hideOverlay(); if (rowIsAllApps.get(focusedRow)) enterEdit(); });
        addOverlayItem("App info", () -> { hideOverlay(); openAppInfo(app.pkg); });
        if (app.userInstalled) {
            addOverlayItem("Uninstall", () -> { hideOverlay(); uninstall(app); });
        }
        addOverlayItem("Cancel", this::hideOverlay);
        showOverlay();
    }

    private void showQuickPanel() {
        if (saverOn || overlay.getVisibility() == View.VISIBLE) return;
        menuLongFired = true;
        if (editing) exitEdit();
        overlayPanel.removeAllViews();
        addOverlayTitle("Quick settings");
        addOverlayItem("Settings", () -> { hideOverlay(); openSettings(Settings.ACTION_SETTINGS); });
        addOverlayItem("Display", () -> { hideOverlay(); openSettings(Settings.ACTION_DISPLAY_SETTINGS); });
        addOverlayItem("Sound", () -> { hideOverlay(); openSettings(Settings.ACTION_SOUND_SETTINGS); });
        addOverlayItem("Network", () -> { hideOverlay(); openSettings(Settings.ACTION_WIFI_SETTINGS); });
        addOverlayItem("Cancel", this::hideOverlay);
        showOverlay();
    }

    private void addOverlayTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(22f);
        title.setTextColor(0xFFE8EDF8);
        title.setPadding(0, 0, 0, dp(12));
        overlayPanel.addView(title);
    }

    private void addOverlayItem(String text, Runnable action) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18f);
        t.setTextColor(0xFFE8EDF8);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        t.setFocusable(true);
        t.setBackgroundColor(0x22FFFFFF);
        t.setOnClickListener(v -> action.run());
        t.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? 0x55FFFFFF : 0x22FFFFFF));
        overlayPanel.addView(t, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (overlayPanel.getChildCount() == 2) t.requestFocus();
    }

    private void showOverlay() {
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(Motion.UNFOCUS_MS).start();
    }

    private void hideOverlay() {
        overlay.animate().alpha(0f).setDuration(Motion.UNFOCUS_MS).withEndAction(() -> {
            overlay.setVisibility(View.GONE);
            List<AppTile> row = rowTiles(focusedRow);
            if (!row.isEmpty()) row.get(Math.min(focusedCol, row.size() - 1)).requestFocus();
        }).start();
    }

    private void uninstall(AppEntry a) {
        try {
            startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                    Uri.fromParts("package", a.pkg, null)));
        } catch (Exception ignored) {}
    }

    private void openSettings(String action) {
        try { startActivity(new Intent(action)); } catch (Exception ignored) {}
    }

    private void launch(AppEntry app) {
        sfx.click();
        try { startActivity(app.intent); } catch (Exception ignored) {}
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", pkg, null)));
        } catch (Exception ignored) {}
    }

    /* ---------------- loading ---------------- */

    private void loadApps() {
        new Thread(() -> {
            List<AppEntry> apps = repo.load();
            runOnUiThread(() -> populate(apps));
        }, "app-loader").start();
    }

    private void updateDate() {
        date.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(new Date()));
    }

    /* ---------------- idle and screensaver ---------------- */

    private void scheduleIdle() {
        main.removeCallbacks(idleCheck);
        main.postDelayed(idleCheck, IDLE_MS);
    }

    private void checkIdle() {
        if (!saverOn && System.currentTimeMillis() - lastInput >= IDLE_MS) {
            showScreensaver();
        } else {
            scheduleIdle();
        }
    }

    private void showScreensaver() {
        saverOn = true;
        if (!allApps.isEmpty()) {
            AppEntry pick = allApps.get((int) (Math.random() * allApps.size()));
            if (pick.banner != null) saverArt.setImageDrawable(pick.banner);
        }
        screensaver.setVisibility(View.VISIBLE);
        screensaver.setAlpha(0f);
        screensaver.animate().alpha(1f).setDuration(Motion.HERO_MS).start();
        tickSaverClock();
    }

    private void hideScreensaver() {
        saverOn = false;
        main.removeCallbacks(clockTick);
        screensaver.animate().alpha(0f).setDuration(Motion.HERO_MS).withEndAction(
                () -> screensaver.setVisibility(View.GONE)).start();
        scheduleIdle();
    }

    private void tickSaverClock() {
        main.removeCallbacks(clockTick);
        saverClock.setText(new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()));
        saverDate.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(new Date()));
        if (saverOn) main.postDelayed(clockTick, 1000);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
