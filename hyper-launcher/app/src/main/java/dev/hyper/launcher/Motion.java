package dev.hyper.launcher;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

final class Motion {

    static final long FOCUS_MS = 320;
    static final long UNFOCUS_MS = 220;
    static final long GLIDE_MS = 380;
    static final long HERO_MS = 420;
    static final float FOCUS_SCALE = 1.1f;

    static final Interpolator FOCUS_IN = new OvershootInterpolator(0.9f);
    static final Interpolator FOCUS_OUT = new DecelerateInterpolator(1.6f);
    static final Interpolator EASE_OUT_EXPO = new PathInterpolator(0.16f, 1f, 0.3f, 1f);

    private Motion() {}
}
