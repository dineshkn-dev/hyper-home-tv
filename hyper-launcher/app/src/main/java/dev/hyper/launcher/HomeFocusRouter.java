package dev.hyper.launcher;

import android.view.KeyEvent;
import android.view.View;

/** Explicit D-pad routing for the home deck and apps row. */
final class HomeFocusRouter {

    interface Host {
        View brand();
        View hero();
        View manage();
        View topAction(int index);
        int topActionCount();
        int topActionIndex(View v);
        View pinnedTile(int index);
        int pinnedIndex(View v);
        boolean focus(View v);
        void focusFirstApp();
        boolean deckForward();
        boolean deckBack();
    }

    private final Host host;

    HomeFocusRouter(Host host) {
        this.host = host;
    }

    boolean handle(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_LEFT
                && code != KeyEvent.KEYCODE_DPAD_RIGHT
                && code != KeyEvent.KEYCODE_DPAD_UP
                && code != KeyEvent.KEYCODE_DPAD_DOWN) return false;

        View focus = host.brand() != null ? currentFocus() : null;
        if (focus == null) return false;

        if (focus == host.brand()) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) host.focus(host.topAction(0));
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) host.focus(host.hero());
            return true;
        }

        int top = host.topActionIndex(focus);
        if (top >= 0) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (top > 0) host.focus(host.topAction(top - 1));
                else host.focus(host.brand());
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (top < host.topActionCount() - 1)
                    host.focus(host.topAction(top + 1));
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                host.focus(host.hero());
            }
            return true;
        }

        int pinned = host.pinnedIndex(focus);
        if (pinned >= 0) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (pinned > 0) host.focus(host.pinnedTile(pinned - 1));
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                host.focus(host.pinnedTile(pinned + 1));
            } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                host.focus(host.manage());
            }
            return true;
        }

        if (focus == host.manage()) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) host.focusFirstApp();
            else if (code == KeyEvent.KEYCODE_DPAD_UP) host.focus(host.hero());
            return true;
        }

        if (focus == host.hero()) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                host.deckBack();
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                host.deckForward();
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                host.focusFirstApp();
            } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                host.focus(host.topAction(0));
            }
            return true;
        }
        return false;
    }

    private View currentFocus() {
        return host.brand() != null && host.brand().getRootView() != null
                ? host.brand().getRootView().findFocus() : null;
    }
}
