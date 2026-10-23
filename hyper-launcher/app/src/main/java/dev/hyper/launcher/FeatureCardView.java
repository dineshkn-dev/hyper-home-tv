package dev.hyper.launcher;

import android.content.Context;
import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** A single rendered card inside the Featured Tonight deck. */
final class FeatureCardView extends FrameLayout {

    private static final long PROGRESS_MS = 1100;
    private static final PathInterpolator PROGRESS_CURVE =
            new PathInterpolator(0.16f, 1f, 0.3f, 1f);

    private final TextView eyebrow, title, description, meta;
    private final ImageView art;
    private final View progressTrack, progressFill;
    private float progress;

    FeatureCardView(Context ctx) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;
        setBackgroundResource(R.drawable.bg_hero);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 16 * d);
            }
        });
        setFocusable(false);
        setClickable(false);

        LayoutInflater.from(ctx).inflate(R.layout.view_feature_card, this, true);
        eyebrow = findViewById(R.id.featureEyebrow);
        title = findViewById(R.id.featureTitle);
        description = findViewById(R.id.featureDescription);
        meta = findViewById(R.id.featureMeta);
        art = findViewById(R.id.featureArt);
        progressTrack = findViewById(R.id.featureProgressTrack);
        progressFill = findViewById(R.id.featureProgressFill);
        progressFill.setPivotX(0f);
    }

    void bind(FeatureItem item) {
        eyebrow.setText(item.eyebrow);
        title.setText(item.title);
        description.setText(item.description);
        meta.setText(item.meta);

        progress = Math.max(0f, Math.min(1f, item.progress));
        progressTrack.setVisibility(progress > 0.001f ? View.VISIBLE : View.GONE);
        progressFill.animate().cancel();
        progressFill.setPivotX(0f);
        progressFill.setScaleX(0f);

        art.setTag(item.imageUri);
        art.setPadding(0, 0, 0, 0);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageDrawable(null);

        boolean hasImage = item.imageUri != null && !item.imageUri.isEmpty();
        if (!hasImage) {
            applyAppFallback(item);
            return;
        }
        int w = Math.max(getWidth(),
                (int) (getResources().getDisplayMetrics().widthPixels * 0.36f));
        int h = Math.max(getHeight(),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.36f));
        BitmapLoader.load(getContext(), item.imageUri, w, h, bitmap -> {
            if (!item.imageUri.equals(art.getTag())) return;
            if (bitmap != null) {
                art.setPadding(0, 0, 0, 0);
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setImageBitmap(bitmap);
            } else {
                applyAppFallback(item);
            }
        });
    }

    void playProgress() {
        if (progress <= 0.001f) return;
        progressTrack.setVisibility(View.VISIBLE);
        progressFill.animate().cancel();
        progressFill.setPivotX(0f);
        progressFill.setScaleX(0f);
        progressFill.animate()
                .scaleX(progress)
                .setStartDelay(120)
                .setDuration(PROGRESS_MS)
                .setInterpolator(PROGRESS_CURVE)
                .start();
    }

    private void applyAppFallback(FeatureItem item) {
        art.setPadding(0, 0, 0, 0);
        if (item.appBanner != null) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(item.appBanner);
        } else if (item.appIcon != null) {
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = (int) (54 * getResources().getDisplayMetrics().density);
            art.setPadding(p, p, p, p);
            art.setImageDrawable(item.appIcon);
        } else {
            art.setImageDrawable(null);
        }
    }
}
