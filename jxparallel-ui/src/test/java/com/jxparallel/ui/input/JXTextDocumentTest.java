package com.jxparallel.ui.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXTextDocumentTest {
    @Test
    void shouldEditAndSelectText() {
        JXTextDocument document = new JXTextDocument("abc");
        document.setCaret(1, false);
        document.insert("X");
        assertEquals("aXbc", document.getText());
        document.selectAll();
        document.insert("done");
        assertEquals("done", document.getText());
    }
}
