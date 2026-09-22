package com.jxparallel.ui.resource;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXMarkupLoader {
    private final JXResourceCache cache;

    public JXMarkupLoader() {
        this(new JXResourceCache(100));
    }

    public JXMarkupLoader(JXResourceCache cache) {
        this.cache = cache;
    }

    public JXElement load(InputStream input) {
        if (input == null) throw new IllegalArgumentException("Input cannot be null");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder()
                    .parse(input).getDocumentElement();
            return convert(root);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to load JX markup", exception);
        }
    }

    public JXResourceCache cache() { return cache; }

    private JXElement convert(Element element) {
        JXProps.Builder props = JXProps.builder();
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            props.set(attribute.getNodeName(), attribute.getNodeValue());
        }
        java.util.List<JXElement> children = new java.util.ArrayList<JXElement>();
        NodeList nodes = element.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element) {
                children.add(convert((Element) nodes.item(index)));
            }
        }
        return JXElement.of(element.getTagName(), props.build(),
                children.toArray(new JXElement[children.size()]));
    }
}
