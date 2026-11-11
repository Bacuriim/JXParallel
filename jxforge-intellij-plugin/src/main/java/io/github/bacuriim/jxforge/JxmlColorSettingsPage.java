package io.github.bacuriim.jxforge;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Map;

/** Settings | Editor | Color Scheme | JXML. */
public final class JxmlColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Binding ${...}", JxmlHighlightKeys.BINDING),
            new AttributesDescriptor("Two-way binding @{...}", JxmlHighlightKeys.TWO_WAY_BINDING),
            new AttributesDescriptor("Handler #method", JxmlHighlightKeys.HANDLER),
    };

    @Override
    public @Nullable Icon getIcon() {
        return JxmlIcons.FILE;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return SyntaxHighlighterFactory.getSyntaxHighlighter(XMLLanguage.INSTANCE, null, null);
    }

    @Override
    public @NotNull String getDemoText() {
        return "<Column controller=\"com.acme.CustomersController\" gap=\"8\">\n"
                + "  <TextField text=\"<two>@{customer.name}</two>\" prompt=\"Full name\"/>\n"
                + "  <Label text=\"<one>${status}</one>\"/>\n"
                + "  <For items=\"<one>${orders}</one>\" var=\"o\" type=\"com.acme.Order\">\n"
                + "    <Button text=\"Open\" onAction=\"<handler>#open(o)</handler>\"/>\n"
                + "  </For>\n"
                + "</Column>\n";
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return Map.of("one", JxmlHighlightKeys.BINDING, "two", JxmlHighlightKeys.TWO_WAY_BINDING,
                "handler", JxmlHighlightKeys.HANDLER);
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "JXML";
    }
}
