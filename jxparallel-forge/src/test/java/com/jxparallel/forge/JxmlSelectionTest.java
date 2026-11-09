package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;

/**
 * The selection of lists, tables and trees in a file: {@code selected="@{...}"} (the row, an int) and
 * {@code selection="@{...}"} (the rows, in multiple mode, a double[]) report through {@code onSelect} and
 * {@code onSelection}; the interpreter and the generated code agree.
 */
class JxmlSelectionTest {
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

    private static final String LIST = "<ListView items=\"${rows}\" var=\"r\" type=\"" + ROW + "\" rows=\"5\" ";
    private static final String TABLE = "<TableView items=\"${rows}\" var=\"r\" type=\"" + ROW + "\" columns=\"Name\" ";
    private static final String TABLE_ROW = "<TableRow><TableCell text=\"${r.name}\"/></TableRow></TableView>";

    @Test
    void aListBoundTwoWaysOnSelectedReadsTheRowAndReportsTheOneSelected() throws Exception {
        SampleController c = new SampleController();
        JXElement list = agree(LIST + "selected=\"@{selected}\"><ListItem text=\"${r.name}\"/></ListView>", c);
        assertEquals(Integer.valueOf(4), list.getProps().get("selected"), "read like ${selected}");
        accept(list, "onSelect", Integer.valueOf(9));
        assertEquals(9, c.getSelected(), "and set through setSelected(int)");
    }

    @Test
    void aMultipleSelectionIsABoundArrayOfRows() throws Exception {
        SampleController c = new SampleController();
        JXElement list = agree(LIST + "selectionMode=\"multiple\" selection=\"@{picked}\" onSelect=\"#setSelected\">"
                + "<ListItem text=\"${r.name}\"/></ListView>", c);
        assertEquals("multiple", list.getProps().get("selectionMode"));
        assertArrayEquals(new double[] {1, 3}, (double[]) list.getProps().get("selection"));
        accept(list, "onSelection", new double[] {2, 5, 6});
        assertArrayEquals(new double[] {2, 5, 6}, c.getPicked());
        accept(list, "onSelect", Integer.valueOf(6));
        assertEquals(6, c.getSelected(), "onSelect still hears of the row last picked");
    }

    @Test
    void aTableATreeAndATreeTableBindTheSelectionToo() throws Exception {
        SampleController c = new SampleController();
        JXElement table = agree(TABLE + "selected=\"@{selected}\">" + TABLE_ROW, c);
        accept(table, "onSelect", Integer.valueOf(1));
        assertEquals(1, c.getSelected());
        JXElement tree = agree("<Tree selection=\"@{picked}\" selectionMode=\"multiple\"><TreeItem text=\"a\"/><TreeItem text=\"b\"/></Tree>", c);
        accept(tree, "onSelection", new double[] {0, 1});
        assertArrayEquals(new double[] {0, 1}, c.getPicked());
        JXElement treeTable = agree("<TreeTable columns=\"Name\" selected=\"@{selected}\"><TreeItem text=\"a\"/></TreeTable>", c);
        accept(treeTable, "onSelect", Integer.valueOf(0));
        assertEquals(0, c.getSelected());
    }

    @Test
    void theSelectionAndFirstAreBoundTwoWaysOnTheSameList() throws Exception {
        SampleController c = new SampleController();
        JXElement list = agree(LIST + "first=\"@{top}\" selected=\"@{selected}\" selection=\"@{picked}\">"
                + "<ListItem text=\"${r.name}\"/></ListView>", c);
        accept(list, "onChange", Integer.valueOf(3));
        accept(list, "onSelect", Integer.valueOf(8));
        accept(list, "onSelection", new double[] {8});
        assertEquals(3, c.getTop());
        assertEquals(8, c.getSelected());
        assertArrayEquals(new double[] {8}, c.getPicked());
    }

    @Test
    void theMistakesAreReportedWithTheirLine() {
        JxmlException twice = assertThrows(JxmlException.class, () -> ViewCompiler.read("<Column>\n" + LIST
                + "selected=\"@{selected}\" onSelect=\"#setSelected\"><ListItem/></ListView></Column>", "t.jxml"));
        assertEquals(2, twice.getLine());
        assertTrue(twice.getMessage().contains("has both onSelect and a @{...} binding"), twice.getMessage());
        JxmlException mode = assertThrows(JxmlException.class, () -> ViewCompiler.read("<Column>\n" + LIST
                + "selectionMode=\"several\"><ListItem/></ListView></Column>", "t.jxml"));
        assertEquals(2, mode.getLine());
        assertTrue(mode.getMessage().contains("[single, multiple]"), mode.getMessage());
        JxmlException other = assertThrows(JxmlException.class, () -> ViewCompiler.read("<Column>\n"
                + "<ListView items=\"${rows}\" var=\"r\" selectionMode=\"@{top}\"><ListItem/></ListView></Column>", "t.jxml"));
        assertTrue(other.getMessage().contains("cannot be bound two ways"), other.getMessage());
        assertTrue(other.getMessage().contains("first or selected or selection"), other.getMessage());
    }
}
