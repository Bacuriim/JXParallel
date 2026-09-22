package com.jxparallel.ui.resource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXMarkupLoaderTest {
    @Test
    void shouldLoadIndependentMarkup() {
        JXMarkupLoader loader = new JXMarkupLoader();
        com.jxparallel.ui.JXElement root = loader.load(new ByteArrayInputStream(
                "<column gap=\"8\"><button label=\"Save\"/><input value=\"Name\"/></column>"
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("column", root.getType());
        assertEquals(2, root.getChildren().size());
        assertEquals("Save", root.getChildren().get(0).getProps().getString("label"));
    }
}
