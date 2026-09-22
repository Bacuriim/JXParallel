package com.jxparallel.ui.native2d;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jxparallel.ui.JXElement;

public final class JXWindow {
    private final Frame frame;
    private final Canvas canvas;
    private JXNativeNode root;
    private JXNativeNode focusedNode;
    private Runnable onFirstPaint;
    private boolean firstPaintReported;
    private final AtomicBoolean repaintPending = new AtomicBoolean(false);

    public JXWindow(String title) {
        frame = new Frame(title == null ? "JXParallel" : title);
        canvas = new Canvas() {
            @Override
            public void paint(Graphics graphics) {
                render(graphics);
                if (!firstPaintReported) {
                    firstPaintReported = true;
                    Runnable callback = onFirstPaint;
                    if (callback != null) {
                        callback.run();
                    }
                }
            }
        };
        canvas.setBackground(java.awt.Color.WHITE);
        canvas.setFocusable(true);
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                canvas.requestFocus();
                if (root != null) {
                    JXNativeNode hit = root.hitTest(event.getX(), event.getY());
                    if (hit != null) {
                        focusedNode = hit;
                        hit.dispatchPointer(new JXPointerEvent(event.getX(), event.getY(), event.getButton()));
                    }
                }
            }
        });
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (root != null) {
                    if (focusedNode != null) {
                        focusedNode.dispatchKey(new JXKeyEvent(event.getKeyCode(), event.getKeyChar()));
                    }
                }
            }
        });
        frame.add(canvas);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                dispose();
            }
        });
    }

    public void setContent(JXElement element) {
        root = JXSkiaRenderer.mount(element);
        DimensionHelper.applyPreferredSize(frame, JXSkiaRenderer.preferredSize(root));
        renderNow();
    }

    public void show() {
        frame.setVisible(true);
        canvas.requestFocus();
        renderNow();
    }

    public void setOnFirstPaint(Runnable callback) {
        onFirstPaint = callback;
    }

    public void invokeLater(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }
        EventQueue.invokeLater(action);
    }

    public void renderNow() {
        if (root != null) {
            JXSkiaRenderer.layout(root, Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()));
            requestRepaint();
        }
    }

    private void requestRepaint() {
        if (repaintPending.compareAndSet(false, true)) {
            canvas.repaint();
            repaintPending.set(false);
        }
    }

    public void dispose() {
        frame.dispose();
    }

    private void render(Graphics graphics) {
        if (root == null) {
            return;
        }
        Image image = JXSkiaRenderer.render(root, canvas.getWidth(), canvas.getHeight());
        graphics.drawImage(image, 0, 0, null);
    }

    private static final class DimensionHelper {
        private DimensionHelper() {
        }

        static void applyPreferredSize(Frame frame, java.awt.Dimension size) {
            frame.setSize(Math.max(320, size.width + 32), Math.max(200, size.height + 64));
        }
    }
}
