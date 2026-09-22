package com.jxparallel.ui.native2d;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
    private final AtomicBoolean repaintPending = new AtomicBoolean(false);

    public JXWindow(String title) {
        frame = new Frame(title == null ? "JXParallel" : title);
        canvas = new Canvas() {
            @Override
            public void paint(Graphics graphics) {
                render((Graphics2D) graphics);
            }
        };
        canvas.setBackground(Color.WHITE);
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
        root = JX2DRenderer.mount(element);
        DimensionHelper.applyPreferredSize(frame, JX2DRenderer.preferredSize(root));
        renderNow();
    }

    public void show() {
        frame.setVisible(true);
        canvas.requestFocus();
        renderNow();
    }

    public void renderNow() {
        if (root != null) {
            JX2DRenderer.layout(root, Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()));
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

    private void render(Graphics2D graphics) {
        if (root == null) {
            return;
        }
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        JX2DRenderer.layout(root, canvas.getWidth(), canvas.getHeight());
        JX2DRenderer.paint(root, graphics);
    }

    private static final class DimensionHelper {
        private DimensionHelper() {
        }

        static void applyPreferredSize(Frame frame, java.awt.Dimension size) {
            frame.setSize(Math.max(320, size.width + 32), Math.max(200, size.height + 64));
        }
    }
}
