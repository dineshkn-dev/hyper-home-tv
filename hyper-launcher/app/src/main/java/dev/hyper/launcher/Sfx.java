package dev.hyper.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class Sfx {

    private static final String PREFS = "hyper";
    private static final String KEY_ENABLED = "sound_enabled";
    private static final int RATE = 22050;

    private final Context ctx;
    private final SoundPool pool;
    private volatile int tickId, clickId;

    Sfx(Context ctx) {
        this.ctx = ctx;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build();
        new Thread(this::prepare, "sfx").start();
    }

    private void prepare() {
        try {
            File dir = ctx.getCacheDir();
            File tick = new File(dir, "tick.wav");
            File click = new File(dir, "click.wav");
            write(tick, wav(tone(1800f, 40, 0.22f)));
            write(click, wav(tone(880f, 90, 0.30f)));
            tickId = pool.load(tick.getAbsolutePath(), 1);
            clickId = pool.load(click.getAbsolutePath(), 1);
        } catch (IOException ignored) {}
    }

    void tick() { play(tickId); }

    void click() { play(clickId); }

    boolean isEnabled() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
    }

    void setEnabled(boolean on) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    void release() {
        pool.release();
    }

    private void play(int id) {
        if (id != 0 && isEnabled()) pool.play(id, 1f, 1f, 1, 0, 1f);
    }

    private static short[] tone(float hz, int ms, float amp) {
        int n = RATE * ms / 1000;
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            double env = Math.pow(1.0 - (double) i / n, 2);
            s[i] = (short) (Math.sin(2 * Math.PI * hz * i / RATE) * env * amp * Short.MAX_VALUE);
        }
        return s;
    }

    private static byte[] wav(short[] pcm) {
        int dataBytes = pcm.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataBytes)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1)
                .putInt(RATE).putInt(RATE * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataBytes);
        for (short v : pcm) b.putShort(v);
        return b.array();
    }

    private static void write(File f, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }
}
