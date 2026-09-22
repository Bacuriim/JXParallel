package com.jxparallel.javafx.controls;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXListViewApiTest {
    @Test
    void shouldExposeJavaFxCollectionAndSelectionConcepts() throws Exception {
        Method items = JXListView.class.getMethod("getItems");
        Method selection = JXListView.class.getMethod("getSelectionModel");

        assertEquals("getItems", items.getName());
        assertEquals("getSelectionModel", selection.getName());
    }
}
