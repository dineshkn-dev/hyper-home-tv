package dev.hyper.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import java.util.List;

final class AppAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<AppEntry> items;
    private final int tileW, tileH;

    AppAdapter(Context ctx, List<AppEntry> items, int tileW, int tileH) {
        this.ctx = ctx;
        this.items = items;
        this.tileW = tileW;
        this.tileH = tileH;
    }

    @Override public int getCount() { return items.size(); }
    @Override public AppEntry getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override public View getView(int i, View convert, ViewGroup parent) {
        AppTile tile = new AppTile(ctx, items.get(i), tileW, tileH);
        tile.setLayoutParams(new AbsListView.LayoutParams(tileW, tileH));
        return tile;
    }
}
