package com.jxparallel.fx.sun.css;

/** JXParallel counterpart of JavaFX's internal {@code com.sun.javafx.css.StyleManager} (user agent stylesheets). */
public final class StyleManager {
    private static final StyleManager INSTANCE = new StyleManager();

    private StyleManager() {
    }

    public static StyleManager getInstance() {
        return INSTANCE;
    }

    public void addUserAgentStylesheet(String url) {
        com.sun.javafx.css.StyleManager.getInstance().addUserAgentStylesheet(url);
    }

    public void setDefaultUserAgentStylesheet(String url) {
        com.sun.javafx.css.StyleManager.getInstance().setDefaultUserAgentStylesheet(url);
    }
}
