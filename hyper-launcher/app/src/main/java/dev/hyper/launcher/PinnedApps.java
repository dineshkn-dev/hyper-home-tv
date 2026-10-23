package dev.hyper.launcher;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves and persists the six apps pinned to the Hyper OS home. */
final class PinnedApps {

    static final int MAX = 6;

    private PinnedApps() {}

    static List<String> load(SharedPreferences prefs) {
        List<String> out = new ArrayList<>();
        for (String raw : prefs.getString("pinned", "").split("\n")) {
            String pkg = raw.trim();
            if (!pkg.isEmpty() && !out.contains(pkg)) out.add(pkg);
        }
        return out;
    }

    static void save(SharedPreferences prefs, List<String> pinned) {
        StringBuilder sb = new StringBuilder();
        for (String pkg : pinned) sb.append(pkg).append('\n');
        prefs.edit().putString("pinned", sb.toString()).apply();
    }

    /**
     * Only the apps explicitly pinned by the user, in pin order.
     * Used for the home dock once pins are seeded.
     */
    static List<AppEntry> resolvePinned(List<HomeItem> items, List<String> pinned) {
        Map<String, AppEntry> visible = new LinkedHashMap<>();
        for (HomeItem item : items)
            if (!item.isFolder() && !item.app.pkg.equals("com.android.tv.settings"))
                visible.put(item.app.pkg, item.app);

        List<AppEntry> out = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (String pkg : pinned) {
            AppEntry app = visible.get(pkg);
            if (app != null && used.add(pkg)) out.add(app);
            if (out.size() >= MAX) break;
        }
        return out;
    }

    /**
     * Pinned apps first, then the most-used visible apps, up to six.
     * Used once to seed defaults for installations that predate explicit pins.
     */
    static List<AppEntry> resolve(List<HomeItem> items, List<String> pinned,
                                  Map<String, Integer> uses) {
        Map<String, AppEntry> visible = new LinkedHashMap<>();
        for (HomeItem item : items)
            if (!item.isFolder() && !item.app.pkg.equals("com.android.tv.settings"))
                visible.put(item.app.pkg, item.app);

        List<AppEntry> out = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (String pkg : pinned) {
            AppEntry app = visible.get(pkg);
            if (app != null && used.add(pkg)) out.add(app);
            if (out.size() >= MAX) return out;
        }

        List<AppEntry> candidates = new ArrayList<>();
        for (AppEntry app : visible.values())
            if (!used.contains(app.pkg)) candidates.add(app);
        candidates.sort((a, b) -> Integer.compare(
                uses.getOrDefault(b.pkg, 0),
                uses.getOrDefault(a.pkg, 0)));

        for (AppEntry app : candidates) {
            if (out.size() >= MAX) break;
            if (used.add(app.pkg)) out.add(app);
        }
        return out;
    }
}
