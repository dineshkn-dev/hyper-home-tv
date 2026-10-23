package dev.hyper.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Builds the cards shown in the featured deck. */
final class FeatureItems {

    private FeatureItems() {}

    static List<FeatureItem> deck(Context ctx, List<WatchNextItem> watchNext,
                                  List<AppEntry> pinnedApps) {
        List<FeatureItem> deck = new ArrayList<>();
        if (!watchNext.isEmpty()) {
            for (WatchNextItem item : watchNext) deck.add(fromWatchNext(item));
        } else {
            for (AppEntry app : pinnedApps) deck.add(fromApp(ctx, app));
        }
        return deck;
    }

    static FeatureItem fromWatchNext(WatchNextItem item) {
        String description = item.description.isEmpty()
                ? "Continue where you left off." : item.description;
        String duration = fmtDuration(item.durationMs);
        String meta = item.genres;
        if (meta.isEmpty()) meta = item.episodeLabel;
        if (!duration.isEmpty())
            meta = meta.isEmpty() ? duration : meta + "  ·  " + duration;
        if (meta.isEmpty()) meta = "CONTINUE WATCHING";
        return new FeatureItem("FEATURED TONIGHT · " + currentYear(),
                item.title, description, meta.toUpperCase(Locale.US),
                item.progress, item.imageUri, item.appBanner, item.appIcon,
                item, null);
    }

    static FeatureItem fromApp(Context ctx, AppEntry app) {
        String version = "";
        if (ctx != null) {
            try {
                version = ctx.getPackageManager()
                        .getPackageInfo(app.pkg, 0).versionName;
            } catch (Exception ignored) {}
        }
        String meta = version == null || version.isEmpty()
                ? "APP" : "APP  ·  VERSION " + version.toUpperCase(Locale.US);
        return new FeatureItem("YOUR APPS · " + currentYear(),
                app.label.toString(), appDescription(ctx, app), meta, 0f,
                "", app.banner, app.icon, null, app);
    }

    private static String appDescription(Context ctx, AppEntry app) {
        if (ctx != null) {
            try {
                ApplicationInfo ai = ctx.getPackageManager()
                        .getApplicationInfo(app.pkg, 0);
                if (ai.descriptionRes != 0)
                    return ctx.getPackageManager()
                            .getText(app.pkg, ai.descriptionRes, ai).toString();
            } catch (Exception ignored) {}
        }
        return "Open " + app.label + ".";
    }

    private static String currentYear() {
        return new SimpleDateFormat("yyyy", Locale.US).format(new Date());
    }

    private static String fmtDuration(long ms) {
        if (ms <= 0) return "";
        long minutes = Math.max(1, Math.round(ms / 60000.0));
        long h = minutes / 60, m = minutes % 60;
        return h > 0
                ? h + "H " + String.format(Locale.US, "%02d", m) + "M"
                : m + "M";
    }
}
