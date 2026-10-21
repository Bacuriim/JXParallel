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

/** Reads a JXML file into a plain tree with line numbers; it knows nothing about the tags. */
final class JxmlParser {
    /** One XML element as written. */
    static final class Node {
        final String tag;
        final int line;
        final Map<String, String> attrs = new LinkedHashMap<String, String>();
        final List<Node> children = new ArrayList<Node>();

        Node(String tag, int line) {
            this.tag = tag;
            this.line = line;
        }
    }

    private JxmlParser() {
    }

    static Node parse(InputStream input, final String source) {
        if (input == null) {
            throw new IllegalArgumentException("No input for " + source);
        }
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            SAXParser parser = factory.newSAXParser();
            final Deque<Node> open = new ArrayDeque<Node>();
            final Node[] root = new Node[1];
            parser.parse(input, new DefaultHandler() {
                private Locator locator;

                @Override
                public void setDocumentLocator(Locator value) {
                    locator = value;
                }

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    Node node = new Node(qName, locator == null ? 0 : locator.getLineNumber());
                    for (int i = 0; i < attributes.getLength(); i++) {
                        String name = attributes.getQName(i);
                        if (!name.equals("xmlns") && !name.startsWith("xmlns:")) {
                            node.attrs.put(name, attributes.getValue(i));
                        }
                    }
                    if (open.isEmpty()) {
                        root[0] = node;
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
                    for (int i = start; i < start + length; i++) {
                        if (!Character.isWhitespace(ch[i])) {
                            int line = locator == null ? 0 : locator.getLineNumber();
                            throw new JxmlException(source, line,
                                    "text content is not allowed; use an attribute, such as text=\"...\"");
                        }
                    }
                }
            });
            return root[0];
        } catch (JxmlException e) {
            throw e;
        } catch (SAXParseException e) {
            throw new JxmlException(source, e.getLineNumber(), e.getMessage(), e);
        } catch (Exception e) {
            throw new JxmlException(source, 0, "unreadable: " + e.getMessage(), e);
        }
    }
}
