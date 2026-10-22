package dev.hyper.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import java.util.List;

final class AppAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<HomeItem> items;
    private final int tileW, tileH;

    AppAdapter(Context ctx, List<HomeItem> items, int tileW, int tileH) {
        this.ctx = ctx;
        this.items = items;
        this.tileW = tileW;
        this.tileH = tileH;
    }

    @Override public int getCount() { return items.size(); }
    @Override public HomeItem getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override public View getView(int i, View convert, ViewGroup parent) {
        HomeItem item = items.get(i);
        View tile = item.isFolder()
                ? new FolderTile(ctx, item.folder, item.folderApps, tileW, tileH)
                : new AppTile(ctx, item.app, tileW, tileH);
        tile.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return tile;
    }
}
