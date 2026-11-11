package io.github.bacuriim.jxforge;

import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class FileTypeTest extends BasePlatformTestCase {
    public void testExtensionIsJxmlFileType() {
        FileType type = FileTypeManager.getInstance().getFileTypeByFileName("a.jxml");
        assertSame(JxmlFileType.INSTANCE, type);
        assertEquals("JXML", type.getName());
        assertEquals("jxml", type.getDefaultExtension());
        assertNotNull(type.getIcon());
        assertFalse(type.isBinary());
    }

    public void testFileIsAnXmlFileOfTheJxmlLanguage() {
        PsiFile file = myFixture.configureByText("a.jxml", "<Column><Label text=\"x\"/></Column>");
        assertInstanceOf(file, XmlFile.class);
        assertSame(JxmlLanguage.INSTANCE, file.getLanguage());
        assertTrue(file.getLanguage().isKindOf(XMLLanguage.INSTANCE));
        assertEquals("Column", ((XmlFile) file).getRootTag().getName());
    }

    public void testXmlFoldingBuilderIsFoundForJxml() {
        PsiFile file = myFixture.configureByText("a.jxml", "<Column>\n  <Row>\n    <Label text=\"a\"/>\n  </Row>\n</Column>\n");
        assertFalse(LanguageFolding.INSTANCE.allForLanguage(file.getLanguage()).isEmpty());
    }

    public void testSyntaxHighlighterIsTheXmlOne() {
        PsiFile file = myFixture.configureByText("a.jxml", "<Column/>");
        SyntaxHighlighter h = SyntaxHighlighterFactory.getSyntaxHighlighter(JxmlLanguage.INSTANCE, getProject(), file.getVirtualFile());
        assertFalse(h instanceof PlainSyntaxHighlighter);
    }

    public void testXmlReformatWorks() {
        PsiFile file = myFixture.configureByText("a.jxml", "<Column>\n<Row>\n<Label text=\"a\"/>\n</Row>\n</Column>");
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () -> CodeStyleManager.getInstance(getProject()).reformat(file));
        assertEquals("<Column>\n    <Row>\n        <Label text=\"a\"/>\n    </Row>\n</Column>", file.getText());
    }
}
