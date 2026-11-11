package com.jxparallel.forge;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Reads an FXML file into a plain tree with line numbers, keeping the {@code <?import?>} instructions. */
final class FxmlParser {
    /** One XML element as written: {@code Button}, {@code GridPane.rowIndex}, {@code fx:include}, {@code children}. */
    static final class FxNode {
        final String name;
        final int line;
        final Map<String, String> attrs = new LinkedHashMap<String, String>();
        final List<FxNode> children = new ArrayList<FxNode>();
        final StringBuilder text = new StringBuilder();

        FxNode(String name, int line) {
            this.name = name;
            this.line = line;
        }

        /** An element whose name starts with a lower-case letter is a property of its parent, not an object. */
        boolean isProperty() {
            return !name.contains(":") && Character.isLowerCase(name.charAt(0)) && !name.contains(".");
        }

        /** {@code GridPane.margin}: a static property of another class, written as an element. */
        boolean isStaticProperty() {
            int dot = name.lastIndexOf('.');
            return dot > 0 && dot < name.length() - 1 && Character.isLowerCase(name.charAt(dot + 1));
        }

        String trimmedText() {
            return text.toString().trim();
        }
    }

    /** The parsed file. */
    static final class Document {
        FxNode root;
        /** Simple class name to the imported class, for the {@code <?import a.b.C?>} instructions (wildcards are kept as packages). */
        final Map<String, String> imports = new LinkedHashMap<String, String>();
        final List<String> wildcardPackages = new ArrayList<String>();
    }

    private FxmlParser() {
    }

    static Document parse(InputStream input, final String source) {
        if (input == null) {
            throw new IllegalArgumentException("No input for " + source);
        }
        final Document document = new Document();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            SAXParser parser = factory.newSAXParser();
            final Deque<FxNode> open = new ArrayDeque<FxNode>();
            parser.parse(input, new DefaultHandler() {
                private Locator locator;

                @Override
                public void setDocumentLocator(Locator value) {
                    locator = value;
                }

                @Override
                public void processingInstruction(String target, String data) {
                    if ("import".equals(target) && data != null) {
                        String name = data.trim();
                        if (name.endsWith(".*")) {
                            document.wildcardPackages.add(name.substring(0, name.length() - 2));
                        } else {
                            document.imports.put(name.substring(name.lastIndexOf('.') + 1), name);
                        }
                    }
                }

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    FxNode node = new FxNode(qName, locator == null ? 0 : locator.getLineNumber());
                    for (int i = 0; i < attributes.getLength(); i++) {
                        node.attrs.put(attributes.getQName(i), attributes.getValue(i));
                    }
                    if (open.isEmpty()) {
                        document.root = node;
                    } else {
                        open.peek().children.add(node);
                    }
                    open.push(node);
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    open.pop();
                }

                @Override
                public void characters(char[] ch, int start, int length) {
                    if (!open.isEmpty()) {
                        open.peek().text.append(ch, start, length);
                    }
                }
            });
            if (document.root == null) {
                throw new JxmlException(source, 0, "no root element");
            }
            return document;
        } catch (JxmlException e) {
            throw e;
        } catch (SAXParseException e) {
            throw new JxmlException(source, e.getLineNumber(), e.getMessage(), e);
        } catch (Exception e) {
            throw new JxmlException(source, 0, "unreadable: " + e.getMessage(), e);
        }
    }
}
