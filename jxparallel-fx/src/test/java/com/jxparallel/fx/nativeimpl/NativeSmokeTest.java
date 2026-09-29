package com.jxparallel.fx.nativeimpl;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.scene.control.Button;
import com.jxparallel.fx.scene.control.Label;
import com.jxparallel.fx.scene.control.TextField;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End to end in native mode: show a screen, click, type, and see the controller's code run. */
class NativeSmokeTest extends NativeTestSupport {
    @Test
    void clickingAButtonRunsItsHandler() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        Button[] button = new Button[1];
        NativeScene s = show(fx(() -> {
            button[0] = new Button("Save");
            button[0].setOnAction(e -> clicks.incrementAndGet());
            return new VBox(8, new Label("Customers"), button[0]);
        }), 300, 200);

        click(s, button[0]);

        assertEquals(1, clicks.get());
        close(s);
    }

    @Test
    void typingGoesIntoTheFocusedTextField() throws Exception {
        TextField[] field = new TextField[1];
        NativeScene s = show(fx(() -> {
            field[0] = new TextField();
            return new VBox(8, field[0]);
        }), 300, 200);

        click(s, field[0]);
        type(s, "Ada");
        key(s, "LEFT");
        type(s, "!");

        assertEquals("Ad!a", fx(() -> field[0].getText()));
        assertTrue(fx(() -> field[0].isFocused()));
        JXNativeNode node = node(s, field[0]);
        assertEquals("Ad!a", node.getProperty("value"));
        close(s);
    }
}
