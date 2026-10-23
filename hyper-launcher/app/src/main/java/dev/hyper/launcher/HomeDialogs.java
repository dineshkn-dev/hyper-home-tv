package dev.hyper.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owns the home's floating dialogs: app actions, search, and Wi-Fi. */
final class HomeDialogs {

    interface Host {
        List<AppEntry> visibleApps();
        void launch(AppEntry app);
        void openAppInfo(String pkg);
        void uninstall(AppEntry app);
        boolean isPinned(String pkg);
        void pin(AppEntry app);
        void unpin(AppEntry app);
        void focusBack();
        String ssid();
        ConnectivityManager cm();
    }

    private final Activity a;
    private final Host host;
    private Dialog dlg;
    private boolean open;

    HomeDialogs(Activity a, Host host) {
        this.a = a;
        this.host = host;
    }

    boolean isOpen() { return open; }

    void close() {
        Dialog d = dlg;
        dlg = null;
        open = false;
        if (d != null) d.dismiss();
        host.focusBack();
    }

    void showAppMenu(View anchor, AppEntry app) {
        close();
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout menu = new LinearLayout(a);
        menu.setOrientation(LinearLayout.VERTICAL);

        boolean onHome = host.isPinned(app.pkg);
        addMenuItem(menu, d, R.drawable.ic_home,
                onHome ? "Unpin from home" : "Pin to home", () -> {
                    close();
                    if (onHome) host.unpin(app);
                    else host.pin(app);
                });
        if (app.userInstalled) {
            addMenuItem(menu, d, R.drawable.ic_delete, "Uninstall", () -> {
                close();
                host.uninstall(app);
            });
        } else {
            addMenuItem(menu, d, R.drawable.ic_info, "App info", () -> {
                close();
                host.openAppInfo(app.pkg);
            });
        }
        showAnchoredMenu(anchor, menu);
    }

    void showSearch() {
        close();
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(a);
        title.setText("SEARCH");
        title.setTextSize(9.5f);
        title.setLetterSpacing(0.18f);
        title.setTextColor(0xFF9A9A9A);
        title.setPadding((int) (4 * d), (int) (2 * d),
                (int) (4 * d), (int) (8 * d));
        panel.addView(title);

        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setTextSize(15f);
        input.setTextColor(0xFFF5F5F5);
        input.setHint("Search apps");
        input.setHintTextColor(0xFF5A5A5A);
        input.setBackgroundResource(R.drawable.bg_search_field);
        input.setPadding((int) (12 * d), (int) (10 * d),
                (int) (12 * d), (int) (10 * d));
        panel.addView(input);

        ScrollView scroller = new ScrollView(a);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout results = new LinearLayout(a);
        results.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(results);
        panel.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (300 * d)));

        Runnable filter = () -> {
            results.removeAllViews();
            String q = input.getText().toString().trim()
                    .toLowerCase(Locale.getDefault());
            List<AppEntry> apps = new ArrayList<>(host.visibleApps());
            apps.sort((x, y) -> String.CASE_INSENSITIVE_ORDER.compare(
                    x.label.toString(), y.label.toString()));
            int count = 0;
            for (AppEntry app : apps) {
                if (!q.isEmpty() && !app.label.toString()
                        .toLowerCase(Locale.getDefault()).contains(q)) continue;
                addSearchRow(results, d, app);
                if (++count >= 12) break;
            }
            if (count == 0) {
                TextView empty = new TextView(a);
                empty.setText("No apps found");
                empty.setTextColor(0xFF5A5A5A);
                empty.setTextSize(13f);
                empty.setPadding((int) (8 * d), (int) (12 * d), 0, 0);
                results.addView(empty);
            }
        };
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st,
                                                    int c, int after) {}
            @Override public void onTextChanged(CharSequence s, int st,
                                                int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filter.run(); }
        });
        filter.run();

        Dialog dlg = buildDialog(panel, 520, -1, 0, 0.5f, 0.5f);
        Window win = dlg.getWindow();
        if (win != null) {
            win.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        input.post(() -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    void showWifiDialog() {
        close();
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);

        WifiManager wm = (WifiManager)
                a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return;
        WifiInfo wi = wm.getConnectionInfo();
        String ssid = host.ssid();

        TextView title = new TextView(a);
        title.setText(ssid.isEmpty() ? "Wi-Fi" : ssid);
        title.setTextSize(16f);
        title.setTextColor(0xFFF5F5F5);
        title.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d),
                (int) (4 * d));
        panel.addView(title);

        String band = wi.getFrequency() >= 5000 ? "5 GHz"
                : wi.getFrequency() > 0 ? "2.4 GHz" : "—";
        detailRow(panel, d, "Signal", wi.getRssi() + " dBm");
        detailRow(panel, d, "Band", band + " · " + wi.getLinkSpeed() + " Mbps");
        String ip = "—";
        ConnectivityManager cm = host.cm();
        try {
            for (android.net.LinkAddress la : cm.getLinkProperties(
                    cm.getActiveNetwork()).getLinkAddresses())
                if (la.getAddress() instanceof java.net.Inet4Address) {
                    ip = la.getAddress().getHostAddress();
                    break;
                }
        } catch (Exception ignored) {}
        detailRow(panel, d, "IP", ip);

        View div = new View(a);
        div.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * d));
        dlp.setMargins((int) (10 * d), (int) (6 * d), (int) (10 * d),
                (int) (6 * d));
        panel.addView(div, dlp);

        addMenuItem(panel, d, R.drawable.ic_settings, "Wi-Fi settings", () -> {
            close();
            try { a.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
            catch (Exception e) {
                try { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (Exception ignored) {}
            }
        });
        addMenuItem(panel, d, R.drawable.ic_wifi_off, "Disconnect", () -> {
            close();
            try { wm.disconnect(); } catch (Exception ignored) {}
        });

        View anchor = a.findViewById(R.id.wifiBtn);
        if (anchor == null) return;
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        buildDialog(panel, 230, loc[0] - (int) (10 * d),
                loc[1] + anchor.getHeight() + (int) (4 * d), 0f, 0f);
        panel.post(() -> {
            for (int i = 0; i < panel.getChildCount(); i++)
                if (panel.getChildAt(i).isFocusable()) {
                    panel.getChildAt(i).requestFocus();
                    break;
                }
        });
    }

    private void addSearchRow(LinearLayout results, float d, AppEntry app) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = (int) (10 * d), pv = (int) (8 * d);
        row.setPadding(ph, pv, ph, pv);
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x00000000);
        bg.setCornerRadius(8 * d);
        row.setBackground(bg);

        ImageView icon = new ImageView(a);
        icon.setImageDrawable(app.icon);
        row.addView(icon, new LinearLayout.LayoutParams(
                (int) (24 * d), (int) (24 * d)));

        TextView label = new TextView(a);
        label.setText(app.label);
        label.setTextSize(14f);
        label.setTextColor(0xFFF5F5F5);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginStart((int) (12 * d));
        row.addView(label, llp);

        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x22FFFFFF : 0x00000000);
            label.setTextColor(has ? 0xFFFFFFFF : 0xFFF5F5F5);
        });
        row.setOnClickListener(v -> {
            close();
            host.launch(app);
        });
        results.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void showAnchoredMenu(View anchor, LinearLayout menu) {
        float d = a.getResources().getDisplayMetrics().density;
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int menuW = (int) (195 * d);
        int menuH = (int) (menu.getChildCount() * 52 * d + 16 * d);
        int screenW = a.getResources().getDisplayMetrics().widthPixels;
        int screenH = a.getResources().getDisplayMetrics().heightPixels;
        int x = Math.min(Math.max(loc[0], (int) (16 * d)),
                screenW - menuW - (int) (16 * d));
        boolean flipped = false;
        int y = loc[1] + anchor.getHeight() + (int) (4 * d);
        if (y + menuH > screenH) {
            y = loc[1] - menuH - (int) (4 * d);
            flipped = true;
        }
        if (y < 0) y = (int) (16 * d);

        buildDialog(menu, 195, x, y, 0f, flipped ? 1f : 0f);
        menu.post(() -> menu.getChildAt(0).requestFocus());
    }

    private Dialog buildDialog(View content, int widthDp, int x, int y,
                               float pivotXFrac, float pivotYFrac) {
        float d = a.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF2141414, 0xF2080808});
        bg.setCornerRadius(22 * d);
        bg.setStroke((int) (1 * d), 0x33FFFFFF);
        content.setBackground(bg);
        int pad = (int) (8 * d);
        content.setPadding(pad, pad, pad, pad);

        Dialog dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(content);
        Window w = dlg.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.width = (int) (widthDp * d);
        if (x >= 0) {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = x;
            lp.y = y;
        }
        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        if (Build.VERSION.SDK_INT >= 31) lp.setBlurBehindRadius(48);
        lp.dimAmount = 0.28f;
        w.setAttributes(lp);
        dlg.setOnDismissListener(dd -> {
            if (this.dlg == dd) {
                this.dlg = null;
                open = false;
                host.focusBack();
            }
        });
        this.dlg = dlg;
        open = true;
        dlg.show();

        content.setAlpha(0f);
        content.post(() -> {
            content.setPivotX(content.getWidth() * pivotXFrac);
            content.setPivotY(content.getHeight() * pivotYFrac);
            float startScale = 0.88f;
            content.setScaleX(startScale);
            content.setScaleY(startScale);
            content.setTranslationY(pivotYFrac > 0 ? 10 * d : -10 * d);
            content.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(170)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();
        });
        return dlg;
    }

    private void detailRow(LinearLayout panel, float d, String k, String v) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding((int) (14 * d), (int) (3 * d), (int) (14 * d),
                (int) (3 * d));
        TextView key = new TextView(a);
        key.setText(k);
        key.setTextSize(12.5f);
        key.setTextColor(0xFF9A9A9A);
        row.addView(key, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView val = new TextView(a);
        val.setText(v);
        val.setTextSize(12.5f);
        val.setTextColor(0xFFF5F5F5);
        row.addView(val);
        panel.addView(row);
    }

    private void addMenuItem(LinearLayout menu, float d, int iconRes,
                             String text, Runnable action) {
        LinearLayout item = new LinearLayout(a);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        int ph = (int) (14 * d), pv = (int) (10 * d);
        item.setPadding(ph, pv, ph, pv);
        item.setFocusable(true);
        item.setClickable(true);

        ImageView icon = new ImageView(a);
        icon.setImageResource(iconRes);
        icon.setColorFilter(0xFF9A9A9A);
        int is = (int) (19 * d);
        item.addView(icon, new LinearLayout.LayoutParams(is, is));

        TextView label = new TextView(a);
        label.setText(text);
        label.setTextSize(14.5f);
        label.setTextColor(0xFFF5F5F5);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMarginStart((int) (13 * d));
        item.addView(label, tlp);

        item.setOnFocusChangeListener((v, has) -> {
            GradientDrawable ibg = new GradientDrawable();
            ibg.setColor(has ? 0x22FFFFFF : 0x00000000);
            ibg.setCornerRadius(10 * d);
            v.setBackground(ibg);
            label.setTextColor(has ? 0xFFFFFFFF : 0xFFF5F5F5);
        });
        item.setOnClickListener(v -> action.run());
        menu.addView(item, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
