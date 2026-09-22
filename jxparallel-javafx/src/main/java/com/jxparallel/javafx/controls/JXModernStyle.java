package com.jxparallel.javafx.controls;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.util.Duration;

public final class JXModernStyle {
    private static final String HOVER_HANDLERS_KEY = JXModernStyle.class.getName() + ".hoverHandlers";

    private JXModernStyle() {
    }

    public static void apply(Control control, JXVisualVariant variant, JXVisualDensity density) {
        if (control == null) {
            return;
        }
        JXVisualVariant effectiveVariant = variant == null ? JXVisualVariant.DEFAULT : variant;
        JXVisualDensity effectiveDensity = density == null ? JXVisualDensity.COMFORTABLE : density;
        control.getStyleClass().removeIf(style -> style.startsWith("jx-variant-")
                || style.startsWith("jx-density-"));
        control.getStyleClass().add("jx-modern-control");
        control.getStyleClass().add("jx-variant-" + effectiveVariant.name().toLowerCase());
        control.getStyleClass().add("jx-density-" + effectiveDensity.name().toLowerCase());
        String baseStyle = "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: "
                + padding(effectiveDensity) + "; -fx-focus-color: transparent; "
                + "-fx-faint-focus-color: transparent;";
        String currentStyle = control.getStyle();
        if (currentStyle == null || currentStyle.trim().isEmpty()) {
            control.setStyle(baseStyle);
        } else if (!currentStyle.contains("-fx-background-radius")) {
            control.setStyle(currentStyle + "; " + baseStyle);
        }
    }

    public static void animateHover(Node node) {
        if (node == null) {
            return;
        }
        if (node.getProperties().putIfAbsent(HOVER_HANDLERS_KEY, Boolean.TRUE) == null) {
            node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, event -> {
                ScaleTransition transition = new ScaleTransition(Duration.millis(120), node);
                transition.setToX(1.015);
                transition.setToY(1.015);
                transition.playFromStart();
            });
            node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, event -> {
                ScaleTransition transition = new ScaleTransition(Duration.millis(140), node);
                transition.setToX(1.0);
                transition.setToY(1.0);
                transition.playFromStart();
            });
        }
    }

    public static void animateAppear(Node node) {
        if (node == null) {
            return;
        }
        node.setOpacity(0.0);
        FadeTransition transition = new FadeTransition(Duration.millis(180), node);
        transition.setToValue(1.0);
        transition.play();
    }

    private static int padding(JXVisualDensity density) {
        switch (density) {
            case COMPACT:
                return 5;
            case SPACIOUS:
                return 11;
            case COMFORTABLE:
            default:
                return 8;
        }
    }
}
