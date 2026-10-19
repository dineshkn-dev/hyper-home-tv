package dev.hyper.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AppRepository {

    private static final String PREFS = "hyper";
    private static final String KEY_ORDER = "order";
    private static final String KEY_PINS = "pins";

    private final Context ctx;
    private final PackageManager pm;

    AppRepository(Context ctx) {
        this.ctx = ctx;
        this.pm = ctx.getPackageManager();
    }

    List<AppEntry> load() {
        Map<String, AppEntry> found = new LinkedHashMap<>();
        addCategory(found, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addCategory(found, Intent.CATEGORY_LAUNCHER);

        List<AppEntry> list = new ArrayList<>(found.values());
        list.removeIf(a -> a.pkg.equals(ctx.getPackageName()));
        list.sort((a, b) -> a.userInstalled != b.userInstalled
                ? (a.userInstalled ? -1 : 1)
                : String.CASE_INSENSITIVE_ORDER.compare(a.label.toString(), b.label.toString()));
        return applySavedOrder(list);
    }

    private void addCategory(Map<String, AppEntry> found, String category) {
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
            found.put(pkg, new AppEntry(pkg, ri.loadLabel(pm), icon, banner, launch, user));
        }
    }

    void saveOrder(List<AppEntry> apps) {
        List<String> pkgs = new ArrayList<>();
        for (AppEntry a : apps) pkgs.add(a.pkg);
        write(KEY_ORDER, pkgs);
    }

    List<String> pins() {
        return read(KEY_PINS);
    }

    void togglePin(String pkg) {
        List<String> pins = pins();
        if (!pins.remove(pkg)) pins.add(pkg);
        write(KEY_PINS, pins);
    }

    private List<AppEntry> applySavedOrder(List<AppEntry> sorted) {
        List<String> saved = read(KEY_ORDER);
        if (saved.isEmpty()) return sorted;
        Map<String, AppEntry> byPkg = new LinkedHashMap<>();
        for (AppEntry a : sorted) byPkg.put(a.pkg, a);
        List<AppEntry> out = new ArrayList<>();
        for (String p : saved) {
            AppEntry e = byPkg.remove(p);
            if (e != null) out.add(e);
        }
        out.addAll(byPkg.values());
        return out;
    }

    private List<String> read(String key) {
        List<String> out = new ArrayList<>();
        String s = prefs().getString(key, "");
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split("\n")));
        return out;
    }

    private void write(String key, List<String> items) {
        prefs().edit().putString(key, String.join("\n", items)).apply();
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
