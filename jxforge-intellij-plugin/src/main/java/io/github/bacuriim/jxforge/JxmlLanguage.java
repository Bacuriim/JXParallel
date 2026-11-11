package io.github.bacuriim.jxforge;

import com.intellij.lang.xml.XMLLanguage;

/** JXML: the markup of JXForge. A dialect of XML, so every XML feature of the IDE (editing, folding, formatting) applies. */
public final class JxmlLanguage extends XMLLanguage {
    public static final JxmlLanguage INSTANCE = new JxmlLanguage();

    private JxmlLanguage() {
        super(XMLLanguage.INSTANCE, "JXML", "text/jxml");
    }
}
