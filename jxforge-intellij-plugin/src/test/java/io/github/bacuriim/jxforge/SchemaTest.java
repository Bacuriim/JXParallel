package io.github.bacuriim.jxforge;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Arrays;
import java.util.List;

public class SchemaTest extends BasePlatformTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection.class);
    }

    private List<String> completions(String text) {
        myFixture.configureByText("a.jxml", text);
        LookupElement[] items = myFixture.complete(CompletionType.BASIC);
        return items == null ? List.of() : Arrays.stream(items).map(LookupElement::getLookupString).toList();
    }

    public void testTagCompletionWithoutAnySchemaLine() {
        List<String> c = completions("<Column>\n  <But<caret>\n</Column>");
        assertTrue(c.toString(), c.contains("Button"));
    }

    public void testAttributeCompletionWithoutAnySchemaLine() {
        List<String> c = completions("<Column>\n  <Button <caret>/>\n</Column>");
        assertTrue(c.toString(), c.contains("text"));
        assertTrue(c.toString(), c.contains("onAction"));
    }

    public void testRootTagCompletion() {
        List<String> c = completions("<<caret>");
        assertTrue(c.toString(), c.contains("Column"));
        myFixture.configureByText("b.jxml", "<Colu<caret>");
        myFixture.complete(CompletionType.BASIC); // the only variant is inserted
        assertTrue(myFixture.getEditor().getDocument().getText().startsWith("<Column"));
    }

    public void testUnknownTagAndAttributeAreReported() {
        myFixture.configureByText("a.jxml", "<Column><Nope/><Button bogus=\"1\"/></Column>");
        List<HighlightInfo> errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
        String all = errors.toString();
        assertTrue(all, all.contains("Nope"));
        assertTrue(all, all.contains("bogus"));
    }

    public void testValidFileHasNoErrors() {
        myFixture.configureByText("a.jxml",
                "<Column gap=\"4\" padding=\"8\"><Label text=\"Hi\"/><Button text=\"Ok\" onAction=\"#ok\"/></Column>");
        assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
    }

    public void testBundledUrnSchemaLineWorks() {
        List<String> c = completions("<Column xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + " xsi:noNamespaceSchemaLocation=\"urn:jxforge:jxml.xsd\">\n <But<caret>\n</Column>");
        assertTrue(c.toString(), c.contains("Button"));
        myFixture.configureByText("b.jxml", "<Column xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"urn:jxforge:jxml.xsd\"><Label text=\"x\"/><Nope/></Column>");
        String all = myFixture.doHighlighting(HighlightSeverity.ERROR).toString();
        assertTrue(all, all.contains("Nope"));
        assertFalse(all, all.contains("resolve"));
    }

    public void testSchemaCarriesDocumentation() throws Exception {
        String xsd = new String(getClass().getResourceAsStream("/io/github/bacuriim/jxforge/jxml.xsd").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xsd.contains("xs:documentation"));
        assertTrue(xsd.contains("name=\"Button\""));
    }

    public void testFileNamingItsOwnSchemaKeepsIt() {
        myFixture.addFileToProject("own.xsd", "<?xml version=\"1.0\"?>\n<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                + "<xs:element name=\"Mine\"><xs:complexType><xs:sequence><xs:element name=\"Child\" minOccurs=\"0\"/>"
                + "</xs:sequence></xs:complexType></xs:element>\n</xs:schema>");
        List<String> c = completions("<Mine xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:noNamespaceSchemaLocation=\"own.xsd\">\n <<caret>\n</Mine>");
        assertTrue(c.toString(), c.contains("Child"));
        assertFalse(c.toString(), c.contains("Button"));
    }
}
