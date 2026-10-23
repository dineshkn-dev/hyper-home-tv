package dev.hyper.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongConsumer;

/** Kills cached background processes and reports the freed memory. */
final class MemoryOptimizer {

    private MemoryOptimizer() {}

    static void sweep(Context ctx, LongConsumer done) {
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            ActivityManager am =
                    (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
            try { am.getMemoryInfo(before); } catch (Exception ignored) { }

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

            if (before.availMem < before.totalMem * 3L / 4L) {
                ArrayList<java.nio.ByteBuffer> chunks = new ArrayList<>();
                try {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    for (int i = 0; i < 12; i++) {
                        am.getMemoryInfo(mi);
                        if (mi.availMem < 96L * 1024 * 1024) break;
                        java.nio.ByteBuffer b =
                                java.nio.ByteBuffer.allocateDirect(48 << 20);
                        for (int off = 0; off < b.capacity(); off += 4096)
                            b.put(off, (byte) 1);
                        chunks.add(b);
                        SystemClock.sleep(50);
                    }
                    SystemClock.sleep(1200);
                } catch (Throwable ignored) { }

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
            handler.post(() -> done.accept(freed));
        }, "optimize").start();
    }
}
