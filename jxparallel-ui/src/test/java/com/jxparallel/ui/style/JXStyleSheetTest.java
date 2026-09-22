package com.jxparallel.ui.style;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXStyleSheetTest {
    @Test
    void shouldResolveTypeAndIdRules() {
        JXStyleSheet sheet = new JXStyleSheet()
                .add("button", JXStyle.builder().set("size", Integer.valueOf(12)).build())
                .add("#save", JXStyle.builder().set("size", Integer.valueOf(14)).build());
        JXElement button = JXElement.of("button", JXProps.builder().set("id", "save").build());
        assertEquals(14, sheet.resolve(button, null).get("size"));
    }
}
