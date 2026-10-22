package dev.hyper.launcher;

import java.util.List;
import java.util.Map;

/** One cell in the home grid — either an app or a folder. */
final class HomeItem {

    final AppEntry app;
    final Folder folder;
    final List<AppEntry> folderApps;

    private HomeItem(AppEntry app, Folder folder, List<AppEntry> folderApps) {
        this.app = app;
        this.folder = folder;
        this.folderApps = folderApps;
    }

    static HomeItem app(AppEntry app) {
        return new HomeItem(app, null, null);
    }

    static HomeItem folder(Folder folder, List<AppEntry> apps) {
        return new HomeItem(null, folder, apps);
    }

    boolean isFolder() {
        return folder != null;
    }

    String key() {
        return isFolder() ? folder.key() : app.pkg;
    }

    CharSequence label() {
        return isFolder() ? folder.name : app.label;
    }

    int usage(Map<String, Integer> uses) {
        if (!isFolder()) return uses.getOrDefault(app.pkg, 0);
        int total = 0;
        for (AppEntry a : folderApps) total += uses.getOrDefault(a.pkg, 0);
        return total;
    }
}
