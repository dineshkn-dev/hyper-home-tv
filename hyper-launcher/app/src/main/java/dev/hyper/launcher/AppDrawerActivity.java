package dev.hyper.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** TV app drawer: rail filters, icon grid, hold-OK dropdown actions. */
public final class AppDrawerActivity extends Activity {

    private static final String PREF = "hyper";
    private static final String SETTINGS_PKG = "com.android.tv.settings";
    private static final int ACCENT = 0xFFC8FF3D;
    private static final int TXT = 0xFFEDEDED;
    private static final int DIM = 0xFF9A9A9A;
    private static final int LINE = 0xFF242424;
    private static final long HOLD_MS = 350;

    private TvGridView grid;
    private LinearLayout railItems;
    private TextView title, subtitle, installed;
    private AppDrawerAdapter adapter;
    private SharedPreferences prefs;
    private float d;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> apps = new ArrayList<>();
    private final Set<String> pinned = new HashSet<>();
    private final List<Rail> rails = new ArrayList<>();

    private int heldPos = -1;
    private long centerDownAt;
    private boolean longPressSeen;

    private PopupWindow popup;
    private int tileSize;
    private boolean loading;
    private boolean pinnedOnly;
    private String selectedPkg;
    private int selected = -1;

    private static final class Rail {
        final LinearLayout row;
        final ImageView icon;
        final TextView label;
        final boolean pinnedFilter;
        Rail(LinearLayout row, ImageView icon, TextView label, boolean pinnedFilter) {
            this.row = row;
            this.icon = icon;
            this.label = label;
            this.pinnedFilter = pinnedFilter;
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;

        setContentView(R.layout.activity_app_drawer);
        grid = findViewById(R.id.drawerGrid);
        railItems = findViewById(R.id.drawerRailItems);
        title = findViewById(R.id.drawerTitle);
        subtitle = findViewById(R.id.drawerSubtitle);
        installed = findViewById(R.id.drawerInstalled);

        int rootW = getResources().getDisplayMetrics().widthPixels
                - (int) (128 * d);
        int gridW = rootW - (int) (180 * d) - (int) (24 * d);
        int gap = (int) (16 * d);
        tileSize = Math.max((int) (96 * d), (gridW - gap * 5) / 6);

        buildRails();
        setupGrid();

        ImageView brand = findViewById(R.id.drawerBrand);
        brand.setOnClickListener(v ->
                startActivity(new Intent(this, LauncherSettingsActivity.class)));
        brand.setOnFocusChangeListener((v, has) ->
                FocusGlow.apply(v, has, 1.12f));
    }

    @Override protected void onResume() {
        super.onResume();
        reloadPins();
        loadApps();
    }

    @Override protected void onPause() {
        super.onPause();
        heldPos = -1;
        centerDownAt = 0;
        longPressSeen = false;
        if (popup != null) popup.dismiss();
    }

    @Override public void onBackPressed() {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
            return;
        }
        super.onBackPressed();
    }

    private void buildRails() {
        addRail(R.drawable.ic_apps, "All Apps", false);
        addRail(R.drawable.ic_home, "Pinned", true);
        addManageRail();
        styleRails();
    }

    private void addRail(int iconRes, String text, boolean pinnedFilter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        int ph = (int) (12 * d), pv = (int) (10 * d);
        row.setPadding(ph, pv, ph, pv);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int is = (int) (18 * d);
        row.addView(icon, new LinearLayout.LayoutParams(is, is));

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13f);
        label.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginStart((int) (12 * d));
        row.addView(label, llp);

        row.setOnClickListener(v -> {
            this.pinnedOnly = pinnedFilter;
            styleRails();
            rebuildGrid(false, selectedPkg);
            grid.post(grid::requestFocus);
        });
        row.setOnFocusChangeListener((v, has) -> styleRails());
        row.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                grid.requestFocus();
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (2 * d);
        railItems.addView(row, lp);
        rails.add(new Rail(row, icon, label, pinnedFilter));
    }

    private void addManageRail() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        int ph = (int) (12 * d), pv = (int) (10 * d);
        row.setPadding(ph, pv, ph, pv);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_settings);
        icon.setColorFilter(DIM);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int is = (int) (18 * d);
        row.addView(icon, new LinearLayout.LayoutParams(is, is));

        TextView label = new TextView(this);
        label.setText("Manage Apps");
        label.setTextSize(13f);
        label.setLetterSpacing(0.04f);
        label.setTextColor(DIM);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginStart((int) (12 * d));
        row.addView(label, llp);

        row.setOnClickListener(v ->
                startActivity(new Intent(this, ManageAppsActivity.class)));
        row.setOnFocusChangeListener((v, has) -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(10 * d);
            bg.setColor(has ? 0x22FFFFFF : 0x00000000);
            if (has) bg.setStroke((int) (1 * d), ACCENT);
            row.setBackground(bg);
            label.setTextColor(has ? ACCENT : DIM);
            icon.setColorFilter(has ? ACCENT : DIM);
            row.animate().scaleX(has ? 1.02f : 1f)
                    .scaleY(has ? 1.02f : 1f)
                    .setDuration(150).start();
        });
        row.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                grid.requestFocus();
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (10 * d);
        railItems.addView(row, lp);
    }

    private void styleRails() {
        for (Rail r : rails) {
            boolean isSelected = r.pinnedFilter == pinnedOnly;
            boolean focused = r.row.isFocused();
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(10 * d);
            bg.setColor(focused ? 0x22FFFFFF
                    : isSelected ? 0x14FFFFFF : 0x00000000);
            if (isSelected || focused)
                bg.setStroke((int) (1 * d), focused ? ACCENT : 0x33FFFFFF);
            r.row.setBackground(bg);
            r.label.setTextColor(isSelected ? ACCENT : DIM);
            r.icon.setColorFilter(isSelected ? ACCENT : DIM);
            r.row.animate().scaleX(focused ? 1.02f : 1f)
                    .scaleY(focused ? 1.02f : 1f)
                    .setDuration(150).start();
        }
    }

    private void setupGrid() {
        grid.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private int last = -1;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                                                 View view, int position, long id) {
                if (last >= 0 && last != position) {
                    View old = parent.getChildAt(last - parent.getFirstVisiblePosition());
                    if (old instanceof FocusedTile)
                        ((FocusedTile) old).setFocusedState(false);
                }
                if (view instanceof FocusedTile)
                    ((FocusedTile) view).setFocusedState(true);
                last = position;
                selected = position;
                selectedPkg = position >= 0 && position < apps.size()
                        ? apps.get(position).pkg : null;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                if (last >= 0) {
                    View old = parent.getChildAt(last - parent.getFirstVisiblePosition());
                    if (old instanceof FocusedTile)
                        ((FocusedTile) old).setFocusedState(false);
                }
                last = -1;
                selected = -1;
                selectedPkg = null;
            }
        });
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < apps.size()) launch(apps.get(position));
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            showAppMenu(position);
            return true;
        });
        grid.setKeyHandler(event -> {
            int code = event.getKeyCode();
            int pos = grid.getSelectedItemPosition();
            if (pos < 0) pos = selected;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (code == KeyEvent.KEYCODE_MENU) {
                    showAppMenu(pos);
                    return true;
                }
                if (isEnter(code)) {
                    if ((event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0)
                        longPressSeen = true;
                    if (event.getRepeatCount() == 0) {
                        longPressSeen = false;
                        centerDownAt = SystemClock.uptimeMillis();
                        heldPos = pos;
                    }
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && pos % 6 == 0) {
                    focusCurrentRail();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT
                        && (pos % 6 == 5 || pos == apps.size() - 1)) {
                    return true;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP && isEnter(code)) {
                long held = centerDownAt == 0 ? 0
                        : SystemClock.uptimeMillis() - centerDownAt;
                if (heldPos >= 0 && heldPos < apps.size()) {
                    if (longPressSeen || held >= HOLD_MS) showAppMenu(heldPos);
                    else launch(apps.get(heldPos));
                }
                heldPos = -1;
                centerDownAt = 0;
                longPressSeen = false;
                return true;
            }
            return false;
        });
    }

    private void focusCurrentRail() {
        for (Rail r : rails) {
            if (r.pinnedFilter == pinnedOnly) {
                r.row.requestFocus();
                return;
            }
        }
    }

    private static boolean isEnter(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void reloadPins() {
        pinned.clear();
        pinned.addAll(PinnedApps.load(prefs));
        if (adapter != null) adapter.notifyDataSetChanged();
        styleRails();
        updateCounts();
    }

    private void loadApps() {
        if (loading) return;
        loading = true;
        final String keepPkg = selectedPkg;
        new Thread(() -> {
            Set<String> hidden = new HashSet<>();
            for (String p : prefs.getString("hidden", "").split("\n")) {
                p = p.trim();
                if (!p.isEmpty()) hidden.add(p);
            }
            List<AppEntry> list = new ArrayList<>();
            for (AppEntry app : AppCatalog.launchable(this)) {
                if (app.pkg.equals(SETTINGS_PKG) || hidden.contains(app.pkg)) continue;
                list.add(app);
            }
            runOnUiThread(() -> {
                loading = false;
                allApps.clear();
                allApps.addAll(list);
                rebuildGrid(true, keepPkg);
            });
        }, "drawer-apps").start();
    }

    private void rebuildGrid(boolean forceSelect, String keepPkg) {
        if (!forceSelect) selectedPkg = selected >= 0 && selected < apps.size()
                ? apps.get(selected).pkg : selectedPkg;
        apps.clear();
        for (AppEntry app : allApps)
            if (!pinnedOnly || pinned.contains(app.pkg)) apps.add(app);

        adapter = new AppDrawerAdapter(this, apps, pinned, tileSize);
        grid.setAdapter(adapter);
        updateCounts();
        selected = -1;

        int position = 0;
        if (keepPkg != null) {
            for (int i = 0; i < apps.size(); i++)
                if (apps.get(i).pkg.equals(keepPkg)) { position = i; break; }
        }
        final int target = position;
        grid.postDelayed(() -> {
            if (!apps.isEmpty()) grid.setSelection(target);
            grid.requestFocus();
            if (selected < 0 && !apps.isEmpty()) {
                selected = target;
                selectedPkg = apps.get(target).pkg;
                View first = grid.getChildAt(target - grid.getFirstVisiblePosition());
                if (first instanceof FocusedTile)
                    ((FocusedTile) first).setFocusedState(true);
            }
        }, 120);
    }

    private void updateCounts() {
        title.setText(pinnedOnly ? "Pinned" : "All Apps");
        subtitle.setText(apps.size() + (apps.size() == 1 ? " app" : " apps")
                + "  ·  " + pinned.size() + " pinned");
        installed.setText(allApps.size() + " INSTALLED");
    }

    private void showAppMenu(int position) {
        if (position < 0 || position >= apps.size()) return;
        PopupWindow old = popup;
        if (old != null) {
            old.setOnDismissListener(null);
            old.dismiss();
        }

        AppEntry app = apps.get(position);
        boolean isPinned = pinned.contains(app.pkg);
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * d);
        bg.setColor(0xF2121212);
        bg.setStroke((int) (1 * d), 0x55FFFFFF);
        menu.setBackground(bg);
        int pad = (int) (6 * d);
        menu.setPadding(pad, pad, pad, pad);

        addMenuRow(menu, R.drawable.ic_play, "Open",
                () -> launch(app));
        addMenuRow(menu, R.drawable.ic_home,
                isPinned ? "Remove from Home" : "Pin to Home",
                () -> togglePin(app));
        addMenuRow(menu, R.drawable.ic_settings, "Manage Apps",
                () -> startActivity(new Intent(this, ManageAppsActivity.class)));

        int width = (int) (230 * d);
        PopupWindow p = new PopupWindow(menu, width,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);
        p.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        p.setOutsideTouchable(true);
        p.setFocusable(true);
        p.setElevation(14 * d);
        p.setOnDismissListener(() -> {
            if (popup == p) popup = null;
            heldPos = -1;
            centerDownAt = 0;
            longPressSeen = false;
            grid.requestFocus();
        });
        menu.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && code == KeyEvent.KEYCODE_BACK) {
                p.dismiss();
                return true;
            }
            return false;
        });
        popup = p;

        View anchor = grid.getChildAt(position - grid.getFirstVisiblePosition());
        if (anchor == null) anchor = grid;
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        menu.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int h = menu.getMeasuredHeight();
        int x = Math.min(Math.max((int) (12 * d), loc[0]),
                screenW - width - (int) (12 * d));
        int below = loc[1] + anchor.getHeight() + (int) (4 * d);
        if (below + h > screenH - (int) (12 * d))
            below = loc[1] - h - (int) (4 * d);
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.START,
                x, Math.max((int) (12 * d), below));
        menu.post(() -> {
            if (menu.getChildCount() > 0) menu.getChildAt(0).requestFocus();
        });
    }

    private void addMenuRow(LinearLayout menu, int iconRes, String text,
                            Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = (int) (12 * d), pv = (int) (10 * d);
        row.setPadding(ph, pv, ph, pv);
        row.setFocusable(true);
        row.setClickable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(DIM);
        int is = (int) (17 * d);
        row.addView(icon, new LinearLayout.LayoutParams(is, is));

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13f);
        label.setTextColor(TXT);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginStart((int) (11 * d));
        row.addView(label, llp);

        row.setOnFocusChangeListener((v, has) -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(9 * d);
            bg.setColor(has ? 0x22FFFFFF : 0x00000000);
            if (has) bg.setStroke((int) (1 * d), ACCENT);
            row.setBackground(bg);
            label.setTextColor(has ? 0xFFFFFFFF : TXT);
            icon.setColorFilter(has ? ACCENT : DIM);
        });
        row.setOnClickListener(v -> {
            if (popup != null) popup.dismiss();
            action.run();
        });
        menu.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void togglePin(AppEntry app) {
        List<String> pins = PinnedApps.load(prefs);
        if (pins.remove(app.pkg)) {
            PinnedApps.save(prefs, pins);
            pinned.remove(app.pkg);
            Toast.makeText(this, "Removed from Home", Toast.LENGTH_SHORT).show();
        } else {
            if (pins.size() >= PinnedApps.MAX) {
                Toast.makeText(this, "Home is full (6)", Toast.LENGTH_SHORT).show();
                return;
            }
            pins.add(app.pkg);
            PinnedApps.save(prefs, pins);
            pinned.add(app.pkg);
            Toast.makeText(this, "Pinned to Home", Toast.LENGTH_SHORT).show();
        }
        rebuildGrid(false, app.pkg);
    }

    private void launch(AppEntry app) {
        try { startActivity(app.intent); } catch (Exception ignored) {}
    }
}
