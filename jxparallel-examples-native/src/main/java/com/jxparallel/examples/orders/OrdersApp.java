package com.jxparallel.examples.orders;

import java.time.LocalDate;

import com.jxparallel.core.JXParallel;
import com.jxparallel.ui.native2d.JXWindow;

/**
 * JX Orders: a small order-management desktop app built from compiled JXML screens. Run it with
 * {@code java -cp ... com.jxparallel.examples.orders.OrdersApp} (see {@code docs/example-orders.md}).
 *
 * <p>Options: {@code --screen=orders|customers|dashboard}, {@code --orders=10000} (how many to generate),
 * {@code --theme=light|dark}, {@code --select=<order id>} (select that order once loaded) and
 * {@code --edit=<order id>} (open it in the editor), which exist so that a screen can be shown without input.
 */
public final class OrdersApp {
    private OrdersApp() {
    }

    /** The command line, read. */
    static final class Options {
        ShellController.Screen screen = ShellController.Screen.ORDERS;
        int orders = 10_000;
        String theme = Palette.LIGHT;
        int select;
        int edit;

        static Options parse(String[] args) {
            Options o = new Options();
            for (String a : args) {
                if (a.startsWith("--screen=")) {
                    o.screen = ShellController.Screen.valueOf(a.substring(9).trim().toUpperCase());
                } else if (a.startsWith("--orders=")) {
                    o.orders = Integer.parseInt(a.substring(9));
                } else if (a.startsWith("--theme=")) {
                    o.theme = a.substring(8);
                } else if (a.startsWith("--select=")) {
                    o.select = Integer.parseInt(a.substring(9));
                } else if (a.startsWith("--edit=")) {
                    o.edit = Integer.parseInt(a.substring(7));
                } else {
                    throw new IllegalArgumentException("unknown option " + a
                            + " (--screen=orders|customers|dashboard --orders=N --theme=light|dark --select=ID --edit=ID)");
                }
            }
            return o;
        }
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        JXWindow window = new JXWindow("JX Orders");
        WindowHost host = new WindowHost(window);
        Session session = new Session(host, LocalDate::now);
        ShellController shell = new ShellController(session);
        host.setScreen(shell::view);

        session.setTheme(options.theme);
        shell.start(options.screen);
        window.setIconFiles("classpath:orders/app-icon-32.png", "classpath:orders/app-icon-64.png",
                "classpath:orders/app-icon-128.png");
        window.setSize(1220, 780);
        window.setMinSize(900, 560);
        window.setContent(shell.view());
        window.setOnFirstPaint(() -> {
            host.markReady();
            shell.loadSample(options.orders).thenRun(() -> host.runOnUi(() -> {
                if (options.select > 0) {
                    shell.orders().select(options.select);
                    host.requestRender();
                }
                if (options.edit > 0) {
                    Order o = session.store().find(options.edit);
                    if (o != null) {
                        shell.editor().openFor(o);
                    }
                }
            }));
        });
        window.show(); // returns when the window is closed
        JXParallel.shutdownNow();
    }
}
