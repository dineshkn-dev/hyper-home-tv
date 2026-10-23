package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny dependency-free image loader for Watch Next poster art. */
final class BitmapLoader {

    interface Callback {
        void onBitmap(Bitmap bitmap);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 8)) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private BitmapLoader() {}

    static void load(Context ctx, String uri, int reqW, int reqH, Callback cb) {
        if (uri == null || uri.isEmpty()) {
            cb.onBitmap(null);
            return;
        }
        Bitmap cached = CACHE.get(uri);
        if (cached != null) {
            cb.onBitmap(cached);
            return;
        }
        final Context app = ctx.getApplicationContext();
        final int w = Math.max(1, reqW);
        final int h = Math.max(1, reqH);
        POOL.execute(() -> {
            Bitmap bitmap = decode(app, uri, w, h);
            if (bitmap != null) CACHE.put(uri, bitmap);
            final Bitmap result = bitmap;
            MAIN.post(() -> cb.onBitmap(result));
        });
    }

    private static Bitmap decode(Context ctx, String uri, int reqW, int reqH) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = open(ctx, uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= reqW
                    && bounds.outHeight / (sample * 2) >= reqH)
                sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = open(ctx, uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        }
    }

    private static InputStream open(Context ctx, String uri) throws Exception {
        Uri u = Uri.parse(uri);
        String scheme = u.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            return c.getInputStream();
        }
        return ctx.getContentResolver().openInputStream(u);
    }
}
