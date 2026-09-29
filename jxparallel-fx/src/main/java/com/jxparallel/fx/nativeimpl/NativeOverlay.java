package com.jxparallel.fx.nativeimpl;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXNativeNode;

/**
 * Something drawn over a scene: the list of an open combo box, a date picker's calendar, a
 * tooltip, a context menu or the view of a drag. Popups are layers of the window (not separate
 * windows), placed below their owner and kept inside the window, like JavaFX's auto-fix.
 */
final class NativeOverlay {
    enum Kind { COMBO, CALENDAR, TOOLTIP, MENU, DRAG }

    final Kind kind;
    final NativeModel owner;
    double x;
    double y;
    /** Calendar month shown (the date picker's month when opened). */
    int year;
    int month;
    /** Tooltip text or the context menu model. */
    Object content;

    NativeOverlay(Kind kind, NativeModel owner) {
        this.kind = kind;
        this.owner = owner;
    }

    JXElement element(NativeScene scene) {
        JXNativeNode anchor = owner == null ? null : scene.node(owner);
        switch (kind) {
            case COMBO: {
                if (anchor == null) {
                    return null;
                }
                double width = Math.max(anchor.getWidth(), 60);
                JXElement list = NativeCells.comboPopup(owner, scene, width);
                double h = NativeCells.number(list.getProps().get("prefHeight"), 100);
                double[] at = below(scene, anchor, width, h);
                return place(list, at[0], at[1]);
            }
            case CALENDAR: {
                if (anchor == null) {
                    return null;
                }
                JXElement calendar = calendar();
                double[] at = below(scene, anchor, 236, 208);
                return place(calendar, at[0], at[1]);
            }
            case TOOLTIP: {
                JXElement tip = JXElement.of("tooltip", JXProps.builder().set("label", String.valueOf(content)).build());
                return place(tip, Math.min(x, Math.max(0, scene.width - 300)), Math.min(y, Math.max(0, scene.height - 30)));
            }
            case MENU: {
                JXElement menu = menu(scene);
                return menu == null ? null : place(menu, x, y);
            }
            case DRAG: {
                if (!(content instanceof javafx.scene.image.Image)) {
                    return null;
                }
                javafx.scene.image.Image image = (javafx.scene.image.Image) content;
                JXElement view = JXElement.of("image", JXProps.builder().set("pixels", NativeElements.pixels(image))
                        .set("imageWidth", image.getWidth()).set("imageHeight", image.getHeight())
                        .set("opacity", 0.6).set("mouseTransparent", true).build());
                return place(view, x + 2, y + 2);
            }
            default:
                return null;
        }
    }

    /** Below the owner, or above it when there is no room, inside the window. */
    private static double[] below(NativeScene scene, JXNativeNode anchor, double w, double h) {
        double px = Math.max(0, Math.min(anchor.getX(), scene.width - w));
        double py = anchor.getY() + anchor.getHeight();
        if (py + h > scene.height && anchor.getY() - h >= 0) {
            py = anchor.getY() - h;
        }
        return new double[] {px, py};
    }

    private static JXElement place(JXElement e, double x, double y) {
        JXProps.Builder b = JXProps.builder();
        e.getProps().asMap().forEach(b::set);
        b.set("leftAnchor", Math.floor(x)).set("topAnchor", Math.floor(y)).set("overlay", true);
        return JXElement.of(e.getType(), b.build(), e.getChildren().toArray(new JXElement[0]));
    }

    private JXElement calendar() {
        Locale locale = Locale.getDefault();
        LocalDate first = LocalDate.of(year, month, 1);
        int firstDay = WeekFields.of(locale).getFirstDayOfWeek().getValue();
        String[] names = new String[7];
        for (int i = 0; i < 7; i++) {
            names[i] = java.time.DayOfWeek.of((firstDay - 1 + i) % 7 + 1).getDisplayName(TextStyle.SHORT, locale);
        }
        String title = first.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + year;
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        Object value = Native.value(owner, "value");
        LocalDate today = LocalDate.now();
        JXProps.Builder p = JXProps.builder().set("popupOf", owner).set("year", year).set("month", month)
                .set("firstDayOfWeek", firstDay).set("weekdays", names).set("title", title)
                .set("todayYear", today.getYear()).set("todayMonth", today.getMonthValue()).set("todayDay", today.getDayOfMonth());
        if (value instanceof LocalDate) {
            LocalDate v = (LocalDate) value;
            p.set("selectedYear", v.getYear()).set("selectedMonth", v.getMonthValue()).set("selectedDay", v.getDayOfMonth());
        }
        return JXElement.of("calendar", p.build());
    }

    /** A context menu: its items as cells, separators as separators. */
    private JXElement menu(NativeScene scene) {
        NativeModel menu = NativeElements.model(content);
        if (menu == null) {
            return null;
        }
        List<JXElement> rows = new ArrayList<>();
        int index = 0;
        int hovered = (int) NativeCells.number(menu.state.get("hover"), -1);
        for (Object o : Native.list(menu, "items")) {
            NativeModel item = NativeElements.model(o);
            if (item == null || Boolean.FALSE.equals(Native.value(item, "visible"))) {
                index++;
                continue;
            }
            if (item.is(javafx.scene.control.SeparatorMenuItem.class)) {
                rows.add(JXElement.of("separator", JXProps.builder().set("orientation", "horizontal").build()));
            } else {
                rows.add(JXElement.of("cell", JXProps.builder().set("value", NativeElements.string(item, "text"))
                        .set("menuIndex", index).set("selected", index == hovered).set("listFocused", true)
                        .set("disabled", Boolean.TRUE.equals(Native.value(item, "disable")))
                        .set("padding", new double[] {4, 20, 4, 12}).set("model", item).build()));
            }
            index++;
        }
        return JXElement.of("popup", JXProps.builder().set("popupOf", menu).set("padding", new double[] {1, 1, 1, 1})
                .set("minWidth", 120.0).build(), rows.toArray(new JXElement[0]));
    }
}
