package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** The XSD generated from {@link JxmlSchema}: valid, accepts every sample screen, and rejects what the reader rejects. */
class JxmlXsdTest {
    private static String xsd;
    private static Schema schema;

    @BeforeAll
    static void generate() throws Exception {
        xsd = JxmlXsd.generate();
        schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new ByteArrayInputStream(xsd.getBytes(StandardCharsets.UTF_8))));
    }

    /** Null when {@code jxml} is valid, else the first error. */
    private static String check(String jxml) {
        return check(new StreamSource(new ByteArrayInputStream(jxml.getBytes(StandardCharsets.UTF_8))));
    }

    private static String check(StreamSource source) {
        Validator validator = schema.newValidator();
        try {
            validator.validate(source);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Test
    void theSchemaIsWellFormedXmlAndAValidSchema() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xsd.getBytes(StandardCharsets.UTF_8)));
        assertEquals("schema", document.getDocumentElement().getLocalName());
        assertNotNull(schema); // SchemaFactory compiled it in @BeforeAll
        assertEquals("", document.getDocumentElement().getAttribute("targetNamespace"),
                "JXML files have no namespace, so the schema has none");
    }

    @Test
    void everyTagOfTheTableHasAnElementAndEveryAttributeIsDeclared() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xsd.getBytes(StandardCharsets.UTF_8)));
        Set<String> declared = new LinkedHashSet<String>();
        NodeList top = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < top.getLength(); i++) {
            if (top.item(i) instanceof Element && "element".equals(top.item(i).getLocalName())) {
                Element element = (Element) top.item(i);
                declared.add(element.getAttribute("name"));
                if (JxmlSchema.tag(element.getAttribute("name")) != null) {
                    JxmlSchema.Tag tag = JxmlSchema.tag(element.getAttribute("name"));
                    Set<String> attrs = new LinkedHashSet<String>();
                    NodeList all = element.getElementsByTagNameNS("*", "attribute");
                    for (int a = 0; a < all.getLength(); a++) {
                        attrs.add(((Element) all.item(a)).getAttribute("name"));
                    }
                    if (element.getElementsByTagNameNS("*", "attributeGroup").getLength() > 0) {
                        // the layout attributes come from the group every element but the language's tags refers to
                        for (int g = 0; g < top.getLength(); g++) {
                            if (top.item(g) instanceof Element && "attributeGroup".equals(top.item(g).getLocalName())) {
                                NodeList grouped = ((Element) top.item(g)).getElementsByTagNameNS("*", "attribute");
                                for (int a = 0; a < grouped.getLength(); a++) {
                                    attrs.add(((Element) grouped.item(a)).getAttribute("name"));
                                }
                            }
                        }
                    }
                    for (String name : tag.attrs.keySet()) {
                        assertTrue(attrs.contains(name), tag.name + " lacks the attribute " + name);
                    }
                    if (!tag.control) {
                        for (String name : JxmlSchema.LAYOUT.keySet()) {
                            assertTrue(attrs.contains(name), tag.name + " lacks the layout attribute " + name);
                        }
                    }
                }
            }
        }
        Set<String> expected = new LinkedHashSet<String>(JxmlSchema.tags().keySet());
        expected.add("Include");
        expected.add("Style");
        assertEquals(expected, declared);
    }

    @Test
    void everySampleScreenValidates() throws IOException {
        List<Path> samples = new ArrayList<Path>();
        try (Stream<Path> walk = Files.walk(Paths.get("src/test/shared/samples"))) {
            samples.addAll(walk.filter(p -> p.toString().endsWith(".jxml")).collect(Collectors.toList()));
        }
        samples.add(Paths.get("src/test/resources/feature.jxml"));
        assertTrue(samples.size() > 15, "the samples were not found from " + new File(".").getAbsolutePath());
        for (Path sample : samples) {
            assertNull(check(new StreamSource(sample.toFile())), sample.toString());
        }
    }

    /** The screens of the example application are a second corpus; skipped when the module is not beside this one. */
    @Test
    void theExampleScreensValidate() throws IOException {
        Path dir = Paths.get("../jxparallel-examples-native/src/main/jxml");
        if (!Files.isDirectory(dir)) {
            return;
        }
        int count = 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".jxml")).collect(Collectors.toList())) {
                assertNull(check(new StreamSource(file.toFile())), file.toString());
                count++;
            }
        }
        assertTrue(count > 100, "found only " + count);
    }

    @Test
    void aFileMayPointToTheSchemaAndStillLoads() {
        String jxml = "<Column xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:noNamespaceSchemaLocation=\"jxml.xsd\" gap=\"4\"><Label text=\"${title}\"/></Column>";
        assertNull(check(jxml));
        // the reader ignores the attribute, so the same file is a screen
        assertEquals("x.jxml", JxmlTemplate.read(new ByteArrayInputStream(jxml.getBytes(StandardCharsets.UTF_8)), "x.jxml")
                .getSource());
    }

    @Test
    void anUnknownTagFails() {
        assertNotNull(check("<Column><Labl text=\"x\"/></Column>"));
        assertNotNull(check("<Colum/>"));
    }

    @Test
    void anUnknownAttributeFails() {
        assertNotNull(check("<Column><Label txt=\"x\"/></Column>"));
        assertNotNull(check("<Column gap=\"4\" colour=\"#fff\"/>"));
        // an attribute of another tag
        assertNotNull(check("<Column><Label selected=\"true\"/></Column>"));
    }

    @Test
    void valuesAreCheckedForShapeNotForTheirSource() {
        assertNull(check("<Column gap=\"${gap}\" padding=\"4 8\"><Label text=\"a\" fontSize=\"13.5\"/></Column>"));
        assertNull(check("<Column><TextField text=\"@{name}\"/><CheckBox selected=\"@{on}\"/><Button onAction=\"#save\"/></Column>"));
        assertNull(check("<Column><Button onAction=\"#open(item)\" variant=\"tonal\" textFill=\"primary\"/></Column>"));
        assertNotNull(check("<Column><Button disabled=\"maybe\"/></Column>"));
        assertNotNull(check("<Column><Button variant=\"huge\"/></Column>"));
        assertNotNull(check("<Column gap=\"wide\"/>"));
        assertNotNull(check("<Column><Button onAction=\"save\"/></Column>"));
        assertNotNull(check("<Column><Label textFill=\"#12\"/></Column>"));
        // a value the reader takes only as a literal cannot be bound
        assertNotNull(check("<Column padding=\"${p}\"/>"));
        // required attributes
        assertNotNull(check("<Column><Icon/></Column>"));
        assertNotNull(check("<Column><For var=\"r\"/></Column>"));
    }

    @Test
    void childrenFollowTheTable() {
        assertNull(check("<Column><For items=\"${rows}\" var=\"r\"><If test=\"${r.on}\"><Label text=\"x\"/></If><Else><Label/></Else></For></Column>"));
        assertNull(check("<Tabs><Tab title=\"A\"><Label/></Tab></Tabs>"));
        assertNull(check("<MenuBar><Menu text=\"File\"><MenuItem text=\"Open\"/><MenuSeparator/></Menu></MenuBar>"));
        assertNull(check("<Tree><TreeItem text=\"a\"><TreeItem text=\"b\"/></TreeItem></Tree>"));
        assertNotNull(check("<Label><Label/></Label>"));              // no children
        assertNotNull(check("<Column><Tab title=\"A\"/></Column>"));   // Tab only in Tabs
        assertNotNull(check("<Column><MenuItem text=\"A\"/></Column>")); // MenuItem only in menus
        assertNotNull(check("<Tabs><Label/></Tabs>"));
        assertNotNull(check("<MenuBar><Label/></MenuBar>"));
        assertNotNull(check("<Scroll><Label/><Label/></Scroll>"));      // one child
    }

    @Test
    void includeAndStyleAreInTheSchema() {
        assertNull(check("<Column><Style name=\"t\" bold=\"true\" textFill=\"primary\"/><Include src=\"parts/a.jxml\" hgrow=\"always\"/>"
                + "<Label style=\"t\"/></Column>"));
        assertNotNull(check("<Column><Include/></Column>"));
        assertNotNull(check("<Column><Style bold=\"true\"/></Column>"));
    }

    @Test
    void writeCreatesTheFile(@TempDir Path dir) throws IOException {
        File file = dir.resolve("deep/jxml.xsd").toFile();
        JxmlXsd.write(file);
        assertEquals(xsd, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertFalse(xsd.isEmpty());
    }

    @Test
    void theOutputIsTheSameEveryTime() {
        assertEquals(xsd, JxmlXsd.generate());
    }
}
