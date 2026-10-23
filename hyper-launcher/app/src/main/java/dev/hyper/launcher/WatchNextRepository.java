package dev.hyper.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads and normalizes the system Watch Next / Play Next queue. */
final class WatchNextRepository {

    private WatchNextRepository() {}

    static List<WatchNextItem> load(Context ctx) {
        List<WatchNextItem> items = new ArrayList<>();
        String[] projection = {
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
                TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
                TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_TYPE,
                TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION,
                TvContract.WatchNextPrograms.COLUMN_LONG_DESCRIPTION,
                TvContract.WatchNextPrograms.COLUMN_CANONICAL_GENRE,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
                TvContract.WatchNextPrograms.COLUMN_SEARCHABLE
        };
        try (Cursor c = ctx.getContentResolver().query(
                TvContract.WatchNextPrograms.CONTENT_URI, projection,
                null, null, null)) {
            if (c == null) return items;
            int titleIdx = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_TITLE);
            int episodeIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE);
            int seasonIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
            int epNumIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
            int pkgIdx = c.getColumnIndex(TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME);
            int thumbIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI);
            int posterIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
            int intentIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
            int typeIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_TYPE);
            int shortDescIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION);
            int longDescIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_LONG_DESCRIPTION);
            int genreIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_CANONICAL_GENRE);
            int durationIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS);
            int positionIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
            int lastIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS);
            int browsableIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_BROWSABLE);
            int searchableIdx = c.getColumnIndex(
                    TvContract.WatchNextPrograms.COLUMN_SEARCHABLE);

            Set<String> seen = new HashSet<>();
            while (c.moveToNext()) {
                int browsable = browsableIdx < 0 || c.isNull(browsableIdx)
                        ? 1 : c.getInt(browsableIdx);
                if (browsable == 0) continue;
                int searchable = searchableIdx < 0 || c.isNull(searchableIdx)
                        ? 1 : c.getInt(searchableIdx);
                if (searchable == 0) continue;

                String intentUri = col(c, intentIdx);
                String pkg = col(c, pkgIdx);
                if (intentUri.isEmpty() && pkg.isEmpty()) continue;

                String title = col(c, titleIdx);
                String episode = col(c, episodeIdx);
                String season = col(c, seasonIdx);
                String epNum = col(c, epNumIdx);
                String key = pkg + "|" + (title.isEmpty() ? episode : title);
                if (!seen.add(key)) continue;

                long duration = durationIdx < 0 ? 0 : c.getLong(durationIdx);
                long position = positionIdx < 0 ? 0 : c.getLong(positionIdx);
                float progress = duration > 0 && position > 0
                        ? Math.min(1f, position / (float) duration) : 0f;
                long last = lastIdx < 0 ? 0 : c.getLong(lastIdx);

                String image = col(c, thumbIdx);
                if (image.isEmpty()) image = col(c, posterIdx);

                CharSequence appLabel = pkg;
                Drawable appIcon = null, appBanner = null;
                if (!pkg.isEmpty()) {
                    try {
                        ApplicationInfo ai = ctx.getPackageManager()
                                .getApplicationInfo(pkg, 0);
                        appLabel = ctx.getPackageManager().getApplicationLabel(ai);
                        appIcon = ctx.getPackageManager().getApplicationIcon(ai);
                        appBanner = ctx.getPackageManager().getApplicationBanner(ai);
                    } catch (Exception ignored) {}
                }

                String label = appLabel == null ? pkg : appLabel.toString();
                if (label.isEmpty()) label = "Continue watching";
                String ep = "";
                if (!season.isEmpty() && !epNum.isEmpty())
                    ep = "S" + season + " E" + epNum;
                else if (!episode.isEmpty())
                    ep = episode;
                String meta = ep.isEmpty() ? label : ep + " · " + label;
                String mainTitle = !title.isEmpty() ? title
                        : !episode.isEmpty() ? episode : "Continue watching";

                int type = typeIdx < 0 || c.isNull(typeIdx)
                        ? -1 : c.getInt(typeIdx);
                String episodeLabel;
                if (!season.isEmpty() && !epNum.isEmpty())
                    episodeLabel = "S" + season + " · E" + epNum;
                else if (type == TvContract.WatchNextPrograms.TYPE_MOVIE)
                    episodeLabel = "MOVIE";
                else if (!episode.isEmpty())
                    episodeLabel = "EPISODE";
                else
                    episodeLabel = "WATCHING";

                String description = col(c, shortDescIdx);
                if (description.isEmpty()) description = col(c, longDescIdx);
                String genres = col(c, genreIdx)
                        .replace("_", " ")
                        .replace(",", " · ")
                        .toUpperCase(Locale.US);

                items.add(new WatchNextItem(mainTitle, meta, episodeLabel,
                        description, genres, pkg, image, intentUri, duration,
                        last, progress, appIcon, appBanner));
            }
        } catch (Exception e) {
            Log.w("Hyper", "Watch Next query failed", e);
        }
        items.sort((a, b) -> Long.compare(b.lastEngagement, a.lastEngagement));
        return items;
    }

    private static String col(Cursor c, int idx) {
        if (idx < 0 || c.isNull(idx)) return "";
        String s = c.getString(idx);
        return s == null ? "" : s.trim();
    }
}
