package dev.hyper.launcher;

/** App metadata used by Manage Apps. */
final class ManagedApp {

    final AppEntry app;
    final long size;
    final String category;
    final boolean hidden;
    final int uses;

    ManagedApp(AppEntry app, long size, String category, boolean hidden, int uses) {
        this.app = app;
        this.size = size;
        this.category = category;
        this.hidden = hidden;
        this.uses = uses;
    }

    static String fmtSize(long b) {
        if (b >= 1_000_000_000L)
            return String.format(java.util.Locale.US, "%.1f GB", b / 1e9);
        if (b <= 0) return "—";
        return Math.max(1, b / 1_000_000L) + " MB";
    }
}
