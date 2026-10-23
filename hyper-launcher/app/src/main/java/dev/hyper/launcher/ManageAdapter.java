package dev.hyper.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import java.util.List;
import java.util.Map;

final class ManageAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<HomeItem> items;
    private final Map<String, ManagedApp> apps;
    private final int tileW, tileH;

    ManageAdapter(Context ctx, List<HomeItem> items,
                  Map<String, ManagedApp> apps, int tileW, int tileH) {
        this.ctx = ctx;
        this.items = items;
        this.apps = apps;
        this.tileW = tileW;
        this.tileH = tileH;
    }

    @Override public int getCount() { return items.size(); }
    @Override public HomeItem getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override public View getView(int i, View convert, ViewGroup parent) {
        HomeItem item = items.get(i);
        View tile;
        if (item.isFolder()) {
            tile = new ManagedFolderTile(ctx, item.folder, item.folderApps,
                    tileW, tileH);
        } else {
            ManagedApp app = apps.get(item.app.pkg);
            if (app == null) {
                tile = new View(ctx);
            } else {
                tile = new ManagedAppTile(ctx, app, tileW, tileH);
            }
        }
        tile.setLayoutParams(new AbsListView.LayoutParams(tileW, tileH));
        return tile;
    }

    int tileWidth() { return tileW; }
    int tileHeight() { return tileH; }
}
