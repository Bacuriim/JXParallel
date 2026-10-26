package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;

/**
 * The overlay tags (pickers with popups, menus, dialogs, drawer, snack bar) and the trees: what each becomes, which
 * handlers reach the controller, the mistakes they report, and that the interpreter and the generated code (with and
 * without hoisting) build the same tree for every one of them.
 */
class JxmlOverlaysTest {
    private static final String CTRL = "com.jxparallel.forge.samples.SampleController";
    private static final String NODE = CTRL + ".Node";

    @TempDir
    Path dir;

    private JXElement agree(String xml, Consumer<SampleController> setup) throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views(xml, "t.jxml");
        SampleController c = new SampleController();
        setup.accept(c);
        JXElement first = views.get(0).build(c);
        for (int i = 1; i < views.size(); i++) {
            assertNull(JxmlTrees.diff(first, views.get(i).build(c)), "view " + i);
        }
        return first;
    }

    private JXElement screen(String body, Consumer<SampleController> setup) throws Exception {
        return agree("<Column controller=\"" + CTRL + "\">\n" + body + "\n</Column>", setup);
    }

    private JXElement screen(String body) throws Exception {
        return screen(body, c -> { });
    }

    private static String shape(JXElement e) {
        StringBuilder s = new StringBuilder(e.getType());
        if (!e.getChildren().isEmpty()) {
            s.append('(');
            for (int i = 0; i < e.getChildren().size(); i++) {
                s.append(i == 0 ? "" : ",").append(shape(e.getChildren().get(i)));
            }
            s.append(')');
        }
        return s.toString();
    }

    @SuppressWarnings("unchecked")
    private static void accept(JXElement e, String prop, Object value) {
        ((Consumer<Object>) e.getProps().get(prop)).accept(value);
    }

    private static JxmlException error(String xml) {
        return assertThrows(JxmlException.class, () -> ViewCompiler.read(xml, "t.jxml"));
    }

    private static void assertError(int line, String message, String xml) {
        JxmlException e = error(xml);
        assertEquals(line, e.getLine(), e.getMessage());
        assertTrue(e.getMessage().startsWith("t.jxml:" + line + ": "), e.getMessage());
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    // ---- pickers ---------------------------------------------------------------------------------

    @Test
    void theComboBoxAndTheDatePickerCarryShowingAndAskThroughOnShowing() throws Exception {
        SampleController[] seen = new SampleController[1];
        JXElement column = agree("<Column controller=\"" + CTRL + "\">\n"
                + "<ComboBox items=\"${cities}\" value=\"${city}\" showing=\"${comboOpen}\" onShowing=\"#setOpen\"/>\n"
                + "<ChoiceBox items=\"A, B\" showing=\"true\"/>\n"
                + "<DatePicker value=\"${date}\" showing=\"${dateOpen}\" onShowing=\"#setOpen\"/>\n</Column>", c -> seen[0] = c);
        JXElement combo = column.getChildren().get(0);
        assertEquals("select", combo.getType());
        assertEquals(Boolean.TRUE, combo.getProps().get("showing"));
        assertEquals(Boolean.TRUE, column.getChildren().get(1).getProps().get("showing"));
        assertEquals(Boolean.FALSE, column.getChildren().get(2).getProps().get("showing"));
        accept(combo, "onShowing", Boolean.FALSE);
        accept(column.getChildren().get(2), "onShowing", Boolean.TRUE);
        assertEquals(Arrays.asList("showing false", "showing true"), seen[0].calls);
        // a combo box and a date picker are controlled: showing is false unless the file says otherwise
        assertEquals(Boolean.FALSE, screen("<ComboBox/>").getChildren().get(0).getProps().get("showing"));
    }

    @Test
    void aTimePickerHasItsFormatStepAndValueAndReportsTheNewTime() throws Exception {
        SampleController[] seen = new SampleController[1];
        JXElement column = agree("<Column controller=\"" + CTRL + "\">\n"
                + "<TimePicker value=\"${time}\" prompt=\"HH:mm\" hour24=\"false\" minuteStep=\"15\" disabled=\"${busy}\" "
                + "showing=\"${timeOpen}\" onShowing=\"#setOpen\" onChange=\"#setTime\"/>\n"
                + "<TimePicker value=\"@{time}\"/>\n</Column>", c -> {
                    seen[0] = c;
                    c.timeOpen = true;
                });
        JXElement time = column.getChildren().get(0);
        assertEquals("timepicker", time.getType());
        assertEquals("09:30", time.getProps().get("value"));
        assertEquals("HH:mm", time.getProps().get("prompt"));
        assertEquals(Boolean.FALSE, time.getProps().get("hour24"));
        assertEquals(Integer.valueOf(15), time.getProps().get("minuteStep"));
        assertEquals(Boolean.FALSE, time.getProps().get("disabled"));
        assertEquals(Boolean.TRUE, time.getProps().get("showing"));
        JXElement defaults = column.getChildren().get(1);
        assertEquals(Boolean.TRUE, defaults.getProps().get("hour24"));
        assertEquals(Integer.valueOf(5), defaults.getProps().get("minuteStep"));
        assertEquals(Boolean.FALSE, defaults.getProps().get("showing"));
        accept(time, "onChange", "14:45");
        assertEquals("14:45", seen[0].time);
        accept(defaults, "onChange", "01:05");
        assertEquals("01:05", seen[0].time, "@{time} calls setTime");
    }

    @Test
    void aColorPickerHasItsValueAndReportsTheNewColor() throws Exception {
        SampleController[] seen = new SampleController[1];
        JXElement column = agree("<Column controller=\"" + CTRL + "\">\n"
                + "<ColorPicker value=\"${accent}\" prompt=\"none\" disabled=\"true\" showing=\"${colorOpen}\" onShowing=\"#setOpen\" "
                + "onChange=\"#setAccent\"/>\n<ColorPicker value=\"@{accent}\"/>\n</Column>", c -> seen[0] = c);
        JXElement color = column.getChildren().get(0);
        assertEquals("colorpicker", color.getType());
        assertEquals("#E91E63", color.getProps().get("value"));
        assertEquals(Boolean.TRUE, color.getProps().get("disabled"));
        assertEquals(Boolean.FALSE, color.getProps().get("showing"));
        accept(color, "onChange", "#112233");
        accept(column.getChildren().get(1), "onChange", "#445566");
        assertEquals("#445566", seen[0].accent);
    }

    // ---- menus -----------------------------------------------------------------------------------

    @Test
    void aMenuBarIsMenusOfItemsSeparatorsAndSubmenusWithLoopsAndConditionsBuiltInPlace() throws Exception {
        String xml = "<MenuBar controller=\"" + CTRL + "\">\n"
                + "<Menu text=\"File\"><MenuItem text=\"New\" accelerator=\"Ctrl+N\" onAction=\"#save\"/><MenuSeparator/>"
                + "<MenuItem text=\"Locked\" disabled=\"true\"/><Menu text=\"Export\"><MenuItem text=\"PDF\"/></Menu></Menu>\n"
                + "<Menu text=\"Cities\"><For items=\"${cities}\" var=\"c\"><MenuItem text=\"${c}\"/></For>"
                + "<If test=\"${on}\"><MenuItem text=\"On\"/></If><Else><MenuItem text=\"Off\"/></Else></Menu>\n</MenuBar>";
        JXElement bar = agree(xml, c -> { });
        assertEquals("menubar(menu(menuitem,menuitem,menuitem,menu(menuitem)),menu(menuitem,menuitem,menuitem))", shape(bar));
        JXElement file = bar.getChildren().get(0);
        assertEquals("File", file.getProps().get("label"));
        JXElement item = file.getChildren().get(0);
        assertEquals("New", item.getProps().get("label"));
        assertEquals("Ctrl+N", item.getProps().get("accelerator"));
        assertTrue(item.getProps().get("onAction") instanceof Runnable);
        assertNull(item.getProps().get("separator"));
        assertEquals(Boolean.TRUE, file.getChildren().get(1).getProps().get("separator"));
        assertEquals(Boolean.TRUE, file.getChildren().get(2).getProps().get("disabled"));
        assertEquals("On", bar.getChildren().get(1).getChildren().get(2).getProps().get("label"));
        JXElement off = agree(xml, c -> c.on = false);
        assertEquals("Off", off.getChildren().get(1).getChildren().get(2).getProps().get("label"));
        List<String> cities = new ArrayList<String>();
        for (int i = 0; i < 2; i++) {
            cities.add(String.valueOf(bar.getChildren().get(1).getChildren().get(i).getProps().get("label")));
        }
        assertEquals(Arrays.asList("Fortaleza", "Recife"), cities);
        SampleController c = new SampleController();
        ((Runnable) ViewCompiler.read(xml, "t.jxml").build(c).getChildren().get(0).getChildren().get(0).getProps().get("onAction")).run();
        assertEquals(Arrays.asList("save"), c.calls);
    }

    @Test
    void aMenuButtonAndAContextMenuHoldItemsAndAskThroughOnShowing() throws Exception {
        SampleController[] seen = new SampleController[1];
        JXElement column = agree("<Column controller=\"" + CTRL + "\">\n"
                + "<MenuButton text=\"${title}\" disabled=\"${busy}\" showing=\"${menuOpen}\" onShowing=\"#setOpen\">"
                + "<MenuItem text=\"A\"/><Menu text=\"B\"><MenuItem text=\"C\"/></Menu></MenuButton>\n"
                + "<MenuButton text=\"Own\"><MenuItem text=\"D\"/></MenuButton>\n"
                + "<Stack><ContextMenu disabled=\"false\" showing=\"${contextOpen}\" onShowing=\"#setOpen\"><MenuItem text=\"E\"/>"
                + "</ContextMenu></Stack>\n</Column>", c -> {
                    seen[0] = c;
                    c.contextOpen = false;
                });
        JXElement button = column.getChildren().get(0);
        assertEquals("menubutton(menuitem,menu(menuitem))", shape(button));
        assertEquals("Sample", button.getProps().get("label"));
        assertEquals(Boolean.TRUE, button.getProps().get("showing"));
        assertNull(column.getChildren().get(1).getProps().get("showing"), "no showing: the renderer keeps the state");
        JXElement context = column.getChildren().get(2).getChildren().get(0);
        assertEquals("contextmenu", context.getType());
        assertEquals(Boolean.FALSE, context.getProps().get("showing"));
        accept(button, "onShowing", Boolean.FALSE);
        accept(context, "onShowing", Boolean.TRUE);
        assertEquals(Arrays.asList("showing false", "showing true"), seen[0].calls);
    }

    // ---- dialogs, drawer, snack bar --------------------------------------------------------------

    @Test
    void aDialogIsATitleContentAndButtonsInAModal() throws Exception {
        SampleController[] seen = new SampleController[1];
        JXElement stack = agree("<Stack controller=\"" + CTRL + "\">\n"
                + "<Dialog title=\"${title}\" showing=\"${dialogOpen}\" onShowing=\"#setOpen\" dismissible=\"false\" prefWidth=\"400\">"
                + "<Label text=\"body\"/><Button slot=\"actions\" text=\"Cancel\"/>"
                + "<Button slot=\"actions\" text=\"OK\" defaultButton=\"true\" onAction=\"#save\"/><Label text=\"more\"/></Dialog>\n"
                + "<Dialog showing=\"false\"><Label text=\"only content\"/></Dialog>\n</Stack>", c -> seen[0] = c);
        JXElement dialog = stack.getChildren().get(0);
        assertEquals("dialog(#text,#text,#text,buttonbar(button,button))", shape(dialog));
        assertEquals(Boolean.TRUE, dialog.getProps().get("showing"));
        assertEquals(Boolean.FALSE, dialog.getProps().get("dismissible"));
        assertEquals(Double.valueOf(24), dialog.getProps().get("padding"));
        assertEquals(Double.valueOf(16), dialog.getProps().get("gap"));
        assertEquals("TOP_LEFT", dialog.getProps().get("alignment"));
        assertEquals(Double.valueOf(280), dialog.getProps().get("minWidth"));
        assertEquals(Double.valueOf(400), dialog.getProps().get("prefWidth"), "layout attributes go to the modal");
        JXElement title = dialog.getChildren().get(0);
        assertEquals("Sample", title.getProps().get("value"));
        assertEquals(Boolean.TRUE, title.getProps().get("bold"));
        assertEquals(Double.valueOf(18), title.getProps().get("fontSize"));
        assertEquals("body", dialog.getChildren().get(1).getProps().get("value"));
        assertEquals("more", dialog.getChildren().get(2).getProps().get("value"), "content stays in order, actions go last");
        JXElement buttons = dialog.getChildren().get(3);
        assertNull(buttons.getChildren().get(0).getProps().get("position"), "the slot only chose the place");
        assertEquals(Boolean.TRUE, buttons.getChildren().get(1).getProps().get("defaultButton"));
        ((Runnable) buttons.getChildren().get(1).getProps().get("onAction")).run();
        accept(dialog, "onShowing", Boolean.FALSE);
        assertEquals(Arrays.asList("save", "showing false"), seen[0].calls);
        assertEquals("dialog(#text)", shape(stack.getChildren().get(1)));
        assertEquals(Boolean.TRUE, stack.getChildren().get(1).getProps().get("dismissible"));
    }

    @Test
    void anAlertDialogIsATitleAMessageAndTheButtonsOfItsHandlers() throws Exception {
        SampleController[] seen = new SampleController[1];
        JXElement stack = agree("<Stack controller=\"" + CTRL + "\">\n"
                + "<AlertDialog title=\"Careful\" message=\"${name}\" confirmText=\"Do it\" cancelText=\"Stop\" onConfirm=\"#save\" "
                + "onCancel=\"#save\" showing=\"true\" dismissible=\"false\"/>\n<AlertDialog message=\"Plain\"/>\n</Stack>", c -> seen[0] = c);
        JXElement alert = stack.getChildren().get(0);
        assertEquals("dialog(#text,#text,buttonbar(button,button))", shape(alert));
        assertEquals("Careful", alert.getChildren().get(0).getProps().get("value"));
        JXElement message = alert.getChildren().get(1);
        assertEquals("Ada", message.getProps().get("value"));
        assertEquals(Boolean.TRUE, message.getProps().get("wrapText"));
        JXElement bar = alert.getChildren().get(2);
        assertEquals("Stop", bar.getChildren().get(0).getProps().get("label"));
        assertNull(bar.getChildren().get(0).getProps().get("defaultButton"));
        assertEquals("Do it", bar.getChildren().get(1).getProps().get("label"));
        assertEquals(Boolean.TRUE, bar.getChildren().get(1).getProps().get("defaultButton"));
        ((Runnable) bar.getChildren().get(0).getProps().get("onAction")).run();
        ((Runnable) bar.getChildren().get(1).getProps().get("onAction")).run();
        assertEquals(Arrays.asList("save", "save"), seen[0].calls);
        JXElement plain = stack.getChildren().get(1);
        assertEquals("dialog(#text,buttonbar(button))", shape(plain));
        assertEquals("OK", plain.getChildren().get(1).getChildren().get(0).getProps().get("label"));
        assertNull(plain.getChildren().get(1).getChildren().get(0).getProps().get("onAction"));
    }

    @Test
    void aDrawerIsAColumnAtOneSideAndASnackBarAMessageWithAnActionAndADuration() throws Exception {
        SampleController[] seen = new SampleController[1];
        JXElement stack = agree("<Stack controller=\"" + CTRL + "\">\n"
                + "<Drawer showing=\"${drawerOpen}\" side=\"right\" padding=\"4 8\" gap=\"3\" onShowing=\"#setOpen\" prefWidth=\"300\">"
                + "<Button text=\"Home\"/><Label text=\"x\"/></Drawer>\n<Drawer><Label/></Drawer>\n"
                + "<SnackBar message=\"${title}\" action=\"Undo\" onAction=\"#save\" duration=\"${alpha}\" showing=\"${snackOpen}\" "
                + "onShowing=\"#setOpen\"/>\n<SnackBar message=\"m\" duration=\"2500\"/>\n</Stack>", c -> seen[0] = c);
        JXElement drawer = stack.getChildren().get(0);
        assertEquals("drawer(button,#text)", shape(drawer));
        assertEquals("right", drawer.getProps().get("side"));
        assertEquals(Boolean.TRUE, drawer.getProps().get("showing"));
        assertArrayEquals(new double[] {4, 8, 4, 8}, (double[]) drawer.getProps().get("padding"), 0);
        assertEquals(Double.valueOf(3), drawer.getProps().get("gap"));
        assertEquals("left", stack.getChildren().get(1).getProps().get("side"));
        assertEquals(Boolean.FALSE, stack.getChildren().get(1).getProps().get("showing"));
        JXElement snack = stack.getChildren().get(2);
        assertEquals("snackbar", snack.getType());
        assertEquals("Sample", snack.getProps().get("message"));
        assertEquals("Undo", snack.getProps().get("action"));
        assertEquals(Double.valueOf(0.5), snack.getProps().get("duration"));
        ((Runnable) snack.getProps().get("onAction")).run();
        accept(snack, "onShowing", Boolean.FALSE);
        assertEquals(Arrays.asList("save", "showing false"), seen[0].calls);
        assertEquals(Double.valueOf(2500), stack.getChildren().get(3).getProps().get("duration"));
        assertNull(stack.getChildren().get(3).getProps().get("action"));
    }

    // ---- trees -----------------------------------------------------------------------------------

    private static final String SMALL_TREE = "<Tree rowHeight=\"20\" first=\"${top}\" rows=\"${limit}\">\n"
            + "<TreeItem text=\"a\" expanded=\"true\" key=\"ka\"><TreeItem text=\"a1\" key=\"ka1\"/>"
            + "<TreeItem text=\"a2\" expanded=\"${open}\" key=\"ka2\"><TreeItem text=\"a2x\" key=\"ka2x\"/></TreeItem></TreeItem>\n"
            + "<TreeItem text=\"b\" key=\"kb\"><TreeItem text=\"hidden\"/></TreeItem>\n<TreeItem text=\"c\" key=\"kc\"/>\n</Tree>";

    private static List<String> texts(JXElement list) {
        List<String> out = new ArrayList<String>();
        for (JXElement row : list.getChildren()) {
            out.add(String.valueOf(row.getProps().get("value")));
        }
        return out;
    }

    @Test
    void aTreeIsTheVisibleItemsInOrderWithTheirDepthAndArrowAsRowsOfAList() throws Exception {
        JXElement tree = screen(SMALL_TREE, c -> { c.top = 0; c.limit = 10; }).getChildren().get(0);
        assertEquals("list", tree.getType());
        assertEquals(Arrays.asList("a", "a1", "a2", "a2x", "b", "c"), texts(tree));
        assertEquals(Integer.valueOf(6), tree.getProps().get("itemCount"));
        assertEquals(Double.valueOf(20), tree.getProps().get("cellHeight"));
        assertEquals(Integer.valueOf(0), tree.getProps().get("first"));
        assertEquals(Double.valueOf(0), tree.getProps().get("scrollY"));
        String[] disclosure = {"expanded", "leaf", "expanded", "leaf", "collapsed", "leaf"};
        double[] indent = {0, 1, 1, 2, 0, 0};
        for (int i = 0; i < 6; i++) {
            JXElement row = tree.getChildren().get(i);
            assertEquals("cell", row.getType());
            assertEquals(disclosure[i], row.getProps().get("disclosure"), "row " + i);
            assertEquals(Double.valueOf(indent[i]), row.getProps().get("indent"), "row " + i);
            assertEquals(Boolean.valueOf(i % 2 == 1), row.getProps().get("odd"));
            assertFalse(row.getProps().asMap().containsKey("expanded"), "what only the model has does not reach the renderer");
            assertTrue(row.getChildren().isEmpty());
        }
        assertEquals("ka2x", tree.getChildren().get(3).getProps().get("key"));
        JXElement collapsed = screen(SMALL_TREE, c -> { c.top = 0; c.limit = 10; c.open = false; }).getChildren().get(0);
        assertEquals(Arrays.asList("a", "a1", "a2", "b", "c"), texts(collapsed), "a2 collapsed hides a2x");
        assertEquals("collapsed", collapsed.getChildren().get(2).getProps().get("disclosure"));
    }

    @Test
    void aTreeWindowStartsAtFirstHasRowsRowsAndTheScrollOffsetOfTheWholeModel() throws Exception {
        JXElement tree = screen(SMALL_TREE, c -> { c.top = 2; c.limit = 3; }).getChildren().get(0);
        assertEquals(Arrays.asList("a2", "a2x", "b"), texts(tree));
        assertEquals(Integer.valueOf(6), tree.getProps().get("itemCount"));
        assertEquals(Integer.valueOf(2), tree.getProps().get("first"));
        assertEquals(Double.valueOf(40), tree.getProps().get("scrollY"));
        assertEquals(Double.valueOf(1), tree.getChildren().get(0).getProps().get("indent"));
        assertEquals(Boolean.FALSE, tree.getChildren().get(0).getProps().get("odd"), "odd follows the place in the whole tree");
        JXElement past = screen(SMALL_TREE, c -> { c.top = 50; c.limit = 3; }).getChildren().get(0);
        assertEquals(Integer.valueOf(5), past.getProps().get("first"), "kept within the items");
        assertEquals(Arrays.asList("c"), texts(past));
        JXElement none = screen(SMALL_TREE, c -> { c.top = 0; c.limit = 0; }).getChildren().get(0);
        assertEquals(0, none.getChildren().size());
        assertEquals(Integer.valueOf(6), none.getProps().get("itemCount"));
        JXElement standard = screen("<Tree>\n<TreeItem text=\"x\"/>\n</Tree>").getChildren().get(0);
        assertEquals(Double.valueOf(24), standard.getProps().get("cellHeight"));
        assertEquals(1, standard.getChildren().size());
        assertFalse(standard.getProps().asMap().containsKey("rows"));
    }

    @Test
    void rowsHandlersReachTheControllerWithTheirLoopItem() throws Exception {
        String xml = "<Tree controller=\"" + CTRL + "\" rows=\"20\">\n"
                + "<For items=\"${folders}\" var=\"f\" type=\"" + NODE + "\" key=\"${f.id}\">"
                + "<TreeItem text=\"${f.name}\" expanded=\"${f.expanded}\" onAction=\"#pick(f)\" onToggle=\"#toggle(f)\">"
                + "<For items=\"${f.children}\" var=\"c\" type=\"" + NODE + "\"><TreeItem text=\"${c.name}\" onAction=\"#pick(c)\"/></For>"
                + "</TreeItem></For>\n</Tree>";
        List<JxmlView<Object>> views = new ViewCompiler(dir).views(xml, "t.jxml");
        for (JxmlView<Object> view : views) {
            SampleController c = new SampleController();
            JXElement tree = view.build(c);
            // src is expanded: src, main (collapsed), test, docs (collapsed), README.md
            assertEquals(Arrays.asList("src", "main", "test", "docs", "README.md"), texts(tree));
            assertEquals("expanded", tree.getChildren().get(0).getProps().get("disclosure"));
            assertEquals("leaf", tree.getChildren().get(1).getProps().get("disclosure"));
            assertEquals("collapsed", tree.getChildren().get(3).getProps().get("disclosure"), "docs has a child but is not expanded");
            ((Runnable) tree.getChildren().get(0).getProps().get("onToggle")).run();
            ((Runnable) tree.getChildren().get(3).getProps().get("onAction")).run();
            ((Runnable) tree.getChildren().get(2).getProps().get("onAction")).run();
            assertEquals(Arrays.asList("toggle src", "pick docs", "pick test"), c.calls);
            assertEquals(Integer.valueOf(10), tree.getChildren().get(0).getProps().get("key"), "the item's key, the same instance each build");
            assertSame(tree.getChildren().get(0).getProps().get("key"), view.build(c).getChildren().get(0).getProps().get("key"));
        }
    }

    @Test
    void aTreeTableHasColumnsAndRowsOfCellsWithTheTreeInTheFirst() throws Exception {
        String xml = "<TreeTable rowHeight=\"30\" first=\"1\" rows=\"3\" columns=\"Name, Size\" columnWidths=\"-1, 80\" constrained=\"true\">\n"
                + "<TreeItem text=\"docs\" expanded=\"true\" values=\"12 KB\" key=\"d\" onAction=\"#save\" onToggle=\"#save\">"
                + "<TreeItem text=\"guide\" values=\"4 KB, x\" selected=\"true\" listFocused=\"true\"/></TreeItem>\n"
                + "<TreeItem text=\"top\" values=\"1 KB\"/>\n<TreeItem text=\"last\"/>\n</TreeTable>";
        JXElement table = agree("<Column controller=\"" + CTRL + "\">" + xml + "</Column>", c -> { }).getChildren().get(0);
        assertEquals("table", table.getType());
        assertArrayEquals(new String[] {"Name", "Size"}, (String[]) table.getProps().get("columns"));
        assertArrayEquals(new double[] {-1, 80}, (double[]) table.getProps().get("columnWidths"), 0);
        assertEquals(Boolean.TRUE, table.getProps().get("constrained"));
        assertEquals(Integer.valueOf(4), table.getProps().get("itemCount"));
        assertEquals(Integer.valueOf(1), table.getProps().get("first"));
        assertEquals(Double.valueOf(30), table.getProps().get("rowHeight"));
        assertEquals(Double.valueOf(30), table.getProps().get("scrollY"));
        assertEquals(3, table.getChildren().size());
        JXElement guide = table.getChildren().get(0);
        assertEquals("tablerow", guide.getType());
        assertEquals(Boolean.TRUE, guide.getProps().get("selected"));
        assertEquals(Boolean.TRUE, guide.getProps().get("listFocused"));
        assertEquals(3, guide.getChildren().size(), "the tree cell, then one for each value");
        JXElement first = guide.getChildren().get(0);
        assertEquals("guide", first.getProps().get("value"));
        assertEquals(Double.valueOf(1), first.getProps().get("indent"));
        assertEquals("leaf", first.getProps().get("disclosure"));
        assertEquals(Boolean.TRUE, first.getProps().get("tableCell"));
        assertEquals(Boolean.FALSE, first.getProps().get("paintBackground"));
        assertEquals("4 KB", guide.getChildren().get(1).getProps().get("value"));
        assertEquals("x", guide.getChildren().get(2).getProps().get("value"));
        assertNull(guide.getChildren().get(1).getProps().get("indent"), "only the first cell is a tree cell");
        JXElement top = table.getChildren().get(1);
        assertEquals(2, top.getChildren().size());
        assertEquals(1, table.getChildren().get(2).getChildren().size(), "no values: the tree cell only");
        JXElement all = agree("<Column controller=\"" + CTRL + "\">" + xml.replace("first=\"1\" ", "") + "</Column>", c -> { })
                .getChildren().get(0);
        JXElement docs = all.getChildren().get(0);
        assertEquals("d", docs.getProps().get("key"));
        assertEquals("expanded", docs.getChildren().get(0).getProps().get("disclosure"));
        assertTrue(docs.getChildren().get(0).getProps().get("onToggle") instanceof Runnable);
        assertTrue(docs.getChildren().get(1).getProps().get("onAction") instanceof Runnable, "every cell of the row selects it");
        assertNull(docs.getChildren().get(1).getProps().get("onToggle"));
    }

    // ---- mistakes --------------------------------------------------------------------------------

    @Test
    void menuAndTreeTagsOnlyStandWhereTheyMean() {
        assertError(2, "<MenuItem> is only allowed inside <MenuButton>, <ContextMenu> or <Menu>", "<Column>\n<MenuItem text=\"x\"/></Column>");
        assertError(2, "<MenuItem> is only allowed inside", "<Column>\n<Row><MenuItem/></Row></Column>");
        assertError(2, "<MenuItem> is only allowed inside", "<Column>\n<MenuBar><MenuItem/></MenuBar></Column>");
        assertError(2, "<Menu> is only allowed inside <MenuBar>, <MenuButton>, <ContextMenu> or <Menu>", "<Column>\n<Menu text=\"x\"/></Column>");
        assertError(2, "<TreeItem> is only allowed inside <Tree>, <TreeTable> or <TreeItem>", "<Column>\n<TreeItem text=\"x\"/></Column>");
        assertError(2, "<TreeItem> is only allowed inside", "<Column>\n<Stack><TreeItem/></Stack></Column>");
        assertError(2, "<MenuItem> is only allowed inside", "<Column>\n<MenuSeparator/></Column>");
        assertError(3, "<MenuBar> takes only <Menu> as children, not <Label>", "<Column>\n<MenuBar>\n<Label/></MenuBar></Column>");
        assertError(2, "<MenuButton> takes only <MenuItem> or <Menu> as children, not <Label>",
                "<Column>\n<MenuButton text=\"m\"><Label/></MenuButton></Column>");
        assertError(2, "<ContextMenu> takes only <MenuItem> or <Menu> as children, not <Button>",
                "<Column>\n<Stack><ContextMenu><Button/></ContextMenu></Stack></Column>");
        assertError(2, "<Menu> takes only <MenuItem> or <Menu> as children, not <Tree>",
                "<Column>\n<MenuBar><Menu><Tree/></Menu></MenuBar></Column>");
        assertError(2, "<Tree> takes only <TreeItem> as children, not <Label>", "<Column>\n<Tree><Label/></Tree></Column>");
        assertError(2, "<TreeTable> takes only <TreeItem> as children, not <TableRow>", "<Column>\n<TreeTable><TableRow/></TreeTable></Column>");
        assertError(3, "<TreeItem> takes only <TreeItem> as children, not <Button>",
                "<Column>\n<Tree><TreeItem>\n<Button/></TreeItem></Tree></Column>");
        // loops and conditions are looked through, so a wrong tag inside them is still found
        assertError(3, "<Menu> takes only <MenuItem> or <Menu> as children, not <Label>",
                "<Column controller=\"" + CTRL + "\">\n<MenuBar><Menu><If test=\"${on}\">\n<Label/></If></Menu></MenuBar></Column>");
        assertError(3, "takes only <TreeItem>", "<Column controller=\"" + CTRL + "\">\n<Tree><For items=\"${rows}\" var=\"r\">\n<Label/></For></Tree></Column>");
    }

    @Test
    void overlayTagsReportTheirOwnMistakes() {
        assertError(2, "side=\"up\" is not one of [left, right]", "<Column>\n<Drawer side=\"up\"/></Column>");
        assertError(2, "is not a valid boolean", "<Column>\n<Dialog showing=\"yes\"/></Column>");
        assertError(2, "onShowing must name a controller method", "<Column>\n<Modal onShowing=\"save\"/></Column>");
        assertError(2, "onConfirm must name a controller method", "<Column>\n<AlertDialog onConfirm=\"save\"/></Column>");
        assertError(2, "onCancel must name a controller method", "<Column>\n<AlertDialog onCancel=\"save\"/></Column>");
        assertError(2, "<AlertDialog> takes no children", "<Column>\n<AlertDialog><Label/></AlertDialog></Column>");
        assertError(2, "<SnackBar> takes no children", "<Column>\n<SnackBar><Label/></SnackBar></Column>");
        assertError(2, "<TimePicker> takes no children", "<Column>\n<TimePicker><Label/></TimePicker></Column>");
        assertError(2, "is not a valid int", "<Column>\n<TimePicker minuteStep=\"often\"/></Column>");
        assertError(2, "is not a valid double", "<Column>\n<SnackBar duration=\"long\"/></Column>");
        assertError(2, "has no attribute title", "<Column>\n<Modal title=\"t\"/></Column>");
        assertError(2, "has no attribute confirmText", "<Column>\n<Dialog confirmText=\"t\"/></Column>");
        assertError(2, "showing cannot be bound two ways with @{...}", "<Column>\n<Dialog showing=\"@{dialogOpen}\"/></Column>");
        assertError(2, "rowHeight must be greater than 0", "<Column>\n<Tree rowHeight=\"0\"/></Column>");
        assertError(2, "rowHeight must be greater than 0", "<Column>\n<TreeTable rowHeight=\"-2\"/></Column>");
        assertError(2, "is not a valid doubles", "<Column>\n<TreeTable columnWidths=\"a, b\"/></Column>");
        assertError(2, "cannot be bound", "<Column controller=\"" + CTRL + "\">\n<Drawer side=\"${title}\"/></Column>");
    }

    @Test
    void aTreeItemWithValuesInAPlainTreeIsReportedAtItsLineAndABoundRowHeightAtBuild() throws Exception {
        assertError(3, "a <TreeItem> has values only in a <TreeTable>", "<Column>\n<Tree>\n<TreeItem text=\"x\" values=\"1 KB\"/></Tree></Column>");
        assertError(4, "has values only in a <TreeTable>",
                "<Column>\n<Tree>\n<TreeItem text=\"x\">\n<TreeItem values=\"1\"/></TreeItem></Tree></Column>");
        assertError(4, "has values only in a <TreeTable>", "<Column controller=\"" + CTRL
                + "\">\n<Tree>\n<If test=\"${on}\">\n<TreeItem values=\"${cities}\"/></If></Tree></Column>");
        String height = "<Column controller=\"" + CTRL + "\">\n<Tree rowHeight=\"${alpha}\"><TreeItem/></Tree></Column>";
        JxmlTemplate bound = ViewCompiler.read(height, "t.jxml");
        SampleController flat = new SampleController();
        flat.alpha = 0;
        JxmlException h = assertThrows(JxmlException.class, () -> bound.build(flat));
        assertTrue(h.getMessage().startsWith("t.jxml:2: ") && h.getMessage().contains("rowHeight must be greater than 0"), h.getMessage());
    }
}
