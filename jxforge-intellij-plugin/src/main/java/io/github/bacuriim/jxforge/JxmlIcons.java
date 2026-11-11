package io.github.bacuriim.jxforge;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

/** The SVG icons of the plugin; a {@code _dark} file next to each is used in dark themes. */
public final class JxmlIcons {
    public static final Icon FILE = IconLoader.getIcon("/icons/jxml.svg", JxmlIcons.class);
    public static final Icon CONTROLLER = IconLoader.getIcon("/icons/jxmlController.svg", JxmlIcons.class);

    private JxmlIcons() {
    }
}
