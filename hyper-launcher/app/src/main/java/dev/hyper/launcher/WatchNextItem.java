package dev.hyper.launcher;

import android.graphics.drawable.Drawable;

/** One row from Android's Watch Next / Play Next queue. */
final class WatchNextItem {

    final String title;
    final String meta;
    final String episodeLabel;
    final String description;
    final String genres;
    final String packageName;
    final String imageUri;
    final String intentUri;
    final long durationMs;
    final long lastEngagement;
    final float progress;
    final Drawable appIcon;
    final Drawable appBanner;

    WatchNextItem(String title, String meta, String episodeLabel,
                  String description, String genres, String packageName,
                  String imageUri, String intentUri, long durationMs,
                  long lastEngagement, float progress,
                  Drawable appIcon, Drawable appBanner) {
        this.title = title;
        this.meta = meta;
        this.episodeLabel = episodeLabel;
        this.description = description;
        this.genres = genres;
        this.packageName = packageName;
        this.imageUri = imageUri;
        this.intentUri = intentUri;
        this.durationMs = durationMs;
        this.lastEngagement = lastEngagement;
        this.progress = progress;
        this.appIcon = appIcon;
        this.appBanner = appBanner;
    }
}
