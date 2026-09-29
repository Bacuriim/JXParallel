package com.jxparallel.fx.nativeimpl;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.layout.Pane;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where popups, calendars, tooltips, menus and drag views are placed in the window. */
class NativeOverlayTest extends NativeTestSupport {

    private static double left(JXElement e) {
        return ((Number) e.getProps().get("leftAnchor")).doubleValue();
    }

    private static double top(JXElement e) {
        return ((Number) e.getProps().get("topAnchor")).doubleValue();
    }

    @Test
    void comboListsOpenBelowOrAboveInsideTheWindow() throws Exception {
        ComboBox<String>[] combo = new ComboBox[3];
        NativeScene s = show(fx(() -> {
            for (int i = 0; i < 3; i++) {
                combo[i] = new ComboBox<>(FXCollections.observableArrayList("a", "b", "c", "d"));
            }
            combo[1].setTranslateX(0);
            Pane spacer = new Pane();
            spacer.setPrefHeight(250);
            combo[2].setPrefWidth(40);
            combo[2].setMinWidth(40);
            VBox right = new VBox(combo[2]);
            right.setPadding(new com.jxparallel.fx.geometry.Insets(0, 0, 0, 280));
            return new VBox(combo[0], right, spacer, combo[1]);
        }), 300, 330);
        JXNativeNode top = node(s, combo[0]);
        JXElement below = fx(() -> new NativeOverlay(NativeOverlay.Kind.COMBO, model(combo[0])).element(s));
        assertEquals(top.getX(), left(below), 0.001);
        assertEquals(top.getY() + top.getHeight(), top(below), 0.001);
        assertEquals(Math.max(top.getWidth(), 60), ((Number) below.getProps().get("prefWidth")).doubleValue(), 0.001);
        double h = 4 * 23 + 2;

        JXNativeNode low = node(s, combo[1]);
        JXElement above = fx(() -> new NativeOverlay(NativeOverlay.Kind.COMBO, model(combo[1])).element(s));
        assertTrue(low.getY() + low.getHeight() + h > 330, "no room below the last combo box");
        assertEquals(low.getY() - h, top(above), 0.001, "so the list opens above it");

        JXElement clamped = fx(() -> new NativeOverlay(NativeOverlay.Kind.COMBO, model(combo[2])).element(s));
        assertEquals(300 - 60, left(clamped), 0.001, "a list is at least 60 wide and stays in the window");
        assertNull(fx(() -> new NativeOverlay(NativeOverlay.Kind.COMBO, model(new ComboBox<String>())).element(s)),
                "a combo box that is not shown has no list");
        close(s);
    }

    @Test
    void calendarsShowTheMonthTheValueAndToday() throws Exception {
        DatePicker[] picker = new DatePicker[1];
        NativeScene s = show(fx(() -> {
            picker[0] = new DatePicker(LocalDate.of(2026, 9, 28));
            return new VBox(picker[0]);
        }), 400, 400);
        JXNativeNode n = node(s, picker[0]);
        JXElement calendar = fx(() -> {
            NativeOverlay o = new NativeOverlay(NativeOverlay.Kind.CALENDAR, model(picker[0]));
            o.year = 2026;
            o.month = 9;
            return o.element(s);
        });
        assertEquals("calendar", calendar.getType());
        assertEquals(n.getY() + n.getHeight(), top(calendar), 0.001);
        assertEquals(28, calendar.getProps().get("selectedDay"));
        assertEquals(9, calendar.getProps().get("selectedMonth"));
        assertEquals(LocalDate.now().getDayOfMonth(), calendar.getProps().get("todayDay"));
        String title = (String) calendar.getProps().get("title");
        assertTrue(Character.isUpperCase(title.charAt(0)) && title.endsWith("2026"), title);
        assertEquals(7, ((String[]) calendar.getProps().get("weekdays")).length);
        fx(() -> picker[0].setValue(null));
        JXElement empty = fx(() -> {
            NativeOverlay o = new NativeOverlay(NativeOverlay.Kind.CALENDAR, model(picker[0]));
            o.year = 2026;
            o.month = 2;
            return o.element(s);
        });
        assertNull(empty.getProps().get("selectedDay"), "no value, no selected day");
        assertNull(fx(() -> new NativeOverlay(NativeOverlay.Kind.CALENDAR, null).element(s)));
        close(s);
    }

    @Test
    void tooltipsStayInsideTheWindow() throws Exception {
        NativeScene s = show(fx(() -> new VBox(new Label("x"))), 400, 200);
        JXElement near = fx(() -> {
            NativeOverlay o = new NativeOverlay(NativeOverlay.Kind.TOOLTIP, null);
            o.content = "dica";
            o.x = 20;
            o.y = 30;
            return o.element(s);
        });
        assertEquals(20.0, left(near));
        assertEquals(30.0, top(near));
        assertEquals("dica", near.getProps().get("label"));
        JXElement far = fx(() -> {
            NativeOverlay o = new NativeOverlay(NativeOverlay.Kind.TOOLTIP, null);
            o.content = "dica";
            o.x = 1000;
            o.y = 1000;
            return o.element(s);
        });
        assertEquals(400 - 300, left(far), 0.001);
        assertEquals(200 - 30, top(far), 0.001);
        close(s);
        NativeScene tiny = show(fx(() -> new VBox(new Label("x"))), 100, 20);
        JXElement squeezed = fx(() -> {
            NativeOverlay o = new NativeOverlay(NativeOverlay.Kind.TOOLTIP, null);
            o.content = "dica";
            o.x = 50;
            o.y = 50;
            return o.element(tiny);
        });
        assertEquals(0.0, left(squeezed), "never left of the window");
        assertEquals(0.0, top(squeezed));
        close(tiny);
    }

    @Test
    void menusListVisibleItemsSeparatorsAndStates() throws Exception {
        NativeScene s = show(fx(() -> new VBox(new Label("x"))), 400, 300);
        ContextMenu menu = fx(() -> {
            MenuItem hidden = new MenuItem("oculto");
            hidden.setVisible(false);
            MenuItem off = new MenuItem("desligado");
            off.setDisable(true);
            return new ContextMenu(new MenuItem("um"), hidden, new SeparatorMenuItem(), off);
        });
        JXElement popup = fx(() -> {
            model(menu).state.put("hover", 3);
            NativeOverlay o = new NativeOverlay(NativeOverlay.Kind.MENU, null);
            o.content = menu;
            o.x = 15;
            o.y = 25;
            return o.element(s);
        });
        assertEquals(15.0, left(popup));
        assertEquals(25.0, top(popup));
        List<JXElement> rows = popup.getChildren();
        assertEquals(3, rows.size(), "the hidden item is left out");
        assertEquals("um", rows.get(0).getProps().get("value"));
        assertEquals(0, rows.get(0).getProps().get("menuIndex"));
        assertEquals("separator", rows.get(1).getType());
        assertEquals(3, rows.get(2).getProps().get("menuIndex"), "indices count the hidden item");
        assertEquals(true, rows.get(2).getProps().get("disabled"));
        assertEquals(true, rows.get(2).getProps().get("selected"), "the hovered row");
        assertEquals(false, rows.get(0).getProps().get("selected"));
        assertNull(fx(() -> new NativeOverlay(NativeOverlay.Kind.MENU, null).element(s)), "no menu, nothing");
        close(s);
    }

    @Test
    void dragViewsFollowThePointerHalfTransparent() throws Exception {
        NativeScene s = show(fx(() -> new VBox(new Label("x"))), 300, 200);
        JXElement view = fx(() -> {
            NativeOverlay o = new NativeOverlay(NativeOverlay.Kind.DRAG, null);
            o.content = new javafx.scene.image.WritableImage(4, 3);
            o.x = 50;
            o.y = 60;
            return o.element(s);
        });
        assertEquals(52.0, left(view));
        assertEquals(62.0, top(view));
        assertEquals(0.6, view.getProps().get("opacity"));
        assertEquals(true, view.getProps().get("mouseTransparent"));
        assertEquals(4.0, view.getProps().get("imageWidth"));
        assertNull(fx(() -> {
            NativeOverlay o = new NativeOverlay(NativeOverlay.Kind.DRAG, null);
            o.content = "not an image";
            return o.element(s);
        }));
        close(s);
    }
}
