package dev.hyper.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.GridView;

/** GridView that lets the drawer intercept OK/Menu before the default item click. */
public final class TvGridView extends GridView {

    public interface KeyHandler {
        boolean onGridKey(KeyEvent event);
    }

    private KeyHandler handler;

    public TvGridView(Context context) {
        super(context);
    }

    public TvGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TvGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setKeyHandler(KeyHandler handler) {
        this.handler = handler;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (handler != null && handler.onGridKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
