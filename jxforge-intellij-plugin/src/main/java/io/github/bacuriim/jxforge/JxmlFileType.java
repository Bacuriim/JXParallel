package io.github.bacuriim.jxforge;

import com.intellij.ide.highlighter.XmlLikeFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** The {@code .jxml} file type. {@link XmlLikeFileType} makes the platform read the encoding declaration like an XML file. */
public final class JxmlFileType extends XmlLikeFileType {
    public static final JxmlFileType INSTANCE = new JxmlFileType();
    public static final String EXTENSION = "jxml";

    private JxmlFileType() {
        super(JxmlLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "JXML";
    }

    @Override
    public @NotNull String getDescription() {
        return "JXForge screen (JXML)";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return EXTENSION;
    }

    @Override
    public Icon getIcon() {
        return JxmlIcons.FILE;
    }
}
