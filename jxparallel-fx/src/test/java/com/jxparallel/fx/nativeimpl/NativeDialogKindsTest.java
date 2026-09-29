package com.jxparallel.fx.nativeimpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.scene.control.Alert;
import com.jxparallel.fx.scene.control.ButtonType;
import com.jxparallel.fx.scene.control.Dialog;
import com.jxparallel.fx.scene.control.Label;
import com.jxparallel.fx.stage.FileChooser;
import com.jxparallel.ui.native2d.JXNativeNode;

import javafx.event.EventHandler;
import javafx.scene.control.Alert.AlertType;
import javafx.util.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alert kinds, headers, icons, dialog events, file chooser filters and notification windows. */
class NativeDialogKindsTest extends NativeTestSupport {

    private static NativeScene top() {
        return NativeRuntime.SCENES.get(NativeRuntime.SCENES.size() - 1);
    }

    /** Texts of the labels shown in a scene. */
    private static List<String> labels(NativeScene s) {
        s.render();
        List<String> out = new ArrayList<>();
        for (NativeModel m : s.nodes.keySet()) {
            if (m.is(javafx.scene.control.Label.class)) {
                out.add(NativeElements.string(m, "text"));
            }
        }
        return out;
    }

    private static int count(NativeScene s, Class<?> type) {
        s.render();
        int n = 0;
        for (NativeModel m : s.nodes.keySet()) {
            if (m.is(type)) {
                n++;
            }
        }
        return n;
    }

    private static String iconStyle(NativeScene s) {
        s.render();
        for (NativeModel m : s.nodes.keySet()) {
            String style = NativeElements.string(m, "style");
            if (style.contains("-fx-background-radius: 20")) {
                return style;
            }
        }
        return null;
    }

    @Test
    void alertKindsHaveTheirTitleHeaderIconAndButtons() throws Exception {
        Object[][] kinds = {
            {Alert.AlertType.INFORMATION, "Dialog.info", "#2d6cdf", 1},
            {Alert.AlertType.WARNING, "Dialog.warning", "#e8a318", 1},
            {Alert.AlertType.ERROR, "Dialog.error", "#d9342b", 1},
            {Alert.AlertType.CONFIRMATION, "Dialog.confirm", "#2d6cdf", 2},
        };
        for (Object[] kind : kinds) {
            Alert alert = fx(() -> new Alert((Alert.AlertType) kind[0], "texto"));
            int windows = fx(() -> NativeRuntime.SCENES.size());
            fx(() -> alert.show());
            NativeScene s = fx(NativeDialogKindsTest::top);
            String key = (String) kind[1];
            String title = NativeDialogs.resource(key + ".title");
            assertNotEquals(key + ".title", title, "JavaFX has the text");
            assertEquals(title, fx(() -> NativeElements.string(s.stage, "title")), key);
            assertTrue(fx(() -> labels(s)).contains(NativeDialogs.resource(key + ".header")), key);
            assertTrue(fx(() -> labels(s)).contains("texto"));
            assertTrue(fx(() -> iconStyle(s)).contains((String) kind[2]), key);
            assertEquals(kind[3], fx(() -> count(s, javafx.scene.control.Button.class)), key);
            assertTrue(fx(() -> alert.isShowing()));
            fx(() -> alert.show());
            assertEquals(windows + 1, (int) fx(() -> NativeRuntime.SCENES.size()), "showing again reuses the window");
            fx(() -> alert.close());
            assertFalse(fx(() -> alert.isShowing()));
            assertEquals(windows, (int) fx(() -> NativeRuntime.SCENES.size()));
        }
    }

    @Test
    void alertOfNoKindHasNoButtonsAndNoIcon() throws Exception {
        Alert alert = fx(() -> new Alert(Alert.AlertType.NONE));
        fx(() -> alert.show());
        NativeScene s = fx(NativeDialogKindsTest::top);
        assertEquals(0, (int) fx(() -> count(s, javafx.scene.control.Button.class)));
        assertNull(fx(() -> iconStyle(s)));
        fx(() -> alert.hide());
        assertFalse(fx(() -> alert.isShowing()));
    }

    @Test
    void headersComeFromTheDialogThenItsPane() throws Exception {
        Dialog<ButtonType> plain = fx(() -> {
            Dialog<ButtonType> d = new Dialog<>();
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            d.getDialogPane().setContentText("corpo");
            return d;
        });
        fx(() -> plain.show());
        NativeScene s = fx(NativeDialogKindsTest::top);
        assertEquals(0, (int) fx(() -> count(s, javafx.scene.control.Separator.class)), "a plain dialog has no header");
        assertEquals("", fx(() -> NativeElements.string(s.stage, "title")));
        fx(() -> plain.close());

        Dialog<ButtonType> paneHeader = fx(() -> {
            Dialog<ButtonType> d = new Dialog<>();
            d.getDialogPane().setHeaderText("Do painel");
            d.getDialogPane().setGraphic(new Label("G"));
            d.getDialogPane().getButtonTypes().add(ButtonType.OK);
            d.setTitle("Título");
            return d;
        });
        fx(() -> paneHeader.show());
        NativeScene p = fx(NativeDialogKindsTest::top);
        assertTrue(fx(() -> labels(p)).containsAll(Arrays.asList("Do painel", "G")));
        assertEquals(1, (int) fx(() -> count(p, javafx.scene.control.Separator.class)));
        assertEquals("Título", fx(() -> NativeElements.string(p.stage, "title")));
        fx(() -> paneHeader.close());

        Alert noHeader = fx(() -> {
            Alert a = new Alert(Alert.AlertType.WARNING, "sem cabeçalho");
            a.setHeaderText(null);
            return a;
        });
        fx(() -> noHeader.show());
        NativeScene n = fx(NativeDialogKindsTest::top);
        assertEquals(0, (int) fx(() -> count(n, javafx.scene.control.Separator.class)), "a null header removes the bar");
        assertNotNull(fx(() -> iconStyle(n)), "the icon moves next to the content");
        fx(() -> noHeader.close());
    }

    @Test
    void dialogEventsAndAThrowingResultConverter() throws Exception {
        List<String> events = new ArrayList<>();
        Dialog<String> dialog = fx(() -> {
            Dialog<String> d = new Dialog<>();
            d.getDialogPane().getButtonTypes().add(ButtonType.OK);
            d.setOnShowing(e -> events.add(e.getSource() == d ? "showing" : "wrong source " + e.getSource()));
            d.setOnShown(e -> events.add("shown"));
            d.setOnHiding(e -> events.add("hiding"));
            d.setOnHidden(e -> events.add("hidden"));
            d.setResultConverter(b -> {
                throw new IllegalStateException("converter failed");
            });
            return d;
        });
        Thread.UncaughtExceptionHandler handler = fx(() -> Thread.currentThread().getUncaughtExceptionHandler());
        List<Throwable> reported = new ArrayList<>();
        fx(() -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> reported.add(e)));
        try {
            fx(() -> dialog.show());
            NativeScene s = fx(NativeDialogKindsTest::top);
            JXNativeNode ok = fx(() -> {
                s.render();
                for (java.util.Map.Entry<NativeModel, JXNativeNode> e : s.nodes.entrySet()) {
                    if (e.getKey().is(javafx.scene.control.Button.class)) {
                        return e.getValue();
                    }
                }
                return null;
            });
            click(s, ok.getX() + 5, ok.getY() + 5);
            assertEquals(Arrays.asList("showing", "shown", "hiding", "hidden"), events);
            assertNull(fx(() -> dialog.getResult()), "a failing converter gives no result");
            assertEquals("converter failed", reported.get(0).getMessage(), "and the failure is reported");
        } finally {
            fx(() -> Thread.currentThread().setUncaughtExceptionHandler(handler));
        }
    }

    @Test
    void aSingleButtonIsWhatTheCloseButtonReturns() throws Exception {
        Dialog<ButtonType> dialog = fx(() -> {
            Dialog<ButtonType> d = new Dialog<>();
            d.getDialogPane().getButtonTypes().add(ButtonType.YES);
            return d;
        });
        fx(() -> dialog.show());
        NativeScene s = fx(NativeDialogKindsTest::top);
        fx(() -> NativeEvents.handle(s, com.jxparallel.ui.native2d.JXInputEvent.window(
                com.jxparallel.ui.native2d.JXInputEvent.Kind.CLOSE_REQUEST, 0, 0)));
        settle();
        assertFalse(fx(() -> dialog.isShowing()), "one button: closing means that button");
        assertSame(ButtonType.YES, fx(() -> dialog.getResult()));
    }

    @Test
    void englishTextsWhenJavaFxHasNone() {
        assertEquals("Information", NativeDialogs.resource("Xialog.info.title"));
        assertEquals("Warning", NativeDialogs.resource("Xialog.warning.header"));
        assertEquals("Error", NativeDialogs.resource("Xialog.error.title"));
        assertEquals("Confirmation", NativeDialogs.resource("Xialog.other.header"));
        assertEquals("Xialog.info.content", NativeDialogs.resource("Xialog.info.content"), "other keys stay as they are");
    }

    @Test
    void fileChooserFiltersAndInitialDirectory() throws Exception {
        fx(() -> {
            FileChooser chooser = new FileChooser();
            NativeModel m = model(chooser);
            NativeDialogs.Filter none = NativeDialogs.filter(m);
            assertTrue(none.patterns.isEmpty());
            assertNull(none.description);
            assertNull(NativeDialogs.directory(m));
            FileChooser.ExtensionFilter images = new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg");
            FileChooser.ExtensionFilter text = new FileChooser.ExtensionFilter("Texto", "*.txt");
            chooser.getExtensionFilters().addAll(images, text);
            NativeDialogs.Filter first = NativeDialogs.filter(m);
            assertEquals(Arrays.asList("*.png", "*.jpg"), first.patterns, "the first filter by default");
            assertEquals("Imagens", first.description);
            chooser.setSelectedExtensionFilter(text);
            assertEquals(Arrays.asList("*.txt"), NativeDialogs.filter(m).patterns, "the selected filter wins");
            File dir = new File(System.getProperty("java.io.tmpdir"));
            chooser.setInitialDirectory(dir);
            assertEquals(dir, NativeDialogs.directory(m));
            return null;
        });
    }

    // ---- notifications -----------------------------------------------------------------------

    private static NativeModel notification(String title, String text) {
        NativeModel n = new NativeModel(Object.class);
        Native.property(n, "title", Object.class).setValue(title);
        Native.property(n, "text", Object.class).setValue(text);
        return n;
    }

    private static void waitUntil(java.util.function.BooleanSupplier done) throws Exception {
        long end = System.currentTimeMillis() + 5000;
        while (!fx(done::getAsBoolean) && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        assertTrue(fx(done::getAsBoolean), "timed out");
    }

    @Test
    void notificationsStackInTheCornerRunTheirActionAndHide() throws Exception {
        AtomicInteger actions = new AtomicInteger();
        int windows = fx(() -> NativeRuntime.SCENES.size());
        NativeModel first = notification("Salvo", "O arquivo foi salvo");
        fx(() -> {
            Native.property(first, "hideAfter", Object.class).setValue(Duration.INDEFINITE);
            Native.property(first, "onAction", Object.class).setValue((EventHandler<javafx.event.ActionEvent>) e -> actions.incrementAndGet());
            NativeDialogs.notify(first, AlertType.INFORMATION);
        });
        NativeScene a = fx(NativeDialogKindsTest::top);
        assertTrue(NativeDialogs.notificationsShowing());
        assertTrue(fx(() -> labels(a)).containsAll(Arrays.asList("Salvo", "O arquivo foi salvo")));
        assertTrue(fx(() -> iconStyle(a)).contains("#2d6cdf"));
        double[] size = fx(() -> NativeRuntime.preferredSize(a.rootModel()));
        assertEquals(1280 - size[0] - 16, fx(() -> NativeElements.number(a.stage, "x", -1)), 0.001);
        assertEquals(800 - (size[1] + 12) - 4, fx(() -> NativeElements.number(a.stage, "y", -1)), 0.001);
        assertTrue(fx(() -> NativeElements.string(a.rootModel(), "style")).contains("#fafafa"));

        NativeModel second = notification("", "escuro");
        Thread other = new Thread(() -> {
            Native.property(second, "dark", boolean.class).setValue(true);
            Native.property(second, "hideAfter", Object.class).setValue(Duration.millis(50));
            NativeDialogs.notify(second, null);
        });
        other.start();
        other.join();
        waitUntil(() -> NativeRuntime.SCENES.size() == windows + 2);
        NativeScene b = fx(NativeDialogKindsTest::top);
        assertTrue(fx(() -> NativeElements.string(b.rootModel(), "style")).contains("#333333"));
        assertNull(fx(() -> iconStyle(b)), "no kind, no graphic: no icon");
        assertEquals(Arrays.asList("escuro"), fx(() -> labels(b)), "no title label without a title");
        assertTrue(fx(() -> NativeElements.number(b.stage, "y", 0)) < fx(() -> NativeElements.number(a.stage, "y", 0)),
                "the second one stacks above the first");
        waitUntil(() -> NativeRuntime.SCENES.size() == windows + 1);

        click(a, 20, 20);
        assertEquals(1, actions.get(), "clicking the notification runs its action");
        fx(() -> NativeRuntime.hide(a.stage));
        assertEquals(windows, (int) fx(() -> NativeRuntime.SCENES.size()));
    }
}
