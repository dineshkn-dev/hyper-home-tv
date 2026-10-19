package dev.hyper.launcher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

final class AppEntry {
    final String pkg;
    final CharSequence label;
    final Drawable icon;
    final Drawable banner;
    final Intent intent;
    final boolean userInstalled;

    AppEntry(String pkg, CharSequence label, Drawable icon, Drawable banner,
             Intent intent, boolean userInstalled) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
        this.banner = banner;
        this.intent = intent;
        this.userInstalled = userInstalled;
    }
}
