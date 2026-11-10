package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;

/**
 * Scrolling in a file: {@code first="@{...}"} on the virtual lists and trees (the renderer reports the row at the top),
 * {@code hvalue}, {@code vvalue} and {@code onScroll} on a {@code Scroll}, and the interpreter and the generated code agree.
 */
class JxmlScrollTest {
    private static final String CTRL = "com.jxparallel.forge.samples.SampleController";
    private static final String ROW = CTRL + ".Row";

    @TempDir
    Path dir;

    private JXElement agree(String body, SampleController c) throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views("<Column controller=\"" + CTRL + "\">\n" + body + "\n</Column>", "t.jxml");
        JXElement first = views.get(0).build(c);
        for (int i = 1; i < views.size(); i++) {
            assertNull(JxmlTrees.diff(first, views.get(i).build(c)), "view " + i);
        }
        return first.getChildren().get(0);
    }

    @SuppressWarnings("unchecked")
    private static void accept(JXElement e, String prop, Object value) {
        ((Consumer<Object>) e.getProps().get(prop)).accept(value);
    }

    @Test
    void aListBoundTwoWaysOnFirstGetsTheRowAtTheTop() throws Exception {
        SampleController c = new SampleController();
        JXElement list = agree("<ListView items=\"${rows}\" var=\"r\" type=\"" + ROW + "\" first=\"@{top}\" rows=\"5\">"
                + "<ListItem text=\"${r.name}\"/></ListView>", c);
        assertEquals(Integer.valueOf(2), list.getProps().get("first"), "read like ${top}");
        accept(list, "onChange", Integer.valueOf(7));
        assertEquals(7, c.getTop(), "and set through setTop(int)");
    }

    @Test
    void aTreeAndATableBindFirstTwoWaysToo() throws Exception {
        SampleController c = new SampleController();
        JXElement tree = agree("<Tree first=\"@{top}\"><TreeItem text=\"a\"/><TreeItem text=\"b\"/></Tree>", c);
        accept(tree, "onChange", Integer.valueOf(1));
        assertEquals(1, c.getTop());
        JXElement table = agree("<TableView items=\"${rows}\" var=\"r\" type=\"" + ROW + "\" first=\"@{top}\" columns=\"Name\">"
                + "<TableRow><TableCell text=\"${r.name}\"/></TableRow></TableView>", c);
        accept(table, "onChange", Integer.valueOf(0));
        assertEquals(0, c.getTop());
    }

    @Test
    void onDropGetsTheListOfPathsInBothWays() throws Exception {
        SampleController c = new SampleController();
        JXElement column = agree("<Column onDrop=\"#dropFiles\" cursor=\"text\"><Label text=\"x\"/></Column>", c);
        assertEquals("text", column.getProps().get("cursor"));
        accept(column, "onDrop", java.util.Arrays.asList("a.txt", "b.txt"));
        assertEquals(java.util.Arrays.asList("a.txt", "b.txt"), c.dropped);
    }

    @Test
    void aScrollTakesItsValuesAndReportsTheWheel() throws Exception {
        SampleController c = new SampleController();
        JXElement scroll = agree("<Scroll hvalue=\"0.25\" vvalue=\"${scroll}\" onScroll=\"#scrolledTo\"><Label text=\"x\"/></Scroll>", c);
        assertEquals(Double.valueOf(0.25), scroll.getProps().get("hvalue"));
        assertEquals(Double.valueOf(c.getScroll()), scroll.getProps().get("vvalue"));
        accept(scroll, "onScroll", new double[] {0, 40});
        assertArrayEquals(new double[] {0, 40}, c.scrolled);
    }
}
