package dev.hyper.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared loader for launchable TV apps. */
final class AppCatalog {

    private AppCatalog() {}

    static List<AppEntry> launchable(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, AppEntry> found = new LinkedHashMap<>();
        addCategory(pm, found, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addCategory(pm, found, Intent.CATEGORY_LAUNCHER);

        List<AppEntry> list = new ArrayList<>(found.values());
        list.removeIf(a -> a.pkg.equals(ctx.getPackageName()));
        list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.label.toString(), b.label.toString()));
        return list;
    }

    private static void addCategory(PackageManager pm, Map<String, AppEntry> found,
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
}
