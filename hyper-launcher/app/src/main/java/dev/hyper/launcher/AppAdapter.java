package dev.hyper.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import java.util.List;

final class AppAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<AppEntry> apps;
    private final int tileW, tileH;

    AppAdapter(Context ctx, List<AppEntry> apps, int tileW, int tileH) {
        this.ctx = ctx;
        this.apps = apps;
        this.tileW = tileW;
        this.tileH = tileH;
    }

    @Override public int getCount() { return apps.size(); }
    @Override public AppEntry getItem(int i) { return apps.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override public View getView(int i, View convert, ViewGroup parent) {
        AppTile t = new AppTile(ctx, apps.get(i), tileW, tileH);
        t.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }
}
