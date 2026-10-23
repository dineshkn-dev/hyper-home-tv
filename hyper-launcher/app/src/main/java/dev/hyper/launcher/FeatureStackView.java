package dev.hyper.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Featured Tonight deck. The focused card is the front of a small fanned
 * stack; advancing animates the next card forward with a Laminar-style
 * ease-out rather than swapping a single hero view.
 */
public final class FeatureStackView extends FrameLayout {

    private static final long MOVE_MS = 440;
    private final Interpolator curve =
            new PathInterpolator(0.16f, 1f, 0.3f, 1f);

    private final List<FeatureCardView> cards = new ArrayList<>();
    private final List<FeatureItem> items = new ArrayList<>();
    private final View backFar, backNear;
    private int current;

    public FeatureStackView(Context ctx) {
        super(ctx);
        backFar = deckLayer(ctx, 0xFF050505);
        backNear = deckLayer(ctx, 0xFF0B0B0B);
        init();
    }

    public FeatureStackView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        backFar = deckLayer(ctx, 0xFF050505);
        backNear = deckLayer(ctx, 0xFF0B0B0B);
        init();
    }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setClickable(true);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundResource(R.drawable.bg_hero);

        GradientDrawable far = new GradientDrawable();
        far.setColor(0xFF050505);
        far.setCornerRadius(16 * d);
        far.setStroke((int) (1 * d), 0x33242424);
        backFar.setBackground(far);

        GradientDrawable near = new GradientDrawable();
        near.setColor(0xFF0B0B0B);
        near.setCornerRadius(16 * d);
        near.setStroke((int) (1 * d), 0x33242424);
        backNear.setBackground(near);

        addView(backFar, match());
        addView(backNear, match());

        setOnFocusChangeListener((v, has) -> {
            if (!cards.isEmpty()) {
                View front = cards.get(current);
                front.animate().cancel();
                front.animate().scaleX(has ? 1.02f : 1f)
                        .scaleY(has ? 1.02f : 1f)
                        .setDuration(220)
                        .setInterpolator(curve)
                        .start();
            }
        });
        setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                return performClick();
            }
            return false;
        });
    }

    private LayoutParams match() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static View deckLayer(Context ctx, int color) {
        View v = new View(ctx);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setClickable(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(16 * ctx.getResources().getDisplayMetrics().density);
        v.setBackground(g);
        return v;
    }

    public void setItems(List<FeatureItem> newItems, int startIndex) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        removeAllViews();
        cards.clear();

        addView(backFar, match());
        addView(backNear, match());

        for (FeatureItem item : items) {
            FeatureCardView card = new FeatureCardView(getContext());
            card.bind(item);
            cards.add(card);
            addView(card, match());
        }

        current = items.isEmpty() ? 0
                : Math.max(0, Math.min(startIndex, items.size() - 1));
        layoutCards(false);
        if (!items.isEmpty()) setContentDescription(items.get(current).title);
        else setContentDescription(null);
        animateCurrentProgress();
    }

    public FeatureItem getCurrent() {
        return current >= 0 && current < items.size()
                ? items.get(current) : null;
    }

    public boolean goForward() {
        if (current + 1 >= cards.size()) return false;
        current++;
        layoutCards(true);
        updateDescription();
        animateCurrentProgress();
        return true;
    }

    public boolean goBack() {
        if (current <= 0) return false;
        current--;
        layoutCards(true);
        updateDescription();
        animateCurrentProgress();
        return true;
    }

    private void animateCurrentProgress() {
        if (current < 0 || current >= cards.size()) return;
        FeatureCardView card = cards.get(current);
        card.post(card::playProgress);
    }

    private void updateDescription() {
        if (current < items.size())
            setContentDescription(items.get(current).title);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutCards(false);
    }

    private void layoutCards(boolean animate) {
        float d = getResources().getDisplayMetrics().density;
        float deckWidth = getWidth();
        if (deckWidth <= 0)
            deckWidth = getResources().getDisplayMetrics().widthPixels * 0.4f;

        // The two under-layers sit to the right of the front card, giving the
        // deck a Hotstar-style edge stack rather than a top-left fan.
        backNear.animate().cancel();
        backFar.animate().cancel();
        float nearScale = 0.94f;
        float farScale = 0.90f;
        backNear.setScaleX(nearScale);
        backNear.setScaleY(nearScale);
        backNear.setRotation(0.8f);
        backNear.setTranslationX((1 - nearScale) * deckWidth / 2f + 28 * d);
        backNear.setTranslationY(4 * d);
        backNear.setAlpha(0.55f);
        backNear.setTranslationZ(1 * d);
        backFar.setScaleX(farScale);
        backFar.setScaleY(farScale);
        backFar.setRotation(1.4f);
        backFar.setTranslationX((1 - farScale) * deckWidth / 2f + 42 * d);
        backFar.setTranslationY(8 * d);
        backFar.setAlpha(0.34f);
        backFar.setTranslationZ(0f);

        for (int i = 0; i < cards.size(); i++) {
            FeatureCardView card = cards.get(i);
            int pos = i - current;
            applyCard(card, pos, deckWidth, d, animate);
        }
    }

    private void applyCard(FeatureCardView card, int pos, float deckWidth,
                           float d, boolean animate) {
        float scale, rotation, tx, ty, alpha;
        float z;

        if (pos == 0) {
            scale = 1f;
            rotation = 0f;
            tx = 0f;
            ty = 0f;
            alpha = 1f;
            z = 12 * d;
        } else if (pos == 1) {
            scale = 0.965f;
            rotation = 0.7f;
            tx = (1 - scale) * deckWidth / 2f + 18 * d;
            ty = 4 * d;
            alpha = 0.97f;
            z = 6 * d;
        } else if (pos == 2) {
            scale = 0.93f;
            rotation = 1.4f;
            tx = (1 - scale) * deckWidth / 2f + 34 * d;
            ty = 8 * d;
            alpha = 0.88f;
            z = 2 * d;
        } else if (pos < 0) {
            scale = 0.94f;
            rotation = -2.8f;
            tx = -deckWidth * 0.58f;
            ty = -4 * d;
            alpha = 0f;
            z = 0f;
        } else {
            scale = 0.90f;
            rotation = 1.4f;
            tx = (1 - scale) * deckWidth / 2f + 48 * d;
            ty = 10 * d;
            alpha = 0f;
            z = 0f;
        }

        if (pos == 0 && isFocused()) {
            scale *= 1.02f;
        }

        card.setTranslationZ(z);
        if (animate) {
            card.animate().cancel();
            card.animate()
                    .scaleX(scale).scaleY(scale)
                    .rotation(rotation)
                    .translationX(tx).translationY(ty)
                    .alpha(alpha)
                    .translationZ(z)
                    .setDuration(MOVE_MS)
                    .setInterpolator(curve)
                    .withLayer()
                    .start();
        } else {
            card.setScaleX(scale);
            card.setScaleY(scale);
            card.setRotation(rotation);
            card.setTranslationX(tx);
            card.setTranslationY(ty);
            card.setAlpha(alpha);
        }
    }
}
