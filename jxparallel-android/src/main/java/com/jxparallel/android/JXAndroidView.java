package com.jxparallel.android;

import java.util.function.Supplier;

import android.content.Context;
import android.graphics.Canvas;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXFrame;
import com.jxparallel.ui.native2d.JXKeyEvent;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXPointerEvent;

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
 * <li>A touch is a primary press at its position (as a mouse click: buttons act on the press); moving with the
 * finger down drags the pressed node (scroll bar thumbs, dividers, text selection); holding still for the long-press
 * time is a secondary press (context menus).</li>
 * <li>The soft keyboard opens when a text field has the focus and closes when another node takes it; typed text,
 * backspace, enter and the arrow keys reach the field.</li>
 * <li>UI thread only, like any view; {@link #setContent(JXElement)} from another thread must go through
 * {@code post}.</li>
 * </ul>
 */
public class JXAndroidView extends View {
    private static final int PRIMARY = 0;
    private static final int SECONDARY = 1;

    private final AndroidCanvasPainter painter = new AndroidCanvasPainter();
    private final float density;
    private final int touchSlop;
    private JXNativeNode root;
    private JXNativeNode focused;
    private JXNativeNode pressed;
    /** The text node the soft keyboard was opened for. */
    private JXNativeNode keyboardFor;
    private Supplier<JXElement> source;
    private float downX;
    private float downY;
    private boolean longPressed;
    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (pressed != null && root != null) {
                longPressed = true;
                pressed = null;
                focused = nodeOrKeep(JXFrame.pointer(root, new JXPointerEvent(
                        JXAndroidMath.logical(downX, density), JXAndroidMath.logical(downY, density), SECONDARY)));
                changed();
            }
        }
    };

    public JXAndroidView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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
            focused = null;
            pressed = null;
        }
    }

    private void changed() {
        refresh();
        updateKeyboard();
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (root == null) {
            return false;
        }
        int x = JXAndroidMath.logical(event.getX(), density);
        int y = JXAndroidMath.logical(event.getY(), density);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                requestFocus();
                downX = event.getX();
                downY = event.getY();
                longPressed = false;
                JXNativeNode hit = JXFrame.pointer(root, new JXPointerEvent(x, y, PRIMARY));
                if (hit != null) {
                    focused = hit;
                }
                pressed = hit;
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                changed();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > touchSlop || Math.abs(event.getY() - downY) > touchSlop) {
                    removeCallbacks(longPress);
                }
                if (pressed != null) {
                    JXFrame.drag(pressed, new JXPointerEvent(x, y, PRIMARY));
                    changed();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                pressed = null;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (root == null) {
            return super.onKeyDown(keyCode, event);
        }
        int key = JXAndroidMath.nodeKey(keyCode);
        if (key >= 0) {
            JXFrame.key(root, new JXKeyEvent(key, '\0'), focused);
            changed();
            return true;
        }
        int unicode = event.getUnicodeChar();
        if (unicode > 0 && focused != null) {
            JXFrame.character(focused, (char) unicode);
            changed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Whether something with the focus edits text, so the keyboard belongs on screen. */
    private boolean editing() {
        return focused != null && JXAndroidMath.editsText(focused.getType());
    }

    private void updateKeyboard() {
        InputMethodManager manager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        JXNativeNode want = editing() ? focused : null;
        if (manager == null || want == keyboardFor) {
            return;
        }
        keyboardFor = want;
        if (want != null) {
            manager.restartInput(this);
            manager.showSoftInput(this, 0);
        } else {
            manager.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return editing();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD; // no composing text: the field wants plain characters
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (focused != null) {
                    for (int i = 0; i < text.length(); i++) {
                        JXFrame.character(focused, text.charAt(i));
                    }
                    changed();
                }
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int before, int after) {
                for (int i = 0; i < before; i++) {
                    JXFrame.key(root, new JXKeyEvent(JXAndroidMath.KEY_BACKSPACE, '\0'), focused);
                }
                for (int i = 0; i < after; i++) {
                    JXFrame.key(root, new JXKeyEvent(JXAndroidMath.KEY_DELETE, '\0'), focused);
                }
                changed();
                return true;
            }

            @Override
            public boolean performEditorAction(int actionCode) {
                JXFrame.key(root, new JXKeyEvent(JXAndroidMath.KEY_ENTER, '\0'), focused);
                changed();
                return true;
            }
        };
    }
}
