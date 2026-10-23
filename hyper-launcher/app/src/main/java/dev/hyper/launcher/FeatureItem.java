package dev.hyper.launcher;

import android.graphics.drawable.Drawable;

/** One card in the Featured Tonight deck. */
final class FeatureItem {

    final String eyebrow;
    final String title;
    final String description;
    final String meta;
    final float progress;
    final String imageUri;
    final Drawable appIcon;
    final Drawable appBanner;
    final WatchNextItem watchNext;
    final AppEntry app;

    FeatureItem(String eyebrow, String title, String description, String meta,
                float progress, String imageUri, Drawable appBanner,
                Drawable appIcon, WatchNextItem watchNext, AppEntry app) {
        this.eyebrow = eyebrow;
        this.title = title;
        this.description = description;
        this.meta = meta;
        this.progress = progress;
        this.imageUri = imageUri;
        this.appBanner = appBanner;
        this.appIcon = appIcon;
        this.watchNext = watchNext;
        this.app = app;
    }
}
