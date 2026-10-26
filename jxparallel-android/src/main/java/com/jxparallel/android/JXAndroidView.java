package com.jxparallel.android;

import java.util.function.Supplier;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.input.JXEditBuffer;
import com.jxparallel.ui.input.JXTouchScroller;
import com.jxparallel.ui.native2d.JXFrame;
import com.jxparallel.ui.native2d.JXKeyEvent;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXPointerEvent;
import com.jxparallel.ui.native2d.JXTextInput;

/**
 * Shows a JXParallel element tree: lays it out to the size of the view in dp (the density of the display is the
 * scale), paints it with {@link AndroidCanvasPainter} in {@code onDraw}, and feeds touches and keys back to it.
 *
 * <pre>{@code
 * JXAndroidView view = new JXAndroidView(context);
 * view.setContent(() -> screen.build(controller));   // rebuilt after every touch or key
 * setContentView(view);
 * }</pre>
 *
 * <ul>
 * <li>A touch is a primary press at its position (as a mouse click: buttons act on the press). Inside a scroll pane, list
 * or table that can scroll, the press waits for the lift: a drag past the touch slop scrolls it (and a quick lift goes on
 * as a fling) and never clicks, a tap clicks. Elsewhere moving with the finger down drags the pressed node (scroll bar
 * thumbs, sliders, dividers). Holding still for the long-press time selects the word in a text field and shows
 * Cut, Copy, Paste and Select all; elsewhere it is a secondary press (context menus).</li>
 * <li>The soft keyboard opens when a text field has the focus and closes when another node takes it. It edits through an
 * {@code InputConnection} with composing text (suggestions, accents, swipe typing): each operation changes the field once.
 * Hardware keys type, delete and move the caret; Ctrl+A, C, X and V select all, copy, cut and paste.</li>
 * <li>UI thread only, like any view; {@link #setContent(JXElement)} from another thread must go through
 * {@code post}.</li>
 * </ul>
 */
public class JXAndroidView extends View {
    private static final int PRIMARY = 0;
    private static final int SECONDARY = 1;
    private static final int MENU_CUT = 1;
    private static final int MENU_COPY = 2;
    private static final int MENU_PASTE = 3;
    private static final int MENU_SELECT_ALL = 4;

    private final AndroidCanvasPainter painter = new AndroidCanvasPainter();
    private final AndroidClipboard clipboard;
    private final float density;
    private final JXTouchScroller scroller;
    private final JXEditBuffer buffer = new JXEditBuffer("", 0, 0);
    private JXNativeNode root;
    private JXNativeNode focused;
    private JXNativeNode pressed;
    /** The text node the soft keyboard was opened for. */
    private JXNativeNode keyboardFor;
    private Supplier<JXElement> source;
    private ActionMode actionMode;
    private float downX;
    private float downY;
    /** The touch started in something that scrolls: the press waits for the lift. */
    private boolean deferred;
    /** The touch is not a tap: it stopped a fling or became a long press. */
    private boolean swallowed;
    private boolean longPressed;
    private int batchDepth;
    private boolean batchDirty;
    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            longPress();
        }
    };
    private final Runnable fling = new Runnable() {
        @Override
        public void run() {
            double[] delta = scroller.step(SystemClock.uptimeMillis());
            boolean moved = root != null
                    && JXFrame.scroll(root, JXAndroidMath.logical(downX, density), JXAndroidMath.logical(downY, density),
                            delta[0] / density, delta[1] / density);
            if (moved) {
                invalidate();
            }
            if (moved && scroller.isFlinging()) {
                postOnAnimation(this);
            } else {
                scroller.cancel();
            }
        }
    };

    public JXAndroidView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        ViewConfiguration configuration = ViewConfiguration.get(context);
        scroller = new JXTouchScroller(configuration.getScaledTouchSlop(), configuration.getScaledMinimumFlingVelocity(),
                configuration.getScaledMaximumFlingVelocity());
        clipboard = new AndroidClipboard(context);
        JXAndroidText.install(density);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    /** Shows {@code element}; a tree of the same root type is reconciled in place (focus and field state are kept). */
    public void setContent(JXElement element) {
        source = null;
        mount(element);
        invalidate();
    }

    /**
     * Shows what {@code builder} returns and asks it again after every touch and key, so state the handlers changed
     * shows at once. Call {@link #refresh} when the state changes by other means (a timer, a network answer).
     */
    public void setContent(Supplier<JXElement> builder) {
        source = builder;
        mount(builder.get());
        invalidate();
    }

    /** Builds the content again (see {@link #setContent(Supplier)}) and repaints. */
    public void refresh() {
        if (source != null) {
            mount(source.get());
        }
        invalidate();
    }

    /** The mounted tree, or null. */
    public JXNativeNode getRoot() {
        return root;
    }

    private void mount(JXElement element) {
        if (root == null || !root.reconcile(element)) {
            root = JXFrame.mount(element);
            setFocused(null);
            pressed = null;
        }
    }

    private void setFocused(JXNativeNode next) {
        JXFrame.focus(focused, next);
        focused = next;
    }

    private void changed() {
        refresh();
        updateKeyboard();
        syncBuffer();
    }

    private JXNativeNode nodeOrKeep(JXNativeNode hit) {
        return hit != null ? hit : focused;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec),
                resolveSize(getSuggestedMinimumHeight(), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (root == null) {
            return;
        }
        int width = JXAndroidMath.logicalSize(getWidth(), density);
        int height = JXAndroidMath.logicalSize(getHeight(), density);
        canvas.save();
        canvas.scale(density, density);
        JXFrame.layout(root, width, height);
        painter.begin(canvas);
        JXFrame.paint(root, painter, width, height);
        canvas.restore();
        long wait = JXFrame.tick(root); // a snack bar with a duration closes itself
        if (wait >= 0) {
            postInvalidateDelayed(Math.max(wait, 16));
        }
    }

    // ---- touch --------------------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (root == null) {
            return false;
        }
        int x = JXAndroidMath.logical(event.getX(), density);
        int y = JXAndroidMath.logical(event.getY(), density);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                removeCallbacks(fling);
                requestFocus();
                downX = event.getX();
                downY = event.getY();
                longPressed = false;
                pressed = null;
                deferred = false;
                swallowed = scroller.down(event.getX(), event.getY(), event.getEventTime()); // stopping a fling is no click
                if (swallowed) {
                    return true;
                }
                if (JXFrame.scrollsAt(root, x, y)) {
                    deferred = true; // press on the lift, when it is known not to be a scroll
                } else {
                    press(x, y);
                }
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE: {
                double[] delta = scroller.move(event.getX(), event.getY(), event.getEventTime());
                if (deferred && scroller.isScrolling()) {
                    removeCallbacks(longPress);
                    if (delta != null && (delta[0] != 0 || delta[1] != 0)
                            && JXFrame.scroll(root, JXAndroidMath.logical(downX, density), JXAndroidMath.logical(downY, density),
                                    delta[0] / density, delta[1] / density)) {
                        invalidate();
                    }
                    return true;
                }
                if (Math.abs(event.getX() - downX) > scroller.slop() || Math.abs(event.getY() - downY) > scroller.slop()) {
                    removeCallbacks(longPress);
                }
                if (pressed != null) {
                    JXFrame.drag(pressed, new JXPointerEvent(x, y, PRIMARY));
                    changed();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (deferred) { // the lift is a move too (a fast swipe can come as a press and a lift far apart)
                    double[] delta = scroller.move(event.getX(), event.getY(), event.getEventTime());
                    if (delta != null && (delta[0] != 0 || delta[1] != 0)
                            && JXFrame.scroll(root, JXAndroidMath.logical(downX, density), JXAndroidMath.logical(downY, density),
                                    delta[0] / density, delta[1] / density)) {
                        invalidate();
                    }
                }
                if (deferred && scroller.isTap() && !swallowed && !longPressed) {
                    press(x, y);
                }
                if (scroller.up(event.getEventTime())) {
                    postOnAnimation(fling);
                }
                pressed = null;
                deferred = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                scroller.cancel();
                pressed = null;
                deferred = false;
                return true;
            default:
                return true;
        }
    }

    /** A primary press at (x, y) logical pixels: the hit node takes the focus (a text field puts the caret there). */
    private void press(int x, int y) {
        finishActionMode();
        JXNativeNode hit = JXFrame.pointer(root, new JXPointerEvent(x, y, PRIMARY));
        if (hit != null) {
            setFocused(hit);
        }
        pressed = hit;
        changed();
    }

    private void longPress() {
        if (root == null || scroller.isScrolling()) {
            return;
        }
        longPressed = true;
        swallowed = true;
        deferred = false;
        int x = JXAndroidMath.logical(downX, density);
        int y = JXAndroidMath.logical(downY, density);
        JXNativeNode under = root.hitTest(x, y);
        if (JXTextInput.isEditable(under)) {
            if (under != focused) {
                press(x, y);
            }
            pressed = null;
            JXTextInput.selectWordAt(under, JXTextInput.caret(under));
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            changed();
            startTextActions();
            return;
        }
        pressed = null;
        setFocused(nodeOrKeep(JXFrame.pointer(root, new JXPointerEvent(x, y, SECONDARY))));
        changed();
    }

    // ---- selection menu and clipboard ----------------------------------------------------------

    private void startTextActions() {
        if (actionMode != null) {
            actionMode.invalidate();
            return;
        }
        actionMode = startActionMode(new ActionMode.Callback2() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0, MENU_CUT, 0, "Cut");
                menu.add(0, MENU_COPY, 1, "Copy");
                menu.add(0, MENU_PASTE, 2, "Paste");
                menu.add(0, MENU_SELECT_ALL, 3, "Select all");
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                boolean selection = editing() && JXTextInput.hasSelection(focused) && !JXTextInput.isPassword(focused);
                menu.findItem(MENU_CUT).setVisible(selection);
                menu.findItem(MENU_COPY).setVisible(selection);
                menu.findItem(MENU_PASTE).setVisible(clipboard.hasText());
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                boolean keepOpen = item.getItemId() == MENU_SELECT_ALL;
                textAction(item.getItemId());
                if (keepOpen) {
                    mode.invalidate();
                } else {
                    mode.finish();
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                actionMode = null;
            }

            @Override
            public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                if (focused == null) {
                    return;
                }
                outRect.set(Math.round(focused.getX() * density), Math.round(focused.getY() * density),
                        Math.round((focused.getX() + focused.getWidth()) * density),
                        Math.round((focused.getY() + focused.getHeight()) * density));
            }
        }, ActionMode.TYPE_FLOATING);
    }

    private void finishActionMode() {
        if (actionMode != null) {
            actionMode.finish();
        }
    }

    /** Cut, copy, paste or select all in the focused text field. */
    private boolean textAction(int id) {
        if (!editing()) {
            return false;
        }
        switch (id) {
            case MENU_CUT:
            case android.R.id.cut:
                JXTextInput.cut(focused, clipboard);
                break;
            case MENU_COPY:
            case android.R.id.copy:
                JXTextInput.copy(focused, clipboard);
                break;
            case MENU_PASTE:
            case android.R.id.paste:
                JXTextInput.paste(focused, clipboard);
                break;
            case MENU_SELECT_ALL:
            case android.R.id.selectAll:
                JXTextInput.selectAll(focused);
                break;
            default:
                return false;
        }
        changed();
        return true;
    }

    // ---- keys ---------------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (root == null) {
            return super.onKeyDown(keyCode, event);
        }
        if (event.isCtrlPressed() && editing()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    return textAction(android.R.id.selectAll);
                case KeyEvent.KEYCODE_C:
                    return textAction(android.R.id.copy);
                case KeyEvent.KEYCODE_X:
                    return textAction(android.R.id.cut);
                case KeyEvent.KEYCODE_V:
                    return textAction(android.R.id.paste);
                default:
                    break;
            }
        }
        int key = JXAndroidMath.nodeKey(keyCode);
        if (key >= 0) {
            JXFrame.key(root, new JXKeyEvent(key, '\0'), focused);
            changed();
            return true;
        }
        int unicode = event.getUnicodeChar();
        if (unicode > 0 && focused != null && !event.isCtrlPressed()) {
            JXFrame.character(focused, (char) unicode);
            changed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---- soft keyboard ------------------------------------------------------------------------

    /** Whether something with the focus edits text, so the keyboard belongs on screen. */
    private boolean editing() {
        return focused != null && JXAndroidMath.editsText(focused.getType());
    }

    private InputMethodManager inputMethods() {
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    private void updateKeyboard() {
        InputMethodManager manager = inputMethods();
        JXNativeNode want = editing() ? focused : null;
        if (want == null) {
            finishActionMode();
        }
        if (manager == null || want == keyboardFor) {
            return;
        }
        keyboardFor = want;
        if (want != null) {
            loadBuffer();
            manager.restartInput(this);
            manager.showSoftInput(this, 0);
        } else {
            manager.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    /** The buffer the keyboard edits starts as the field is now. */
    private void loadBuffer() {
        if (editing()) {
            buffer.reset(JXTextInput.text(focused), JXTextInput.anchor(focused), JXTextInput.caret(focused));
        }
    }

    /** The field changed without the keyboard (a key, a tap, the clipboard, the application): the buffer follows and the keyboard is told. */
    private void syncBuffer() {
        if (!editing()) {
            return;
        }
        String text = JXTextInput.text(focused);
        int anchor = JXTextInput.anchor(focused);
        int caret = JXTextInput.caret(focused);
        if (text.equals(buffer.getText()) && anchor == buffer.getAnchor() && caret == buffer.getCaret()) {
            return;
        }
        buffer.reset(text, anchor, caret); // a composition does not survive that
        tellKeyboard();
    }

    private void tellKeyboard() {
        InputMethodManager manager = inputMethods();
        if (manager != null) {
            manager.updateSelection(this, buffer.getSelectionStart(), buffer.getSelectionEnd(), buffer.getComposingStart(),
                    buffer.getComposingEnd());
        }
    }

    /** The keyboard changed the buffer: the field gets the new text in one change, the application builds the screen again. */
    private void commitBuffer() {
        if (!editing()) {
            return;
        }
        if (batchDepth > 0) {
            batchDirty = true;
            return;
        }
        batchDirty = false;
        JXTextInput.setText(focused, buffer.getText(), buffer.getAnchor(), buffer.getCaret());
        refresh();
        if (JXTextInput.text(focused).equals(buffer.getText())) {
            tellKeyboard();
        } else { // the application refused or changed the text: the field is the truth, the keyboard starts again from it
            loadBuffer();
            InputMethodManager manager = inputMethods();
            if (manager != null) {
                manager.restartInput(this);
            }
        }
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return editing();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (!editing()) {
            return null;
        }
        loadBuffer();
        batchDepth = 0;
        batchDirty = false;
        boolean multiline = JXTextInput.isMultiline(focused);
        int type = InputType.TYPE_CLASS_TEXT;
        if (JXTextInput.isPassword(focused)) {
            type |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
        } else if (multiline) {
            type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        }
        outAttrs.inputType = type;
        outAttrs.imeOptions = (multiline ? EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_ENTER_ACTION : EditorInfo.IME_ACTION_DONE)
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        outAttrs.initialSelStart = buffer.getAnchor();
        outAttrs.initialSelEnd = buffer.getCaret();
        if (Build.VERSION.SDK_INT >= 30) {
            outAttrs.setInitialSurroundingText(buffer.getText());
        }
        return new FieldConnection();
    }

    /** The keyboard's way into the focused field: every operation is one change of the buffer and one of the field. */
    private final class FieldConnection extends BaseInputConnection {
        FieldConnection() {
            super(JXAndroidView.this, false);
        }

        private void fresh() {
            if (editing() && !JXTextInput.text(focused).equals(buffer.getText())) {
                loadBuffer();
            }
        }

        @Override
        public boolean beginBatchEdit() {
            batchDepth++;
            return true;
        }

        @Override
        public boolean endBatchEdit() {
            if (batchDepth > 0) {
                batchDepth--;
            }
            if (batchDepth == 0 && batchDirty) {
                commitBuffer();
            }
            return batchDepth > 0;
        }

        @Override
        public CharSequence getTextBeforeCursor(int n, int flags) {
            fresh();
            return buffer.getTextBeforeCursor(n);
        }

        @Override
        public CharSequence getTextAfterCursor(int n, int flags) {
            fresh();
            return buffer.getTextAfterCursor(n);
        }

        @Override
        public CharSequence getSelectedText(int flags) {
            fresh();
            return buffer.hasSelection() ? buffer.getSelectedText() : null;
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            fresh();
            buffer.setComposingText(text, newCursorPosition);
            commitBuffer();
            return true;
        }

        @Override
        public boolean setComposingRegion(int start, int end) {
            fresh();
            buffer.setComposingRegion(start, end);
            tellKeyboard();
            return true;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            fresh();
            buffer.commitText(text, newCursorPosition);
            commitBuffer();
            return true;
        }

        @Override
        public boolean finishComposingText() {
            buffer.finishComposingText();
            tellKeyboard();
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int before, int after) {
            fresh();
            buffer.deleteSurroundingText(before, after);
            commitBuffer();
            return true;
        }

        @Override
        public boolean deleteSurroundingTextInCodePoints(int before, int after) {
            fresh();
            buffer.deleteSurroundingTextInCodePoints(before, after);
            commitBuffer();
            return true;
        }

        @Override
        public boolean setSelection(int start, int end) {
            fresh();
            buffer.setSelection(start, end);
            commitBuffer();
            return true;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return true;
            }
            fresh();
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DEL:
                    buffer.backspace();
                    break;
                case KeyEvent.KEYCODE_FORWARD_DEL:
                    buffer.deleteForward();
                    break;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (editing() && JXTextInput.isMultiline(focused)) {
                        buffer.commitText("\n", 1);
                        break;
                    }
                    return performEditorAction(EditorInfo.IME_ACTION_DONE);
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    move(buffer.hasSelection() ? buffer.getSelectionStart() : buffer.getCaret() - 1, event);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    move(buffer.hasSelection() ? buffer.getSelectionEnd() : buffer.getCaret() + 1, event);
                    break;
                case KeyEvent.KEYCODE_MOVE_HOME:
                    move(0, event);
                    break;
                case KeyEvent.KEYCODE_MOVE_END:
                    move(buffer.length(), event);
                    break;
                default:
                    int unicode = event.getUnicodeChar();
                    if (unicode <= 0) { // none, or a combining accent (the high bit)
                        return false;
                    }
                    buffer.commitText(new String(Character.toChars(unicode)), 1);
                    break;
            }
            commitBuffer();
            return true;
        }

        private void move(int position, KeyEvent event) {
            buffer.finishComposingText();
            buffer.setSelection(event.isShiftPressed() ? buffer.getAnchor() : position, position);
        }

        @Override
        public boolean performEditorAction(int actionCode) {
            if (root == null) {
                return false;
            }
            buffer.finishComposingText();
            JXFrame.key(root, new JXKeyEvent(JXAndroidMath.KEY_ENTER, '\0'), focused);
            changed();
            return true;
        }

        @Override
        public boolean performContextMenuAction(int id) {
            return textAction(id);
        }

        @Override
        public int getCursorCapsMode(int reqModes) {
            return 0;
        }
    }
}
