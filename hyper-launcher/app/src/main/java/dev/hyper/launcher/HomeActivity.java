package dev.hyper.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HomeActivity extends Activity {

    private GridView grid;
    private List<AppEntry> allApps = new ArrayList<>();
    private View lastSelectedTile;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_home);
        grid = findViewById(R.id.grid);
        updateDate();

        grid.setOnItemClickListener((p, v, pos, id) -> launch(allApps.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            openAppInfo(allApps.get(pos).pkg);
            return true;
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (lastSelectedTile instanceof AppTile)
                    ((AppTile) lastSelectedTile).setFocusedState(false);
                if (v instanceof AppTile)
                    ((AppTile) v).setFocusedState(true);
                lastSelectedTile = v;
            }
            @Override public void onNothingSelected(AdapterView<?> p) {
                if (lastSelectedTile instanceof AppTile)
                    ((AppTile) lastSelectedTile).setFocusedState(false);
                lastSelectedTile = null;
            }
        });
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
        try { unregisterReceiver(pkgReceiver); } catch (IllegalArgumentException ignored) {}
    }

    @Override public void onBackPressed() {
        // home shouldn't go back
    }

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
            runOnUiThread(() -> populate(apps));
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
        return list;
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

    private void populate(List<AppEntry> apps) {
        allApps = apps;

        float d = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int sidePad = (int) (64 * d) * 2;
        int gap = (int) (18 * d);
        int cols = 6;
        int tileW = (screenW - sidePad - gap * (cols - 1)) / cols;
        int tileH = (int) (tileW * 9f / 16f);

        grid.setAdapter(new AppAdapter(this, apps, tileW, tileH));
        grid.postDelayed(() -> {
            grid.setSelection(0);
            grid.requestFocus();
            View v = grid.getSelectedView();
            if (v instanceof AppTile) {
                ((AppTile) v).setFocusedState(true);
                lastSelectedTile = v;
            }
        }, 80);
    }
}
