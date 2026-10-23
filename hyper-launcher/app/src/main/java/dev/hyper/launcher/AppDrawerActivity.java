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

/** TV app drawer: icon grid with pin badges, hold-OK dropdown actions. */
public final class AppDrawerActivity extends Activity {

    private static final String PREF = "hyper";
    private static final String SETTINGS_PKG = "com.android.tv.settings";
    private static final int ACCENT = 0xFFC8FF3D;
    private static final int TXT = 0xFFEDEDED;
    private static final int DIM = 0xFF9A9A9A;
    private static final int COLUMNS = 6;
    private static final long HOLD_MS = 350;

    private TvGridView grid;
    private ImageView brand;
    private TextView installed;
    private AppDrawerAdapter adapter;
    private SharedPreferences prefs;
    private float d;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> apps = new ArrayList<>();
    private final Set<String> pinned = new HashSet<>();

    private int heldPos = -1;
    private long centerDownAt;
    private boolean longPressSeen;
    private boolean holdMenuShown;

    private PopupWindow popup;
    private int pendingFocusId = View.NO_ID;
    private int tileSize;
    private boolean loading;
    private String selectedPkg;
    private int selected = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;

        setContentView(R.layout.activity_app_drawer);
        grid = findViewById(R.id.drawerGrid);
        installed = findViewById(R.id.drawerInstalled);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int gridW = screenW - (int) (80 * d) - (int) (24 * d);
        int gap = (int) (20 * d);
        tileSize = Math.max((int) (120 * d), (gridW - gap * (COLUMNS - 1)) / COLUMNS);

        setupGrid();

        brand = findViewById(R.id.drawerBrand);
        brand.setOnClickListener(v ->
                startActivity(new Intent(this, LauncherSettingsActivity.class)));
        brand.setOnFocusChangeListener((v, has) -> {
            FocusGlow.apply(v, has, 1.12f);
            if (has) clearGridFocus();
        });
        brand.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && code == KeyEvent.KEYCODE_DPAD_DOWN) {
                grid.requestFocus();
                return true;
            }
            return false;
        });
    }

    private void clearGridFocus() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof FocusedTile) ((FocusedTile) child).setFocusedState(false);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        reloadPins();
        loadApps();
    }

    @Override protected void onPause() {
        super.onPause();
        View focused = getCurrentFocus();
        if (focused != null && focused.getId() != View.NO_ID)
            pendingFocusId = focused.getId();
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

    private void setupGrid() {
        grid.setOnFocusChangeListener((v, has) -> {
            if (!has || selected < 0) return;
            View tile = grid.getChildAt(selected - grid.getFirstVisiblePosition());
            if (tile instanceof FocusedTile) ((FocusedTile) tile).setFocusedState(true);
        });
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
                        holdMenuShown = false;
                        centerDownAt = SystemClock.uptimeMillis();
                        heldPos = pos;
                    } else if (!holdMenuShown && heldPos >= 0 && heldPos < apps.size()
                            && (longPressSeen
                                || SystemClock.uptimeMillis() - centerDownAt >= HOLD_MS)) {
                        holdMenuShown = true;
                        showAppMenu(heldPos);
                    }
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && pos >= 0 && pos % COLUMNS == 0) {
                    brand.requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT
                        && (pos % COLUMNS == COLUMNS - 1 || pos == apps.size() - 1)) {
                    return true;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP && isEnter(code)) {
                long held = centerDownAt == 0 ? 0
                        : SystemClock.uptimeMillis() - centerDownAt;
                if (!holdMenuShown && heldPos >= 0 && heldPos < apps.size()) {
                    if (longPressSeen || held >= HOLD_MS) showAppMenu(heldPos);
                    else launch(apps.get(heldPos));
                }
                heldPos = -1;
                centerDownAt = 0;
                longPressSeen = false;
                holdMenuShown = false;
                return true;
            }
            return false;
        });
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
        apps.addAll(allApps);

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
            View restore = pendingFocusId == View.NO_ID
                    ? null : findViewById(pendingFocusId);
            pendingFocusId = View.NO_ID;
            if (restore != null && restore != grid && restore.isShown())
                restore.requestFocus();
            else
                grid.requestFocus();
            if (grid.hasFocus() && selected < 0 && !apps.isEmpty()) {
                selected = target;
                selectedPkg = apps.get(target).pkg;
                View first = grid.getChildAt(target - grid.getFirstVisiblePosition());
                if (first instanceof FocusedTile)
                    ((FocusedTile) first).setFocusedState(true);
            }
        }, 120);
    }

    private void updateCounts() {
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
        addMenuRow(menu, R.drawable.ic_info, "App Info",
                () -> openAppInfo(app));
        addMenuRow(menu, R.drawable.ic_delete, "Uninstall",
                () -> uninstall(app));

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

    private void openAppInfo(AppEntry app) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", app.pkg, null));
        try { startActivity(intent); } catch (Exception ignored) {}
    }

    private void uninstall(AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_DELETE,
                android.net.Uri.fromParts("package", app.pkg, null));
        try { startActivity(intent); } catch (Exception ignored) {}
    }
}
