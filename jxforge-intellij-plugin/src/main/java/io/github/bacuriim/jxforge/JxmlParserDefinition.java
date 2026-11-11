package io.github.bacuriim.jxforge;

import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

/** XML parsing, with a file element of the JXML language (as XHTML does it). */
public final class JxmlParserDefinition extends XMLParserDefinition {
    private static final IFileElementType JXML_FILE = new IFileElementType("JXML_FILE", JxmlLanguage.INSTANCE);

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return JXML_FILE;
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new XmlFileImpl(viewProvider, JXML_FILE);
    }
}
