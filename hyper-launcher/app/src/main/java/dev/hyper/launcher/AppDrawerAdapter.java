package dev.hyper.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import java.util.List;
import java.util.Set;

final class AppDrawerAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<AppEntry> apps;
    private final Set<String> pinned;
    private final int tileSize;

    AppDrawerAdapter(Context ctx, List<AppEntry> apps, Set<String> pinned,
                     int tileSize) {
        this.ctx = ctx;
        this.apps = apps;
        this.pinned = pinned;
        this.tileSize = tileSize;
    }

    @Override public int getCount() { return apps.size(); }
    @Override public AppEntry getItem(int i) { return apps.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override public View getView(int i, View convert, ViewGroup parent) {
        AppEntry app = apps.get(i);
        DrawerAppTile tile = new DrawerAppTile(ctx, app, tileSize);
        tile.setPinned(pinned.contains(app.pkg));
        tile.setLayoutParams(new AbsListView.LayoutParams(
                tileSize, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tile;
    }
}
