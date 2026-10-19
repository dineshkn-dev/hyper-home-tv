package dev.hyper.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
        loadApps();
    }

    @Override protected void onPause() {
        super.onPause();
        closeMenu();
        try { unregisterReceiver(pkgReceiver); } catch (IllegalArgumentException ignored) {}
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
                    startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                            Uri.fromParts("package", app.pkg, null)));
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
            if (!hiddenPkgs.contains(a.pkg)) display.add(a);
        allApps = display;

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
