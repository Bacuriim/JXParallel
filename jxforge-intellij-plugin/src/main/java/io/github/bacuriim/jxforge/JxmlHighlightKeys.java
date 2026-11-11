package io.github.bacuriim.jxforge;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/** Text attributes of JXML values, configurable under Settings | Editor | Color Scheme | JXML. */
public final class JxmlHighlightKeys {
    /** {@code ${a.b}}: a one-way binding. */
    public static final TextAttributesKey BINDING =
            TextAttributesKey.createTextAttributesKey("JXML_BINDING", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    /** {@code @{x}}: a two-way binding. */
    public static final TextAttributesKey TWO_WAY_BINDING =
            TextAttributesKey.createTextAttributesKey("JXML_TWO_WAY_BINDING", DefaultLanguageHighlighterColors.STATIC_FIELD);
    /** {@code #handler}. */
    public static final TextAttributesKey HANDLER =
            TextAttributesKey.createTextAttributesKey("JXML_HANDLER", DefaultLanguageHighlighterColors.INSTANCE_METHOD);

    private JxmlHighlightKeys() {
    }
}
