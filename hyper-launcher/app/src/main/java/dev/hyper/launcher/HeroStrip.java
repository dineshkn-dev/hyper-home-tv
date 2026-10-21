package dev.hyper.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Full-width hero strip under the top bar — live stat chips (RAM, CPU
 * clock, storage, temperature, network, uptime) plus an Optimize button
 * that sweeps cached background apps. Refreshes on its own 2.5s tick
 * while visible; framework APIs only.
 */
public final class HeroStrip extends LinearLayout {
    private static final long TICK_MS = 2500;
    private static final int DIM = 0xFF8A94AA;
    private static final int TXT = 0xFFE8EDF8;
    private static final int BLUE = 0xFF5B9DFF;

    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private Chip ram, cpu, store, temp, net, up;
    private boolean optimizing;

    // /proc/stat delta sampling (blocked on many TVs → falls back to freq)
    private long lastTotal = -1, lastIdle = -1;
    // thermal zone chosen on first scan (-1 = none readable)
    private int tempZone = -1;
    private boolean tempScanned;
    private String tempLabel = "SoC";

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (getVisibility() == VISIBLE && isAttachedToWindow()) update();
            handler.postDelayed(this, TICK_MS);
        }
    };

    public HeroStrip(Context c) { super(c); init(); }
    public HeroStrip(Context c, AttributeSet a) { super(c, a); init(); }

    // ---------- layout ----------

    private static final class Chip {
        LinearLayout box, head;
        TextView value, sub;
        View fill, gap;
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        float d = getResources().getDisplayMetrics().density;

        ram = addChip(R.drawable.ic_ram, "MEMORY", d);
        cpu = addChip(R.drawable.ic_cpu, "PROCESSOR", d);
        store = addChip(R.drawable.ic_storage, "STORAGE", d);
        temp = addChip(R.drawable.ic_thermo, "TEMP", d);
        net = addChip(R.drawable.ic_wifi, "NETWORK", d);
        up = addChip(R.drawable.ic_clock, "UPTIME", d);

        // the RAM chip doubles as the Optimize control
        ram.box.setFocusable(true);
        ram.box.setClickable(true);
        ram.box.setOnClickListener(v -> optimize());
        ram.box.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                    .setDuration(140).start();
            setBg(v, has ? 0x2A5B9DFF : 0x0FFFFFFF, d);
        });
        // tiny bolt next to the label hints it's actionable
        ImageView bolt = new ImageView(getContext());
        bolt.setImageResource(R.drawable.ic_bolt);
        bolt.setColorFilter(BLUE);
        LayoutParams bl = new LayoutParams((int) (8 * d), (int) (8 * d));
        bl.setMarginStart((int) (4 * d));
        ram.head.addView(bolt, bl);
    }

    /** compact stat chip: icon left, label + "value · sub" stacked right */
    private Chip addChip(int icon, String label, float d) {
        Chip c = new Chip();
        c.box = new LinearLayout(getContext());
        c.box.setOrientation(HORIZONTAL);
        c.box.setGravity(Gravity.CENTER_VERTICAL);
        c.box.setPadding((int) (10 * d), (int) (5 * d),
                (int) (10 * d), (int) (5 * d));
        setBg(c.box, 0x0FFFFFFF, d);

        ImageView ic = new ImageView(getContext());
        ic.setImageResource(icon);
        ic.setColorFilter(DIM);
        LayoutParams il = new LayoutParams((int) (15 * d), (int) (15 * d));
        il.setMarginEnd((int) (8 * d));
        c.box.addView(ic, il);

        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);

        c.head = new LinearLayout(getContext());
        c.head.setGravity(Gravity.CENTER_VERTICAL);
        TextView lb = new TextView(getContext());
        lb.setText(label);
        lb.setTextSize(8.5f);
        lb.setLetterSpacing(0.08f);
        lb.setTextColor(DIM);
        lb.setSingleLine(true);
        c.head.addView(lb);
        col.addView(c.head);

        LinearLayout vals = new LinearLayout(getContext());
        c.value = new TextView(getContext());
        c.value.setTextSize(13f);
        c.value.setTextColor(TXT);
        c.value.setSingleLine(true);
        c.value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        vals.addView(c.value);
        c.sub = new TextView(getContext());
        c.sub.setTextSize(9f);
        c.sub.setTextColor(DIM);
        c.sub.setSingleLine(true);
        c.sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.sub.setText(" ");
        LayoutParams sl = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        sl.setMarginStart((int) (4 * d));
        vals.addView(c.sub, sl);
        col.addView(vals);

        // 2dp usage bar — fill + gap share the row via weights
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(HORIZONTAL);
        GradientDrawable fb = new GradientDrawable();
        fb.setColor(BLUE);
        fb.setCornerRadius(2 * d);
        c.fill = new View(getContext());
        c.fill.setBackground(fb);
        c.gap = new View(getContext());
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0x14FFFFFF);
        gb.setCornerRadius(2 * d);
        c.gap.setBackground(gb);
        bar.addView(c.fill, new LayoutParams(0, (int) (2 * d), 0.5f));
        bar.addView(c.gap, new LayoutParams(0, (int) (2 * d), 0.5f));
        LayoutParams barLp = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        barLp.topMargin = (int) (4 * d);
        col.addView(bar, barLp);

        c.box.addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginEnd((int) (8 * d));
        addView(c.box, lp);
        return c;
    }

    private static void setBar(Chip c, float frac) {
        frac = Math.max(0f, Math.min(1f, frac));
        ((LayoutParams) c.fill.getLayoutParams()).weight = frac;
        ((LayoutParams) c.gap.getLayoutParams()).weight = 1f - frac;
        c.fill.requestLayout();
        c.gap.requestLayout();
    }

    private static void setBg(View v, int color, float d) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(12 * d);
        v.setBackground(bg);
    }

    // ---------- stat updates ----------

    private void update() {
        Context ctx = getContext();
        try {
            ActivityManager am =
                    (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            float used = (float) (mi.totalMem - mi.availMem) / mi.totalMem;
            ram.value.setText(Math.round(used * 100) + "%");
            ram.sub.setText("· " + fmtBytes(mi.availMem) + " free");
            setBar(ram, used);
        } catch (Exception e) { ram.value.setText("—"); }

        int pct = cpuPct();
        int mhz = cpuMhz();
        if (pct >= 0) cpu.value.setText("CPU " + pct + "%");
        else cpu.value.setText(mhz > 0
                ? String.format(Locale.US, "%.2f GHz", mhz / 1000f) : "—");
        cpu.sub.setText("· "
                + Runtime.getRuntime().availableProcessors() + " cores");
        setBar(cpu, mhz > 0 ? mhz / (float) maxMhz() : 0f);

        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            long total = sf.getTotalBytes(), free = sf.getAvailableBytes();
            store.value.setText(fmtBytes(free));
            store.sub.setText("· free of " + fmtBytes(total));
            setBar(store, (float) (total - free) / total);
        } catch (Exception e) { store.value.setText("—"); }

        float t = tempC();
        if (Float.isNaN(t)) temp.box.setVisibility(GONE);
        else {
            temp.box.setVisibility(VISIBLE);
            temp.value.setText(Math.round(t) + "°C");
            temp.sub.setText("· " + tempLabel);
            setBar(temp, t / 90f);
        }

        updateNet(ctx);

        long upMin = SystemClock.elapsedRealtime() / 60_000;
        up.value.setText(upMin >= 1440
                ? (upMin / 1440) + "d " + (upMin % 1440) / 60 + "h"
                : upMin >= 60
                        ? (upMin / 60) + "h " + (upMin % 60) + "m"
                        : upMin + "m");
        up.sub.setText("· since boot");
        setBar(up, 0f);
    }

    private void updateNet(Context ctx) {
        int mbps = -1;
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null)
                mbps = wm.getConnectionInfo().getLinkSpeed();
        } catch (Exception ignored) { }
        String ip = ipAddr(ctx);
        if (mbps > 0) {
            net.value.setText(mbps + " Mbps");
            net.sub.setText("· Wi-Fi");
        } else if (ip != null) {
            net.value.setText(ip);
            net.sub.setText("· wired");
        } else {
            net.value.setText("Offline");
            net.sub.setText(" ");
        }
        setBar(net, mbps > 0 ? Math.min(1f, mbps / 600f) : 0f);
    }

    private static String ipAddr(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null || cm.getLinkProperties(n) == null) return null;
            for (LinkAddress a : cm.getLinkProperties(n).getLinkAddresses()) {
                InetAddress ia = a.getAddress();
                if (ia instanceof Inet4Address && !ia.isLoopbackAddress())
                    return ia.getHostAddress();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static String fmtBytes(long b) {
        if (b >= 1_000_000_000L)
            return String.format(Locale.US, "%.1f GB", b / 1e9);
        return (b / 1_000_000L) + " MB";
    }

    private int cpuPct() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/stat"));
            String line = r.readLine();
            r.close();
            if (line == null || !line.startsWith("cpu ")) return -1;
            String[] f = line.trim().split("\\s+");
            long idle = Long.parseLong(f[4]) + Long.parseLong(f[5]);
            long total = 0;
            for (int i = 1; i < f.length && i < 9; i++)
                total += Long.parseLong(f[i]);
            if (lastTotal < 0) { lastTotal = total; lastIdle = idle; return -1; }
            long dt = total - lastTotal, di = idle - lastIdle;
            lastTotal = total; lastIdle = idle;
            if (dt <= 0) return -1;
            return Math.min(100, Math.max(0, (int) (100 * (dt - di) / dt)));
        } catch (Exception e) { return -1; }
    }

    /** max scaling_cur_freq across online cores, in MHz (0 = unreadable) */
    private static int cpuMhz() {
        int best = 0;
        for (int i = 0; i < 16; i++) {
            String s = readText(new File(
                    "/sys/devices/system/cpu/cpu" + i
                            + "/cpufreq/scaling_cur_freq"));
            if (s == null) { if (i == 0) break; continue; }
            try { best = Math.max(best, Integer.parseInt(s.trim()) / 1000); }
            catch (NumberFormatException ignored) { }
        }
        return best;
    }

    private static int maxMhz() {
        String s = readText(new File(
                "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"));
        try { return s == null ? 0 : Integer.parseInt(s.trim()) / 1000; }
        catch (NumberFormatException e) { return 0; }
    }

    private float tempC() {
        if (!tempScanned) {
            tempScanned = true;
            int sane = -1;
            for (int i = 0; i < 24; i++) {
                File z = new File("/sys/class/thermal/thermal_zone" + i);
                if (!z.exists()) continue;
                float v = readZone(i);
                if (Float.isNaN(v)) continue;
                String type = readText(new File(z, "type"));
                if (type != null) {
                    String lt = type.toLowerCase();
                    if (lt.contains("cpu") || lt.contains("soc")
                            || lt.contains("ap") || lt.contains("arm")) {
                        tempZone = i;
                        tempLabel = type;
                        break;
                    }
                }
                if (sane < 0 && v > 10 && v < 120) sane = i;
            }
            if (tempZone < 0) tempZone = sane;
        }
        if (tempZone >= 0) {
            float v = readZone(tempZone);
            if (!Float.isNaN(v)) return v;
        }
        try {
            Intent bi = getContext().registerReceiver(null,
                    new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            // no real battery (most TVs) → the temp field is a placeholder
            if (bi != null && bi.getBooleanExtra(
                    BatteryManager.EXTRA_PRESENT, true)) {
                int t = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,
                        Integer.MIN_VALUE);
                if (t != Integer.MIN_VALUE && t >= 100 && t <= 900) {
                    tempLabel = "battery";
                    return t / 10f;
                }
            }
        } catch (Exception ignored) { }
        return Float.NaN;
    }

    private static float readZone(int i) {
        String s = readText(
                new File("/sys/class/thermal/thermal_zone" + i + "/temp"));
        if (s == null) return Float.NaN;
        try {
            float v = Float.parseFloat(s.trim());
            if (v > 500) v /= 1000f;   // millidegrees
            return (v > -20 && v < 150) ? v : Float.NaN;
        } catch (NumberFormatException e) { return Float.NaN; }
    }

    private static String readText(File f) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            String s = r.readLine();
            r.close();
            return s;
        } catch (Exception e) { return null; }
    }

    // ---------- optimize ----------

    private void optimize() {
        if (optimizing) return;
        optimizing = true;
        ram.sub.setText("· sweeping…");
        sweep(getContext(), freed -> {
            optimizing = false;
            Toast.makeText(getContext(),
                    freed > 0 ? "Freed " + freed + " MB"
                            : "Memory already optimized",
                    Toast.LENGTH_SHORT).show();
            update();
        });
    }

    /**
     * Kills cached background processes of every launchable app, then
     * reports freed MB on the main thread. Shared by the RAM chip and
     * the settings "Optimize memory" row.
     */
    static void sweep(Context ctx, java.util.function.LongConsumer done) {
        android.os.Handler h =
                new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            ActivityManager am =
                    (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
            try { am.getMemoryInfo(before); } catch (Exception ignored) { }

            // Sweep every installed package — launchable-only queries miss
            // the biggest memory holders (GMS, providers, keychain…).
            Set<String> pkgs = new HashSet<>();
            PackageManager pm = ctx.getPackageManager();
            List<PackageInfo> pis = Build.VERSION.SDK_INT >= 33
                    ? pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                    : pm.getInstalledPackages(0);
            for (PackageInfo pi : pis) pkgs.add(pi.packageName);
            pkgs.remove(ctx.getPackageName());
            for (String p : pkgs) {
                try { am.killBackgroundProcesses(p); } catch (Exception ignored) { }
            }

            // killBackgroundProcesses is neutered for third-party apps on
            // some firmware (silent no-op — calls succeed, nothing dies).
            // Fallback that actually works: allocate native memory until the
            // PSI stall thresholds trip and lmkd reaps cached processes,
            // then release. We're foreground — cached apps die long before us.
            // Skip entirely when memory is already plentiful.
            if (before.availMem < before.totalMem * 3L / 4L) {
                java.util.ArrayList<java.nio.ByteBuffer> chunks =
                        new java.util.ArrayList<>();
                try {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    for (int i = 0; i < 12; i++) {              // ≤ 576 MB pressure
                        am.getMemoryInfo(mi);
                        if (mi.availMem < 96L * 1024 * 1024) break;
                        java.nio.ByteBuffer b =
                                java.nio.ByteBuffer.allocateDirect(48 << 20);
                        for (int off = 0; off < b.capacity(); off += 4096)
                            b.put(off, (byte) 1);               // commit every page
                        chunks.add(b);
                        SystemClock.sleep(50);
                    }
                    SystemClock.sleep(1200);                    // hold past PSI stall
                } catch (Throwable ignored) { }

                // Release fully: direct buffers only free when their Cleaner
                // runs — clear refs, GC twice, let the kernel settle.
                chunks.clear();
                System.gc();
                System.runFinalization();
                System.gc();
                SystemClock.sleep(1500);
            }
            ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
            try { am.getMemoryInfo(after); } catch (Exception ignored) { }
            final long freed =
                    Math.max(0, after.availMem - before.availMem) / (1024 * 1024);
            android.util.Log.i("HeroStrip", "sweep done: freed=" + freed
                    + "MB after=" + after.availMem / 1048576 + "MB");
            h.post(() -> done.accept(freed));
        }, "optimize").start();
    }

    // ---------- lifecycle ----------

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tick);
    }

    @Override protected void onVisibilityChanged(View v, int vis) {
        super.onVisibilityChanged(v, vis);
        if (vis == VISIBLE && isAttachedToWindow() && ram != null)
            post(() -> update());
    }
}
