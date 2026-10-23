package dev.hyper.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ManageAppsActivity extends Activity {

    public static final String EXTRA_SEARCH = "search";

    private static final String PREF = "hyper";
    private static final int ACCENT = 0xFFC8FF3D;
    private static final int TXT = 0xFFF5F5F5;
    private static final int DIM = 0xFF9A9A9A;
    private static final int FAINT = 0xFF5A5A5A;
    private static final int LINE = 0xFF242424;

    private SharedPreferences prefs;
    private float d;
    private GridView grid;
    private LinearLayout railItems, chips;
    private TextView subtitle, sortBtn, railInstalled, railUsed;
    private LinearLayout actMove, actUninstall, actInfo;
    private TextView actMoveLabel, actUninstallLabel, actInfoLabel;
    private ImageView actMoveIcon, actUninstallIcon, actInfoIcon;

    private final List<ManagedApp> allApps = new ArrayList<>();
    private final Map<String, ManagedApp> appMap = new LinkedHashMap<>();
    private final List<Folder> folders = new ArrayList<>();
    private final List<HomeItem> folderItems = new ArrayList<>();
    private List<HomeItem> items = new ArrayList<>();
    private String filter = "All";
    private int sortMode = 0;                 // 0 name, 1 size, 2 recent
    private HomeItem selected;
    private ManagedApp selectedApp;
    private View lastSelectedView;
    private int tileW, tileH;
    private boolean loading, pendingSearch;
    private final Map<String, TextView> chipViews = new LinkedHashMap<>();

    private static final class MenuItem {
        final int icon;
        final String label;
        final Runnable action;
        MenuItem(int icon, String label, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.action = action;
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;
        setContentView(R.layout.activity_manage_apps);

        grid = findViewById(R.id.manageGrid);
        railItems = findViewById(R.id.railItems);
        chips = findViewById(R.id.chips);
        subtitle = findViewById(R.id.manageSubtitle);
        sortBtn = findViewById(R.id.sortBtn);
        railInstalled = findViewById(R.id.railInstalled);
        railUsed = findViewById(R.id.railUsed);
        actMove = findViewById(R.id.actMove);
        actUninstall = findViewById(R.id.actUninstall);
        actInfo = findViewById(R.id.actInfo);
        actMoveLabel = findViewById(R.id.actMoveLabel);
        actUninstallLabel = findViewById(R.id.actUninstallLabel);
        actInfoLabel = findViewById(R.id.actInfoLabel);
        actMoveIcon = (ImageView) actMove.getChildAt(0);
        actUninstallIcon = (ImageView) actUninstall.getChildAt(0);
        actInfoIcon = (ImageView) actInfo.getChildAt(0);

        pendingSearch = getIntent().getBooleanExtra(EXTRA_SEARCH, false);

        findViewById(R.id.wifiBtn).setVisibility(View.GONE);

        updateDate();
        android.widget.TextClock clock = findViewById(R.id.clock);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("h:mm a");

        setupTopBar();
        buildRail();
        buildChips();
        setupActions();
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (lastSelectedView instanceof ManageTile)
                    ((ManageTile) lastSelectedView).setSelectedState(false);
                if (v instanceof ManageTile)
                    ((ManageTile) v).setSelectedState(true);
                lastSelectedView = v;
                if (pos >= 0 && pos < items.size()) {
                    selected = items.get(pos);
                    selectedApp = selected.isFolder() ? null
                            : appMap.get(selected.app.pkg);
                    updateActions();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {
                if (lastSelectedView instanceof ManageTile)
                    ((ManageTile) lastSelectedView).setSelectedState(false);
                lastSelectedView = null;
                selected = null;
                selectedApp = null;
                updateActions();
            }
        });
        grid.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < items.size()) showItemMenu(items.get(pos));
        });
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < items.size()) showItemMenu(items.get(pos));
            return true;
        });
        grid.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (code == KeyEvent.KEYCODE_DPAD_CENTER
                        || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    int pos = grid.getSelectedItemPosition();
                    if (pos >= 0 && pos < items.size()) {
                        showItemMenu(items.get(pos));
                        return true;
                    }
                }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    int pos = grid.getSelectedItemPosition();
                    int n = items.size();
                    if (pos >= 0 && pos + 6 >= n) {
                        actMove.requestFocus();
                        return true;
                    }
                }
            }
            return false;
        });

        sortBtn.setOnClickListener(v -> showSortMenu());
        sortBtn.setOnFocusChangeListener((v, has) -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(8 * d);
            bg.setColor(has ? 0x22FFFFFF : 0x00000000);
            bg.setStroke((int) (1 * d), has ? ACCENT : 0xFF2A2A2A);
            v.setBackground(bg);
            sortBtn.setTextColor(has ? TXT : DIM);
        });
        sortBtn.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN
                    && (code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_ENTER)) {
                showSortMenu();
                return true;
            }
            return false;
        });
    }

    @Override protected void onResume() {
        super.onResume();
        updateDate();
        loadApps();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN
                && grid != null && grid.hasFocus()
                && (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || e.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || e.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            int pos = grid.getSelectedItemPosition();
            if (pos >= 0 && pos < items.size()) {
                showItemMenu(items.get(pos));
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void updateDate() {
        TextView date = findViewById(R.id.date);
        if (date != null)
            date.setText(new java.text.SimpleDateFormat(
                    "EEEE, MMMM d", Locale.getDefault())
                    .format(new java.util.Date())
                    .toUpperCase(Locale.getDefault()));
    }

    private void setupTopBar() {
        setupAction(findViewById(R.id.searchBtn), this::showSearch);
        setupAction(findViewById(R.id.settingsBtn), () ->
                safeStart(new Intent(Settings.ACTION_SETTINGS)));
        ImageView brand = findViewById(R.id.brandBtn);
        if (brand != null) {
            brand.setOnClickListener(v -> startActivity(
                    new Intent(this, LauncherSettingsActivity.class)));
            brand.setOnFocusChangeListener((v, has) ->
                    FocusGlow.apply(v, has, 1.12f));
        }
    }

    private void setupAction(ImageView v, Runnable action) {
        if (v == null) return;
        v.setColorFilter(DIM);
        v.setOnClickListener(x -> action.run());
        v.setOnFocusChangeListener((x, has) -> {
            FocusGlow.apply(x, has, 1.16f);
            ((ImageView) x).setColorFilter(has ? TXT : DIM);
        });
    }

    // ---------- rail ----------

    private void buildRail() {
        railItems.addView(railRow(R.drawable.ic_home, "Home", false, () -> {
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        }));
        railItems.addView(railRow(R.drawable.ic_search, "Search", false,
                this::showSearch));
        railItems.addView(railRow(R.drawable.ic_apps, "My Apps", true,
                () -> grid.requestFocus()));
        railItems.addView(railRow(R.drawable.ic_settings, "Settings", false,
                () -> startActivity(new Intent(this,
                        LauncherSettingsActivity.class))));
    }

    private View railRow(int iconRes, String label, boolean selected,
                         Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);

        View accent = new View(this);
        GradientDrawable ab = new GradientDrawable();
        ab.setColor(ACCENT);
        ab.setCornerRadius(2 * d);
        accent.setBackground(ab);
        accent.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        row.addView(accent, new LinearLayout.LayoutParams(
                (int) (3 * d), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding((int) (14 * d), (int) (11 * d),
                (int) (10 * d), (int) (11 * d));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        content.addView(icon, new LinearLayout.LayoutParams(
                (int) (18 * d), (int) (18 * d)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(14f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart((int) (12 * d));
        content.addView(text, tlp);

        row.addView(content, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8 * d);
        bg.setColor(selected ? 0x1C1C1C : 0x00000000);
        row.setBackground(bg);
        icon.setColorFilter(selected ? ACCENT : DIM);
        text.setTextColor(selected ? TXT : DIM);

        row.setOnFocusChangeListener((v, has) -> {
            GradientDrawable fb = new GradientDrawable();
            fb.setCornerRadius(8 * d);
            fb.setColor(has ? 0x22FFFFFF : selected ? 0x1C1C1C : 0x00000000);
            v.setBackground(fb);
            icon.setColorFilter(has || selected ? ACCENT : DIM);
            text.setTextColor(has || selected ? TXT : DIM);
        });
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (2 * d);
        row.setLayoutParams(lp);
        return row;
    }

    // ---------- chips / sort ----------

    private void buildChips() {
        for (String name : new String[]{"All", "Streaming", "Music",
                "Games", "Utilities", "Folders"}) {
            TextView chip = new TextView(this);
            chip.setText(name);
            chip.setTextSize(12f);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding((int) (16 * d), (int) (7 * d),
                    (int) (16 * d), (int) (7 * d));
            chip.setFocusable(true);
            chip.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!chipsChildrenEmpty()) lp.setMarginStart((int) (8 * d));
            chips.addView(chip, lp);
            chipViews.put(name, chip);
            chip.setOnClickListener(v -> {
                filter = name;
                styleChips();
                rebuild(false);
                grid.requestFocus();
            });
            chip.setOnFocusChangeListener((v, has) -> {
                boolean on = name.equals(filter);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(100 * d);
                bg.setColor(on ? 0x1C1C1C : has ? 0x14FFFFFF : 0x00000000);
                chip.setBackground(bg);
                chip.setTextColor(on || has ? TXT : DIM);
            });
        }
        styleChips();
    }

    private boolean chipsChildrenEmpty() {
        return chips.getChildCount() == 0;
    }

    private void styleChips() {
        for (Map.Entry<String, TextView> e : chipViews.entrySet()) {
            boolean on = e.getKey().equals(filter);
            TextView chip = e.getValue();
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(100 * d);
            bg.setColor(on ? 0x1C1C1C : 0x00000000);
            chip.setBackground(bg);
            chip.setTextColor(on ? TXT : DIM);
        }
    }

    private void showSortMenu() {
        List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem(R.drawable.ic_sort, "Name", () -> {
            sortMode = 0; rebuild(true); focusGrid();
        }));
        menu.add(new MenuItem(R.drawable.ic_storage, "Size", () -> {
            sortMode = 1; rebuild(true); focusGrid();
        }));
        menu.add(new MenuItem(R.drawable.ic_clock, "Recently used", () -> {
            sortMode = 2; rebuild(true); focusGrid();
        }));
        showMenu("SORT BY", menu);
    }

    // ---------- load / rebuild ----------

    private void loadApps() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            Map<String, ManagedApp> found = queryApps();
            List<Folder> fs = loadFolders();
            runOnUiThread(() -> {
                loading = false;
                appMap.clear();
                appMap.putAll(found);
                allApps.clear();
                allApps.addAll(found.values());
                folders.clear();
                folders.addAll(fs);
                buildFolderItems();
                updateCounts();
                rebuild(true);
                if (pendingSearch) {
                    pendingSearch = false;
                    grid.postDelayed(this::showSearch, 200);
                }
            });
        }, "manage-apps").start();
    }

    private Map<String, ManagedApp> queryApps() {
        PackageManager pm = getPackageManager();
        Map<String, ManagedApp> found = new LinkedHashMap<>();
        Set<String> hidden = loadHidden();
        Map<String, Integer> uses = loadUses();
        for (AppEntry app : AppCatalog.launchable(this)) {
            String pkg = app.pkg;
            if (pkg.equals("com.android.tv.settings")) continue;
            ApplicationInfo ai = null;
            try { ai = pm.getApplicationInfo(pkg, 0); } catch (Exception ignored) {}
            found.put(pkg, new ManagedApp(app, appSize(ai),
                    category(ai, app.label.toString(), pkg),
                    hidden.contains(pkg), uses.getOrDefault(pkg, 0)));
        }
        return found;
    }

    private long appSize(ApplicationInfo ai) {
        if (ai == null) return 0;
        long size = 0;
        try { size += new File(ai.sourceDir).length(); } catch (Exception ignored) {}
        if (ai.splitSourceDirs != null)
            for (String p : ai.splitSourceDirs)
                try { size += new File(p).length(); } catch (Exception ignored) {}
        return size;
    }

    private String category(ApplicationInfo ai, String label, String pkg) {
        String l = (label + " " + pkg).toLowerCase(Locale.US);
        if (contains(l, "music", "spotify", "soundcloud", "tunein", "tidal",
                "radio", "audio")) return "Music";
        if (contains(l, "netflix", "prime video", "disney", "apple tv",
                "youtube", "twitch", "hotstar", "jiosphere", "plex", "kodi",
                "hulu", "video", "movie", "media center", "stream")) return "Streaming";
        if (contains(l, "game", "arcade", "cloud play", "stadia", "xbox",
                "geforce", "nvidia")) return "Games";
        if (ai != null) {
            if (ai.category == ApplicationInfo.CATEGORY_GAME) return "Games";
            if (ai.category == ApplicationInfo.CATEGORY_AUDIO) return "Music";
            if (ai.category == ApplicationInfo.CATEGORY_VIDEO) return "Streaming";
        }
        return "Utilities";
    }

    private static boolean contains(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    private void buildFolderItems() {
        folderItems.clear();
        for (Folder f : folders) {
            List<AppEntry> members = new ArrayList<>();
            for (String pkg : f.apps) {
                ManagedApp ma = appMap.get(pkg);
                if (ma != null) members.add(ma.app);
            }
            if (!members.isEmpty()) folderItems.add(HomeItem.folder(f, members));
        }
    }

    private void updateCounts() {
        railInstalled.setText(allApps.size() + " INSTALLED");
        railUsed.setText(deviceUsed() + " USED");
        subtitle.setText(allApps.size() + " apps installed");
        sortBtn.setText("⇅  Sort by: " + (sortMode == 0 ? "Name"
                : sortMode == 1 ? "Size" : "Recent"));
    }

    private String deviceUsed() {
        try {
            android.os.StatFs sf = new android.os.StatFs(
                    android.os.Environment.getDataDirectory().getPath());
            return ManagedApp.fmtSize(sf.getTotalBytes() - sf.getAvailableBytes());
        } catch (Exception e) {
            return "—";
        }
    }

    private void rebuild(boolean focus) {
        String key = selectedKey();
        List<HomeItem> next = new ArrayList<>();
        if (filter.equals("Folders")) {
            next.addAll(folderItems);
        } else if (filter.equals("All")) {
            next.addAll(folderItems);
            for (ManagedApp a : allApps) next.add(HomeItem.app(a.app));
        } else {
            for (ManagedApp a : allApps)
                if (a.category.equals(filter)) next.add(HomeItem.app(a.app));
        }
        sortItems(next);
        items = next;
        sortBtn.setText("⇅  Sort by: " + (sortMode == 0 ? "Name"
                : sortMode == 1 ? "Size" : "Recent"));

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int gridW = grid.getWidth();
        if (gridW <= 0)
            gridW = screenW - (int) (128 * d) - (int) (180 * d) - (int) (24 * d);
        int gap = (int) (14 * d);
        int vgap = (int) (8 * d);
        int cols = 6;
        tileW = (gridW - gap * (cols - 1)) / cols;
        tileH = (int) (73 * d);
        grid.setNumColumns(cols);
        grid.setColumnWidth(tileW);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(vgap);
        grid.setStretchMode(GridView.NO_STRETCH);
        grid.setAdapter(new ManageAdapter(this, items, appMap, tileW, tileH));

        int sel = 0;
        if (key != null)
            for (int i = 0; i < items.size(); i++)
                if (items.get(i).key().equals(key)) { sel = i; break; }
        final int fsel = sel;
        grid.post(() -> {
            grid.setSelection(fsel);
            if (focus) grid.requestFocus();
        });
        updateActions();
    }

    private String selectedKey() {
        return selected == null ? null : selected.key();
    }

    private void sortItems(List<HomeItem> list) {
        if (sortMode == 0) {
            list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                    a.label().toString(), b.label().toString()));
        } else if (sortMode == 1) {
            list.sort((a, b) -> {
                long sa = sizeOf(a), sb = sizeOf(b);
                if (sa != sb) return Long.compare(sb, sa);
                return String.CASE_INSENSITIVE_ORDER.compare(
                        a.label().toString(), b.label().toString());
            });
        } else {
            list.sort((a, b) -> {
                int ua = usesOf(a), ub = usesOf(b);
                if (ua != ub) return ub - ua;
                return String.CASE_INSENSITIVE_ORDER.compare(
                        a.label().toString(), b.label().toString());
            });
        }
    }

    private long sizeOf(HomeItem item) {
        if (!item.isFolder())
            return appMap.get(item.app.pkg) == null ? 0
                    : appMap.get(item.app.pkg).size;
        long s = 0;
        for (AppEntry a : item.folderApps)
            if (appMap.get(a.pkg) != null) s += appMap.get(a.pkg).size;
        return s;
    }

    private int usesOf(HomeItem item) {
        if (!item.isFolder())
            return appMap.get(item.app.pkg) == null ? 0
                    : appMap.get(item.app.pkg).uses;
        int s = 0;
        for (AppEntry a : item.folderApps)
            if (appMap.get(a.pkg) != null) s += appMap.get(a.pkg).uses;
        return s;
    }

    // ---------- actions ----------

    private void setupActions() {
        setupActionBarItem(actMove, actMoveIcon, actMoveLabel);
        setupActionBarItem(actUninstall, actUninstallIcon, actUninstallLabel);
        setupActionBarItem(actInfo, actInfoIcon, actInfoLabel);
        actMove.setOnClickListener(v -> handleAction(0));
        actUninstall.setOnClickListener(v -> handleAction(1));
        actInfo.setOnClickListener(v -> handleAction(2));
        actMove.setNextFocusUpId(grid.getId());
        actUninstall.setNextFocusUpId(grid.getId());
        actInfo.setNextFocusUpId(grid.getId());
        updateActions();
    }

    private void setupActionBarItem(LinearLayout row, ImageView icon,
                                    TextView label) {
        row.setOnFocusChangeListener((v, has) -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(6 * d);
            bg.setColor(has ? 0x1AFFFFFF : 0x00000000);
            v.setBackground(bg);
            label.setTextColor(has ? TXT : DIM);
        });
    }

    private void updateActions() {
        boolean has = selected != null;
        boolean folder = has && selected.isFolder();
        actMoveLabel.setText(folder ? "Open Folder" : "Move to Home");
        actUninstallLabel.setText(folder ? "Rename" : "Uninstall");
        actInfoLabel.setText(folder ? "Ungroup" : "App Info");
        actMoveIcon.setImageResource(folder ? R.drawable.ic_folder : R.drawable.ic_home);
        actUninstallIcon.setImageResource(folder ? R.drawable.ic_tag : R.drawable.ic_delete);
        actInfoIcon.setImageResource(folder ? R.drawable.ic_restore : R.drawable.ic_info);
        actMoveIcon.setColorFilter(DIM);
        actUninstallIcon.setColorFilter(DIM);
        actInfoIcon.setColorFilter(DIM);
        float alpha = has ? 1f : 0.35f;
        actMove.setAlpha(alpha);
        actUninstall.setAlpha(alpha);
        actInfo.setAlpha(alpha);
    }

    private void handleAction(int which) {
        if (selected == null) {
            Toast.makeText(this, "Select an app first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selected.isFolder()) {
            Folder f = selected.folder;
            if (which == 0) openFolder(selected);
            else if (which == 1) showRenameDialog(f);
            else ungroupFolder(f);
            return;
        }
        ManagedApp app = selectedApp;
        if (app == null) return;
        if (which == 0) moveToHome(app);
        else if (which == 1) uninstall(app);
        else openAppInfo(app.app.pkg);
    }

    private void moveToHome(ManagedApp app) {
        List<String> pins = PinnedApps.load(prefs);
        if (pins.contains(app.app.pkg)) {
            Toast.makeText(this, "Already on Home", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pins.size() >= PinnedApps.MAX) {
            Toast.makeText(this, "Home is full (6)", Toast.LENGTH_SHORT).show();
            return;
        }
        pins.add(app.app.pkg);
        PinnedApps.save(prefs, pins);
        Toast.makeText(this, "Moved to Home", Toast.LENGTH_SHORT).show();
    }

    private void uninstall(ManagedApp app) {
        if (!app.app.userInstalled) {
            Toast.makeText(this, "System app — use App Info", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivityForResult(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                    Uri.fromParts("package", app.app.pkg, null)), 0);
        } catch (Exception ignored) {}
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", pkg, null)));
        } catch (Exception ignored) {}
    }

    private void showItemMenu(HomeItem item) {
        if (item.isFolder()) {
            List<MenuItem> menu = new ArrayList<>();
            menu.add(new MenuItem(R.drawable.ic_folder, "Open", () -> openFolder(item)));
            menu.add(new MenuItem(R.drawable.ic_tag, "Rename",
                    () -> showRenameDialog(item.folder)));
            menu.add(new MenuItem(R.drawable.ic_restore, "Ungroup",
                    () -> ungroupFolder(item.folder)));
            showMenu(item.folder.name, menu);
            return;
        }
        ManagedApp app = appMap.get(item.app.pkg);
        if (app == null) return;
        Set<String> hidden = loadHidden();
        List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem(R.drawable.ic_play, "Open",
                () -> launchApp(app.app)));
        menu.add(new MenuItem(R.drawable.ic_home, "Move to Home",
                () -> moveToHome(app)));
        menu.add(new MenuItem(R.drawable.ic_hide,
                hidden.contains(app.app.pkg) ? "Unhide" : "Hide",
                () -> toggleHidden(app)));
        menu.add(new MenuItem(R.drawable.ic_folder, "Add to folder…",
                () -> showFolderPicker(app)));
        if (app.app.userInstalled)
            menu.add(new MenuItem(R.drawable.ic_delete, "Uninstall",
                    () -> uninstall(app)));
        menu.add(new MenuItem(R.drawable.ic_info, "App Info",
                () -> openAppInfo(app.app.pkg)));
        showMenu(app.app.label.toString(), menu);
    }

    private void toggleHidden(ManagedApp app) {
        Set<String> hidden = loadHidden();
        if (!hidden.remove(app.app.pkg)) hidden.add(app.app.pkg);
        saveHidden(hidden);
        Toast.makeText(this, hidden.contains(app.app.pkg)
                ? "Hidden from Home" : "Restored to Home", Toast.LENGTH_SHORT).show();
        loadApps();
    }

    // ---------- folders ----------

    private void openFolder(HomeItem item) {
        List<AppEntry> apps = item.folderApps;
        if (apps.isEmpty()) return;
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(item.folder.name);
        title.setTextSize(20f);
        title.setTextColor(TXT);
        title.setPadding((int) (12 * d), (int) (8 * d), (int) (12 * d), 0);
        panel.addView(title);

        TextView sub = new TextView(this);
        sub.setText(apps.size() + (apps.size() == 1 ? " app" : " apps"));
        sub.setTextSize(11.5f);
        sub.setTextColor(DIM);
        sub.setPadding((int) (12 * d), (int) (2 * d), (int) (12 * d), (int) (10 * d));
        panel.addView(sub);

        int widthDp = 640;
        int widthPx = (int) (widthDp * d);
        int pad = (int) (8 * d);
        int gap = (int) (12 * d);
        int cols = 4;
        int tw = (widthPx - pad * 2 - gap * (cols - 1)) / cols;
        int th = (int) (tw * 9f / 16f);
        GridView fg = new GridView(this);
        fg.setNumColumns(cols);
        fg.setColumnWidth(tw);
        fg.setHorizontalSpacing(gap);
        fg.setVerticalSpacing(gap);
        fg.setStretchMode(GridView.NO_STRETCH);
        fg.setSelector(new ColorDrawable(Color.TRANSPARENT));
        fg.setVerticalScrollBarEnabled(false);
        fg.setClipToPadding(false);
        fg.setClipChildren(false);
        fg.setAdapter(new AppAdapter(this, apps, tw, th));
        final View[] last = {null};
        fg.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (last[0] instanceof FocusedTile)
                    ((FocusedTile) last[0]).setFocusedState(false);
                if (v instanceof FocusedTile)
                    ((FocusedTile) v).setFocusedState(true);
                last[0] = v;
            }
            @Override public void onNothingSelected(AdapterView<?> p) {
                if (last[0] instanceof FocusedTile)
                    ((FocusedTile) last[0]).setFocusedState(false);
                last[0] = null;
            }
        });
        final Dialog[] holder = new Dialog[1];
        fg.setOnItemClickListener((p, v, pos, id) -> {
            if (holder[0] != null) holder[0].dismiss();
            launchApp(apps.get(pos));
        });
        int rows = (apps.size() + cols - 1) / cols;
        int labelH = (int) (28 * d);
        int visible = Math.min(rows, 3);
        int gh = visible * (th + labelH) + (visible - 1) * gap;
        panel.addView(fg, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gh));
        holder[0] = showPanel(panel, widthDp);
        fg.post(() -> { fg.requestFocus(); fg.setSelection(0); });
    }

    private void showFolderPicker(ManagedApp app) {
        List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem(R.drawable.ic_folder, "New folder", () -> {
            Folder f = new Folder(java.util.UUID.randomUUID().toString(),
                    nextFolderName());
            f.apps.add(app.app.pkg);
            folders.add(f);
            saveFolders();
            buildFolderItems();
            rebuild(true);
            Toast.makeText(this, "Added to " + f.name, Toast.LENGTH_SHORT).show();
        }));
        for (Folder f : folders)
            menu.add(new MenuItem(R.drawable.ic_folder,
                    f.name + " (" + f.apps.size() + ")", () -> {
                        moveIntoFolder(app.app.pkg, f);
                        Toast.makeText(this, "Added to " + f.name,
                                Toast.LENGTH_SHORT).show();
                    }));
        showMenu("ADD TO FOLDER", menu);
    }

    private void moveIntoFolder(String pkg, Folder target) {
        for (Folder f : folders)
            if (f != target) f.apps.remove(pkg);
        if (!target.apps.contains(pkg)) target.apps.add(pkg);
        saveFolders();
        buildFolderItems();
        rebuild(true);
    }

    private String nextFolderName() {
        int n = 1;
        while (true) {
            String name = "Folder " + n;
            boolean used = false;
            for (Folder f : folders)
                if (f.name.equalsIgnoreCase(name)) { used = true; break; }
            if (!used) return name;
            n++;
        }
    }

    private void showRenameDialog(Folder folder) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Rename folder");
        title.setTextSize(9.5f);
        title.setLetterSpacing(0.18f);
        title.setTextColor(DIM);
        title.setPadding((int) (12 * d), (int) (6 * d), (int) (12 * d), (int) (8 * d));
        panel.addView(title);

        EditText input = new EditText(this);
        input.setText(folder.name);
        input.setSingleLine(true);
        input.setTextSize(16f);
        input.setTextColor(TXT);
        input.setSelectAllOnFocus(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1CFFFFFF);
        bg.setCornerRadius(10 * d);
        bg.setStroke((int) (1 * d), 0xFF2A2A2A);
        input.setBackground(bg);
        input.setPadding((int) (12 * d), (int) (10 * d),
                (int) (12 * d), (int) (10 * d));
        panel.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final Dialog[] holder = new Dialog[1];
        Runnable save = () -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) folder.name = name;
            saveFolders();
            buildFolderItems();
            rebuild(true);
            if (holder[0] != null) holder[0].dismiss();
        };
        View saveRow = menuRow(R.drawable.ic_tag, "Save name", save);
        panel.addView(saveRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { save.run(); return true; }
            return false;
        });
        holder[0] = showPanel(panel, 380);
        input.post(() -> {
            input.requestFocus();
            input.selectAll();
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void ungroupFolder(Folder folder) {
        folders.remove(folder);
        saveFolders();
        buildFolderItems();
        rebuild(true);
        Toast.makeText(this, "Folder ungrouped", Toast.LENGTH_SHORT).show();
    }

    // ---------- search ----------

    private void showSearch() {
        if (allApps.isEmpty()) {
            pendingSearch = true;
            Toast.makeText(this, "Loading apps…", Toast.LENGTH_SHORT).show();
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("SEARCH");
        title.setTextSize(9.5f);
        title.setLetterSpacing(0.18f);
        title.setTextColor(DIM);
        title.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (8 * d));
        panel.addView(title);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(15f);
        input.setTextColor(TXT);
        input.setHint("Search apps");
        input.setHintTextColor(FAINT);
        input.setBackgroundResource(R.drawable.bg_search_field);
        input.setPadding((int) (12 * d), (int) (10 * d),
                (int) (12 * d), (int) (10 * d));
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        panel.addView(input);

        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(results);
        panel.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (300 * d)));

        final Dialog[] holder = new Dialog[1];
        Runnable filter = () -> {
            results.removeAllViews();
            String q = input.getText().toString().trim()
                    .toLowerCase(Locale.getDefault());
            List<ManagedApp> list = new ArrayList<>(allApps);
            list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                    a.app.label.toString(), b.app.label.toString()));
            int n = 0;
            for (ManagedApp app : list) {
                if (!q.isEmpty() && !app.app.label.toString()
                        .toLowerCase(Locale.getDefault()).contains(q)) continue;
                results.addView(searchRow(app, holder), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                if (++n >= 12) break;
            }
            if (n == 0) {
                TextView empty = new TextView(this);
                empty.setText("No apps found");
                empty.setTextColor(FAINT);
                empty.setTextSize(13f);
                empty.setPadding((int) (8 * d), (int) (12 * d), 0, 0);
                results.addView(empty);
            }
        };
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st,
                                                    int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st,
                                                int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filter.run(); }
        });
        filter.run();
        holder[0] = showPanel(panel, 520);
        input.post(() -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private View searchRow(ManagedApp app, Dialog[] holder) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        int ph = (int) (10 * d), pv = (int) (8 * d);
        row.setPadding(ph, pv, ph, pv);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x00000000);
        bg.setCornerRadius(8 * d);
        row.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.app.icon);
        row.addView(icon, new LinearLayout.LayoutParams(
                (int) (24 * d), (int) (24 * d)));

        TextView label = new TextView(this);
        label.setText(app.app.label);
        label.setTextSize(14f);
        label.setTextColor(TXT);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginStart((int) (12 * d));
        row.addView(label, llp);

        TextView size = new TextView(this);
        size.setText(ManagedApp.fmtSize(app.size));
        size.setTextSize(10.5f);
        size.setTextColor(DIM);
        row.addView(size);

        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x22FFFFFF : 0x00000000);
            label.setTextColor(has ? 0xFFFFFFFF : TXT);
        });
        row.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            selectApp(app.app.pkg);
        });
        return row;
    }

    private void selectApp(String pkg) {
        filter = "All";
        styleChips();
        rebuild(false);
        for (int i = 0; i < items.size(); i++) {
            HomeItem item = items.get(i);
            if (!item.isFolder() && item.app.pkg.equals(pkg)) {
                final int idx = i;
                grid.post(() -> {
                    grid.setSelection(idx);
                    grid.requestFocus();
                });
                return;
            }
        }
    }

    // ---------- menus / dialogs ----------

    private LinearLayout menuRow(int icon, String text, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        int ph = (int) (12 * d), pv = (int) (10 * d);
        row.setPadding(ph, pv, ph, pv);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x00000000);
        bg.setCornerRadius(8 * d);
        row.setBackground(bg);

        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(0xFF9A9A9A);
        row.addView(ic, new LinearLayout.LayoutParams(
                (int) (18 * d), (int) (18 * d)));

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14f);
        label.setTextColor(TXT);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginStart((int) (12 * d));
        row.addView(label, llp);

        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x22FFFFFF : 0x00000000);
            label.setTextColor(has ? 0xFFFFFFFF : TXT);
        });
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void showMenu(String title, List<MenuItem> actions) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView head = new TextView(this);
        head.setText(title.toUpperCase(Locale.getDefault()));
        head.setTextSize(9.5f);
        head.setLetterSpacing(0.18f);
        head.setTextColor(DIM);
        head.setSingleLine(true);
        head.setEllipsize(TextUtils.TruncateAt.END);
        head.setPadding((int) (12 * d), (int) (6 * d), (int) (12 * d), (int) (8 * d));
        panel.addView(head);

        final Dialog[] holder = new Dialog[1];
        for (MenuItem item : actions) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            int ph = (int) (12 * d), pv = (int) (10 * d);
            row.setPadding(ph, pv, ph, pv);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x00000000);
            bg.setCornerRadius(8 * d);
            row.setBackground(bg);

            ImageView ic = new ImageView(this);
            ic.setImageResource(item.icon);
            ic.setColorFilter(0xFF9A9A9A);
            row.addView(ic, new LinearLayout.LayoutParams(
                    (int) (18 * d), (int) (18 * d)));

            TextView label = new TextView(this);
            label.setText(item.label);
            label.setTextSize(14f);
            label.setTextColor(TXT);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            llp.setMarginStart((int) (12 * d));
            row.addView(label, llp);

            row.setOnFocusChangeListener((v, has) -> {
                bg.setColor(has ? 0x22FFFFFF : 0x00000000);
                label.setTextColor(has ? 0xFFFFFFFF : TXT);
            });
            row.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                item.action.run();
            });
            panel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(panel);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(scroller);
        holder[0] = showPanel(wrap, 300);
        panel.post(() -> {
            for (int i = 0; i < panel.getChildCount(); i++)
                if (panel.getChildAt(i).isFocusable()) {
                    panel.getChildAt(i).requestFocus();
                    break;
                }
        });
    }

    private Dialog showPanel(View content, int widthDp) {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF2141414, 0xF2080808});
        bg.setCornerRadius(20 * d);
        bg.setStroke((int) (1 * d), 0x33FFFFFF);
        content.setBackground(bg);
        int pad = (int) (8 * d);
        content.setPadding(pad, pad, pad, pad);

        Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(content);
        Window w = dlg.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width = (int) (widthDp * d);
        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        if (Build.VERSION.SDK_INT >= 31) lp.setBlurBehindRadius(48);
        lp.dimAmount = 0.35f;
        w.setAttributes(lp);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dlg.setOnDismissListener(dd -> grid.requestFocus());
        dlg.show();
        return dlg;
    }

    private void launchApp(AppEntry app) {
        try { startActivity(app.intent); } catch (Exception ignored) {}
        Map<String, Integer> uses = loadUses();
        uses.put(app.pkg, uses.getOrDefault(app.pkg, 0) + 1);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : uses.entrySet())
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        prefs.edit().putString("uses", sb.toString()).apply();
    }

    private void focusGrid() {
        grid.post(() -> grid.requestFocus());
    }

    private void safeStart(Intent intent) {
        try { startActivity(intent); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) {}
        }
    }

    // ---------- persistence ----------

    private List<Folder> loadFolders() {
        List<Folder> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("folders", "[]"));
            for (int i = 0; i < arr.length(); i++)
                out.add(Folder.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    private void saveFolders() {
        JSONArray arr = new JSONArray();
        for (Folder f : folders) arr.put(f.toJson());
        prefs.edit().putString("folders", arr.toString()).apply();
    }

    private Set<String> loadHidden() {
        Set<String> out = new HashSet<>();
        for (String p : prefs.getString("hidden", "").split("\n")) {
            p = p.trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    private void saveHidden(Set<String> hidden) {
        StringBuilder sb = new StringBuilder();
        for (String p : hidden) sb.append(p).append('\n');
        prefs.edit().putString("hidden", sb.toString()).apply();
    }

    private Map<String, Integer> loadUses() {
        Map<String, Integer> m = new HashMap<>();
        for (String line : prefs.getString("uses", "").split("\n")) {
            int i = line.lastIndexOf('=');
            if (i < 1) continue;
            try {
                m.put(line.substring(0, i),
                        Integer.parseInt(line.substring(i + 1).trim()));
            } catch (NumberFormatException ignored) {}
        }
        return m;
    }
}
