package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;

/**
 * The Material-style tags, styles, themes and includes: what each builds (the same in the interpreter and in the
 * generated code, with hoisting on and off), the handlers they call and the mistakes they report.
 */
class JxmlMaterialTest {
    private static final String CTRL = "com.jxparallel.forge.samples.SampleController";

    @TempDir
    Path dir;

    /** Builds {@code body} in a column with every way of reading it and returns the first child of the first tree. */
    private JXElement one(String body) throws Exception {
        return build("<Column controller=\"" + CTRL + "\">\n" + body + "\n</Column>", new SampleController()).getChildren().get(0);
    }

    private JXElement build(String xml, SampleController c) throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views(xml, "t.jxml");
        return agree(views, c);
    }

    private static JXElement agree(List<JxmlView<Object>> views, SampleController c) {
        JXElement first = views.get(0).build(c);
        for (int i = 1; i < views.size(); i++) {
            assertNull(JxmlTrees.diff(first, views.get(i).build(c)), "view " + i);
        }
        return first;
    }

    @SuppressWarnings("unchecked")
    private static void accept(JXElement e, String prop, Object value) {
        ((Consumer<Object>) e.getProps().get(prop)).accept(value);
    }

    private static JxmlException error(String xml) {
        return assertThrows(JxmlException.class, () -> ViewCompiler.read(xml, "t.jxml"));
    }

    private static void assertError(String source, int line, String message, JxmlException e) {
        assertEquals(line, e.getLine(), e.getMessage());
        assertTrue(e.getMessage().startsWith(source + ":" + line + ": "), e.getMessage());
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    private static void assertError(int line, String message, String xml) {
        assertError("t.jxml", line, message, error(xml));
    }

    // ---- Switch, Chip, ScrollBar, SplitPane, TabBar: values and handlers -------------------------------

    @Test
    void aSwitchReportsABooleanLikeACheckBox() throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views("<Column controller=\"" + CTRL + "\">\n"
                + "<Switch text=\"A\" selected=\"@{agree}\"/><Switch selected=\"${on}\" onChange=\"#setAgree\" disabled=\"true\"/></Column>", "t.jxml");
        for (JxmlView<Object> view : views) {
            SampleController c = new SampleController();
            JXElement sw = view.build(c).getChildren().get(0);
            assertEquals("switch", sw.getType());
            assertEquals("A", sw.getProps().get("label"));
            assertEquals(Boolean.FALSE, sw.getProps().get("checked"));
            accept(sw, "onChange", Boolean.TRUE);
            assertTrue(c.agree);
            JXElement second = view.build(c).getChildren().get(1);
            assertEquals(Boolean.TRUE, second.getProps().get("checked"));
            assertEquals(Boolean.TRUE, second.getProps().get("disabled"));
            accept(second, "onChange", Boolean.FALSE);
            assertFalse(c.agree);
        }
    }

    @Test
    void aChipHasItsKindIconSelectionAndTwoHandlers() throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views("<Column controller=\"" + CTRL + "\">\n"
                + "<Chip text=\"T\" kind=\"filter\" icon=\"star\" selected=\"@{agree}\" closable=\"true\" onClose=\"#save\" onAction=\"#save\"/>"
                + "<Chip/></Column>", "t.jxml");
        for (JxmlView<Object> view : views) {
            SampleController c = new SampleController();
            JXElement chip = view.build(c).getChildren().get(0);
            assertEquals("chip", chip.getType());
            assertEquals("filter", chip.getProps().get("kind"));
            assertEquals("star", chip.getProps().get("icon"));
            assertEquals(Boolean.TRUE, chip.getProps().get("closable"));
            ((Runnable) chip.getProps().get("onClose")).run();
            ((Runnable) chip.getProps().get("onAction")).run();
            assertEquals(Arrays.asList("save", "save"), c.calls);
            accept(chip, "onChange", Boolean.TRUE);
            assertTrue(c.agree);
            JXElement bare = view.build(c).getChildren().get(1);
            assertEquals("assist", bare.getProps().get("kind"));
            assertEquals(Boolean.FALSE, bare.getProps().get("selected"));
            assertEquals("", bare.getProps().get("label"));
        }
    }

    @Test
    void aScrollBarAndASplitPaneReportADoubleAndAnArrayOfPositions() throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views("<Column controller=\"" + CTRL + "\">\n"
                + "<ScrollBar value=\"@{scroll}\" min=\"1\" max=\"9\" visibleAmount=\"2\" orientation=\"vertical\"/>"
                + "<SplitPane dividerPositions=\"@{positions}\"><Label/><Label/><Label/></SplitPane></Column>", "t.jxml");
        for (JxmlView<Object> view : views) {
            SampleController c = new SampleController();
            JXElement column = view.build(c);
            JXElement bar = column.getChildren().get(0);
            assertEquals("scrollbar", bar.getType());
            assertEquals(Double.valueOf(20), bar.getProps().get("value"));
            assertEquals(Double.valueOf(1), bar.getProps().get("min"));
            assertEquals(Double.valueOf(2), bar.getProps().get("visibleAmount"));
            assertEquals("vertical", bar.getProps().get("orientation"));
            accept(bar, "onChange", Double.valueOf(4.5));
            assertEquals(4.5, c.scroll, 0);
            JXElement split = column.getChildren().get(1);
            assertEquals("splitpane", split.getType());
            assertArrayEquals(new double[] {0.3, 0.7}, (double[]) split.getProps().get("dividerPositions"), 0);
            assertEquals(3, split.getChildren().size());
            accept(split, "onChange", new double[] {0.1, 0.2});
            assertArrayEquals(new double[] {0.1, 0.2}, c.positions, 0);
            c.positions = new double[] {0.3, 0.7};
        }
    }

    @Test
    void aTabBarReportsTheSelectedIndexAndTheClosedOne() throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views("<Column controller=\"" + CTRL + "\">\n"
                + "<TabBar titles=\"A, B\" selected=\"@{tabIndex}\" closable=\"true\" onClose=\"#closeTab\"/>"
                + "<TabBar titles=\"${tabTitles}\" selected=\"${tabIndex}\"/></Column>", "t.jxml");
        for (JxmlView<Object> view : views) {
            SampleController c = new SampleController();
            JXElement bar = view.build(c).getChildren().get(0);
            assertEquals("tabbar", bar.getType());
            assertArrayEquals(new String[] {"A", "B"}, (String[]) bar.getProps().get("titles"));
            assertEquals(Integer.valueOf(1), bar.getProps().get("selected"));
            assertEquals(Boolean.TRUE, bar.getProps().get("closable"));
            accept(bar, "onChange", Integer.valueOf(0));
            assertEquals(0, c.tabIndex);
            accept(bar, "onClose", Integer.valueOf(1));
            assertEquals(Arrays.asList(Integer.valueOf(1)), c.closed);
            JXElement bound = view.build(c).getChildren().get(1);
            assertArrayEquals(new String[] {"Inbox", "Sent", "Drafts"}, (String[]) bound.getProps().get("titles"));
            assertEquals(Boolean.FALSE, bound.getProps().get("closable"));
            assertNull(bound.getProps().get("onClose"));
        }
    }

    // ---- composites ------------------------------------------------------------------------------------

    @Test
    void anIconButtonIsARoundButtonWithAnIconThatDoesNotTakeTheClick() throws Exception {
        JXElement button = one("<IconButton icon=\"add\" onAction=\"#save\" hgrow=\"always\"/>");
        assertEquals("button", button.getType());
        assertEquals("text", button.getProps().get("variant"));
        assertEquals(Boolean.TRUE, button.getProps().get("round"));
        assertEquals(Double.valueOf(8), button.getProps().get("padding"));
        assertEquals("always", button.getProps().get("hgrow"));
        assertEquals(1, button.getChildren().size());
        JXElement icon = button.getChildren().get(0);
        assertEquals("icon", icon.getType());
        assertEquals("add", icon.getProps().get("name"));
        assertEquals(Double.valueOf(24), icon.getProps().get("size"));
        assertEquals(Boolean.TRUE, icon.getProps().get("mouseTransparent"));
        assertEquals(Integer.valueOf(0xFF1C1B1F), icon.getProps().get("color"), "onSurface of the light theme");
        JXElement filled = one("<IconButton icon=\"add\" variant=\"filled\" size=\"18\" color=\"#112233\"/>");
        assertEquals("filled", filled.getProps().get("variant"));
        assertEquals(Double.valueOf(18), filled.getChildren().get(0).getProps().get("size"));
        assertEquals(Integer.valueOf(0xFF112233), filled.getChildren().get(0).getProps().get("color"));
        assertEquals(Integer.valueOf(0xFFFFFFFF), one("<IconButton icon=\"add\" variant=\"filled\"/>")
                .getChildren().get(0).getProps().get("color"), "onPrimary on a filled button");
    }

    @Test
    void aFabIsAFloatingButtonWithAnIconATextOrBoth() throws Exception {
        JXElement icon = one("<Fab icon=\"add\"/>");
        assertEquals("fab", icon.getProps().get("variant"));
        assertEquals(Double.valueOf(16), icon.getProps().get("padding"));
        assertEquals(1, icon.getChildren().size());
        JXElement extended = one("<Fab icon=\"edit\" text=\"Create\"/>");
        assertEquals("Create", extended.getProps().get("label"));
        assertEquals(1, extended.getChildren().size());
        JXElement label = one("<Fab text=\"Go\"/>");
        assertEquals(0, label.getChildren().size());
        assertArrayEquals(new double[] {16, 20, 16, 20}, (double[]) label.getProps().get("padding"), 0);
    }

    // ---- mistakes --------------------------------------------------------------------------------------

    @Test
    void theNewTagsReportTheirMistakesWithTheirLine() {
        assertError(2, "needs name=", "<Column>\n<Icon/></Column>");
        assertError(2, "name=\"nope\" is not one of [menu, close, add", "<Column>\n<Icon name=\"nope\"/></Column>");
        assertError(2, "cannot be bound", "<Column>\n<Icon name=\"${title}\"/></Column>");
        assertError(2, "name=\"nope\" is not one of", "<Column>\n<IconButton icon=\"nope\"/></Column>");
        assertError(2, "<IconButton> needs icon=", "<Column>\n<IconButton/></Column>");
        assertError(2, "variant fab is what <Fab> is", "<Column>\n<IconButton icon=\"add\" variant=\"fab\"/></Column>");
        assertError(2, "<Fab> needs icon", "<Column>\n<Fab/></Column>");
        assertError(2, "name=\"nope\" is not one of", "<Column>\n<Fab icon=\"nope\"/></Column>");
        assertError(2, "variant=\"loud\" is not one of [filled, tonal, outlined, text, elevated, fab]",
                "<Column>\n<Button variant=\"loud\"/></Column>");
        assertError(2, "kind=\"big\" is not one of [assist, filter, choice, input]", "<Column>\n<Chip kind=\"big\"/></Column>");
        assertError(2, "orientation=\"diagonal\" is not one of", "<Column>\n<SplitPane orientation=\"diagonal\"/></Column>");
        assertError(2, "is not a valid doubles", "<Column>\n<SplitPane dividerPositions=\"a, b\"/></Column>");
        assertError(2, "closable=\"yes\" is not a valid boolean", "<Column>\n<TabBar closable=\"yes\"/></Column>");
        assertError(2, "has both onChange and a @{...} binding",
                "<Column>\n<TabBar selected=\"@{tabIndex}\" onChange=\"#setTabIndex\"/></Column>");
        assertError(2, "onClose must name a controller method", "<Column>\n<TabBar onClose=\"close\"/></Column>");
        assertError(2, "takes one child", "<Column>\n<Badge><Label/><Label/></Badge></Column>");
        assertError(2, "takes no children", "<Column>\n<Switch><Label/></Switch></Column>");
        assertError(2, "takes no children", "<Column>\n<IconButton icon=\"add\"><Label/></IconButton></Column>");
    }

    @Test
    void aTabBarHandlerMustTakeAnInt() throws Exception {
        JxmlTemplate bad = ViewCompiler.read("<TabBar controller=\"" + CTRL + "\" titles=\"A\" onClose=\"#setName\"/>", "t.jxml");
        JxmlException e = assertThrows(JxmlException.class, () -> bad.build(new SampleController()));
        assertTrue(e.getMessage().contains("setName") && e.getMessage().contains("Integer"), e.getMessage());
    }

    // ---- themes and styles -----------------------------------------------------------------------------

    @Test
    void aThemeNamesTheTokensAndTheRendererInheritsIt() throws Exception {
        JXElement dark = build("<Column controller=\"" + CTRL + "\" theme=\"dark\">\n<Label textFill=\"primary\" background=\"surface\" "
                + "borderColor=\"outline\"/><Icon name=\"add\" color=\"error\"/></Column>", new SampleController());
        assertEquals("dark", dark.getProps().get("theme"));
        assertEquals(Integer.valueOf(0xFF90CAF9), dark.getChildren().get(0).getProps().get("textFill"));
        assertEquals(Integer.valueOf(0xFF1C1B1F), dark.getChildren().get(0).getProps().get("background"));
        assertEquals(Integer.valueOf(0xFF938F99), dark.getChildren().get(0).getProps().get("borderColor"));
        assertEquals(Integer.valueOf(0xFFF2B8B5), dark.getChildren().get(1).getProps().get("color"));
        JXElement light = one("<Label textFill=\"primary\" background=\"onSurface\"/>");
        assertEquals(Integer.valueOf(0xFF1565C0), light.getProps().get("textFill"));
        assertEquals(Integer.valueOf(0xFF1C1B1F), light.getProps().get("background"));
        assertNull(build("<Column controller=\"" + CTRL + "\"/>", new SampleController()).getProps().get("theme"));
        assertEquals("light", build("<Column controller=\"" + CTRL + "\" theme=\"light\"/>", new SampleController())
                .getProps().get("theme"));
        assertEquals(6, JxmlThemes.TOKENS.size());
    }

    @Test
    void aStyleSetsAttributesAndTheElementsOwnWin() throws Exception {
        String xml = "<Column controller=\"" + CTRL + "\">\n"
                + "<Style name=\"base\" background=\"#112233\" textFill=\"#FFFFFF\" bold=\"true\" padding=\"4\"/>\n"
                + "<Style name=\"loud\" background=\"#445566\"/>\n"
                + "<Label text=\"a\" style=\"base\"/>\n"
                + "<Label text=\"b\" style=\"base\" background=\"#778899\" bold=\"false\"/>\n"
                + "<Label text=\"c\" style=\"base loud\"/>\n"
                + "<Card style=\"base\"/>\n"
                + "<Card/>\n"
                + "<Button text=\"d\" style=\"base\"/>\n"
                + "<Row style=\"base\"/>\n"
                + "</Column>";
        JXElement column = build(xml, new SampleController());
        assertEquals(7, column.getChildren().size(), "the Style elements are not elements");
        JXElement a = column.getChildren().get(0);
        assertEquals(Integer.valueOf(0xFF112233), a.getProps().get("background"));
        assertEquals(Integer.valueOf(0xFFFFFFFF), a.getProps().get("textFill"));
        assertEquals(Boolean.TRUE, a.getProps().get("bold"));
        assertNull(a.getProps().get("padding"), "padding is not an attribute of a Label: the style skips it");
        JXElement b = column.getChildren().get(1);
        assertEquals(Integer.valueOf(0xFF778899), b.getProps().get("background"));
        assertEquals(Boolean.FALSE, b.getProps().get("bold"));
        assertEquals(Integer.valueOf(0xFF445566), column.getChildren().get(2).getProps().get("background"), "a later style wins");
        assertEquals(Integer.valueOf(0xFF112233), column.getChildren().get(3).getProps().get("background"), "a style beats a composite's default");
        assertEquals(Integer.valueOf(0xFFFFFFFF), column.getChildren().get(4).getProps().get("background"), "without a style: the default");
        assertEquals(Double.valueOf(4), column.getChildren().get(3).getProps().get("padding") instanceof Double
                ? column.getChildren().get(3).getProps().get("padding") : null);
        assertEquals(Integer.valueOf(0xFFFFFFFF), column.getChildren().get(5).getProps().get("textFill"));
        assertEquals(Integer.valueOf(0xFF112233), column.getChildren().get(6).getProps().get("background"));
        assertFalse(column.getChildren().get(6).getProps().asMap().containsKey("style"));
    }

    @Test
    void stylesReportTheirMistakes() {
        assertError(3, "unknown style \"nope\"; styles: [a]", "<Column>\n<Style name=\"a\" bold=\"true\"/>\n<Label style=\"nope\"/></Column>");
        assertError(2, "<Style> needs name=", "<Column>\n<Style bold=\"true\"/></Column>");
        assertError(3, "two <Style> named a", "<Column>\n<Style name=\"a\"/>\n<Style name=\"a\"/></Column>");
        assertError(2, "<Style> cannot set onAction", "<Column>\n<Style name=\"a\" onAction=\"#save\"/></Column>");
        assertError(2, "<Style> cannot set backgroud", "<Column>\n<Style name=\"a\" backgroud=\"#FFFFFF\"/></Column>");
        assertError(2, "<Style> background cannot be bound", "<Column>\n<Style name=\"a\" background=\"${color}\"/></Column>");
        assertError(2, "<Style> takes no children", "<Column>\n<Style name=\"a\"><Label/></Style></Column>");
        assertError(3, "<Style> is only allowed directly inside the root element",
                "<Column>\n<Row>\n<Style name=\"a\"/></Row></Column>");
        // a style's value is checked where it lands
        assertError(3, "is not a valid color", "<Column>\n<Style name=\"a\" background=\"red\"/>\n<Label style=\"a\"/></Column>");
        assertError(1, "theme=\"neon\" is not one of [light, dark]", "<Column theme=\"neon\"/>");
        assertError(2, "theme is only allowed on the root element", "<Column>\n<Row theme=\"dark\"/></Column>");
        assertError(2, "<Include> has no attribute style", "<Column>\n<Include src=\"x.jxml\" style=\"a\"/></Column>");
    }

    // ---- includes --------------------------------------------------------------------------------------

    private File write(String name, String text) throws Exception {
        File f = dir.resolve(name).toFile();
        Files.createDirectories(f.getParentFile().toPath());
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    void anIncludedPartIsBuiltIntoTheTreeLikeIfItWereWrittenThere() throws Exception {
        write("parts/bar.jxml", "<Row gap=\"3\"><Label text=\"${title}\"/><Include src=\"deep/x.jxml\"/></Row>");
        write("parts/deep/x.jxml", "<Button text=\"${name}\"/>");
        File main = write("main.jxml", "<Column controller=\"" + CTRL + "\">\n<Include src=\"parts/bar.jxml\" hgrow=\"always\"/>\n"
                + "<Label text=\"after\"/>\n</Column>");
        JxmlTemplate template = JxmlTemplate.read(main);
        JXElement tree = agree(new ViewCompiler(dir).views(template), new SampleController());
        assertEquals(2, tree.getChildren().size());
        JXElement bar = tree.getChildren().get(0);
        assertEquals("row", bar.getType());
        assertEquals(Double.valueOf(3), bar.getProps().get("gap"));
        assertEquals("always", bar.getProps().get("hgrow"), "placement written on the include goes to the part's root");
        assertEquals("Sample", bar.getChildren().get(0).getProps().get("value"), "the part binds to the controller of the file that includes it");
        assertEquals("Ada", bar.getChildren().get(1).getProps().get("label"));
    }

    @Test
    void theClasspathAndTheDirectoryFindTheSamePartsAsTheFileSystem() throws Exception {
        JxmlTemplate fromResources = JxmlTemplate.readResource(getClass().getClassLoader(), "samples/includes.jxml");
        JxmlTemplate fromDirectory = JxmlTemplate.read(new File("src/test/shared/samples/includes.jxml"));
        SampleController c = new SampleController();
        assertNull(JxmlTrees.diff(fromResources.build(c), fromDirectory.build(c)));
        JXElement tree = fromResources.build(c);
        assertEquals("dark", tree.getProps().get("theme"));
        // header, styled label, card, button, two rows of the loop, header again
        assertEquals(7, tree.getChildren().size());
        JXElement header = tree.getChildren().get(0);
        assertEquals("row", header.getType());
        assertEquals(Integer.valueOf(0xFF90CAF9), header.getChildren().get(0).getProps().get("textFill"), "a part uses the styles of the file that includes it");
        assertEquals("icon", header.getChildren().get(1).getType());
        assertEquals("Item 0", tree.getChildren().get(4).getProps().get("value"), "a part in a loop sees the loop variable");
        assertEquals("Item 1", tree.getChildren().get(5).getProps().get("value"));
        JXElement button = tree.getChildren().get(3);
        assertEquals(Integer.valueOf(0xFF00FF00), button.getProps().get("background"), "the element wins over its style");
        assertEquals(Integer.valueOf(0xFF0D2B4A), button.getProps().get("textFill"), "onPrimary of the dark theme");
    }

    @Test
    void anIncludeCycleIsReportedWithTheChainOfFiles() throws Exception {
        write("a.jxml", "<Column>\n<Include src=\"b.jxml\"/>\n</Column>");
        write("b.jxml", "<Row>\n\n<Include src=\"a.jxml\"/>\n</Row>");
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlTemplate.read(dir.resolve("a.jxml").toFile()));
        assertTrue(e.getMessage().contains("include cycle: ") && e.getMessage().contains("a.jxml -> ")
                && e.getMessage().contains("b.jxml -> ") && e.getMessage().endsWith("a.jxml"), e.getMessage());
        assertEquals(3, e.getLine());
        assertTrue(e.getMessage().contains("b.jxml:3: "), "the error is in the file that closes the cycle: " + e.getMessage());
        write("self.jxml", "<Column><Include src=\"self.jxml\"/></Column>");
        assertTrue(assertThrows(JxmlException.class, () -> JxmlTemplate.read(dir.resolve("self.jxml").toFile()))
                .getMessage().contains("include cycle"));
    }

    @Test
    void aMistakeInAnIncludedFileNamesThatFileAndLine() throws Exception {
        write("parts/bad.jxml", "<Row>\n<Label text=\"ok\"/>\n<Labl/>\n</Row>");
        File main = write("main.jxml", "<Column>\n<Include src=\"parts/bad.jxml\"/>\n</Column>");
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlTemplate.read(main));
        assertEquals(3, e.getLine());
        assertTrue(e.getMessage().contains("parts/bad.jxml:3: unknown tag <Labl>"), e.getMessage());
        // a binding that fails at build time reports the part too
        write("parts/bind.jxml", "<Label text=\"${nope}\"/>");
        File bound = write("bound.jxml", "<Column controller=\"" + CTRL + "\">\n<Include src=\"parts/bind.jxml\"/>\n</Column>");
        JxmlTemplate template = JxmlTemplate.read(bound);
        JxmlException built = assertThrows(JxmlException.class, () -> template.build(new SampleController()));
        assertTrue(built.getMessage().contains("parts/bind.jxml:1:") && built.getMessage().contains("getNope"), built.getMessage());
        // the generated code names the part in its comments
        String java = JxmlCompiler.compile(template, new JxmlCompiler.Options("gen", "Bound", false));
        assertTrue(java.contains("// bind.jxml:1"), java);
    }

    @Test
    void includeMistakesAreReportedAtTheIncludeLine() throws Exception {
        File main = write("main.jxml", "<Column>\n<Include src=\"missing.jxml\"/>\n</Column>");
        JxmlException missing = assertThrows(JxmlException.class, () -> JxmlTemplate.read(main));
        assertEquals(2, missing.getLine());
        assertTrue(missing.getMessage().contains("cannot read ") && missing.getMessage().contains("missing.jxml"), missing.getMessage());
        assertError(2, "<Include> needs src=", "<Column>\n<Include/></Column>");
        assertError(2, "src cannot be bound", "<Column>\n<Include src=\"${title}\"/></Column>");
        assertError(2, "takes no children", "<Column>\n<Include src=\"x.jxml\"><Label/></Include></Column>");
        assertError(2, "has no attribute controller", "<Column>\n<Include src=\"x.jxml\" controller=\"a.B\"/></Column>");
        assertError("t.jxml", 2, "read from a stream", assertThrows(JxmlException.class, () -> JxmlTemplate.read(
                new java.io.ByteArrayInputStream("<Column>\n<Include src=\"x.jxml\"/></Column>".getBytes(StandardCharsets.UTF_8)), "t.jxml")));
        assertError(1, "cannot be the root element", "<Include src=\"x.jxml\"/>");
        File up = write("deep/up.jxml", "<Column>\n<Include src=\"../../outside.jxml\"/>\n</Column>");
        JxmlLoader loader = JxmlLoader.directory(dir.toFile());
        JxmlException escape = assertThrows(JxmlException.class, () -> JxmlTemplate.read(
                new java.io.ByteArrayInputStream(Files.readAllBytes(up.toPath())), "deep/up.jxml", loader));
        assertTrue(escape.getMessage().contains("leaves the folder"), escape.getMessage());
        // a part's controller must be the includer's
        write("parts/own.jxml", "<Label controller=\"other.Controller\" text=\"x\"/>");
        File clash = write("clash.jxml", "<Column controller=\"" + CTRL + "\">\n<Include src=\"parts/own.jxml\"/>\n</Column>");
        JxmlException differs = assertThrows(JxmlException.class, () -> JxmlTemplate.read(clash));
        assertEquals(2, differs.getLine());
        assertTrue(differs.getMessage().contains("declares controller other.Controller"), differs.getMessage());
        write("parts/same.jxml", "<Label controller=\"" + CTRL + "\" text=\"${title}\"/>");
        File same = write("same.jxml", "<Column controller=\"" + CTRL + "\"><Include src=\"parts/same.jxml\"/></Column>");
        assertEquals("Sample", JxmlTemplate.read(same).build(new SampleController()).getChildren().get(0).getProps().get("value"));
    }

    @Test
    void joiningPathsStaysInsideTheFolderOrTheFile() {
        assertEquals("parts/a.jxml", JxmlLoader.join("main.jxml", "parts/a.jxml"));
        assertEquals("screens/parts/a.jxml", JxmlLoader.join("screens/main.jxml", "./parts/a.jxml"));
        assertEquals("shared/a.jxml", JxmlLoader.join("screens/main.jxml", "../shared/a.jxml"));
        assertNull(JxmlLoader.join("main.jxml", "../a.jxml"));
        assertEquals("C:/x/b.jxml", JxmlLoader.join("C:\\x\\y\\a.jxml", "../b.jxml"));
        assertEquals("/b.jxml", JxmlLoader.join("/x/a.jxml", "../b.jxml"));
    }

    // ---- the compiler's directory mode -------------------------------------------------------------------

    @Test
    void theDirectoryCompilerSkipsPartsRegeneratesWhatIncludesThemAndReadsThemFromTheSourceFolder() throws Exception {
        File src = dir.resolve("src").toFile();
        File out = dir.resolve("out").toFile();
        File part = new File(src, "parts/title.jxml");
        write("src/parts/title.jxml", "<Label text=\"${title}\"/>");
        write("src/screen.jxml", "<Column controller=\"" + CTRL + "\"><Include src=\"parts/title.jxml\"/></Column>");
        write("src/owned.jxml", "<Column controller=\"" + CTRL + "\"><Include src=\"parts/view.jxml\"/></Column>");
        write("src/parts/view.jxml", "<Label controller=\"" + CTRL + "\" text=\"${name}\"/>");
        JXForge.Result first = JXForge.compileDirectory(src, out, "gen", "View", true);
        List<String> written = new java.util.ArrayList<String>();
        for (File f : first.written) {
            written.add(f.getName());
        }
        java.util.Collections.sort(written);
        assertEquals(Arrays.asList("OwnedView.java", "ScreenView.java", "ViewView.java"), written, "the part without a controller has no class");
        assertEquals(1, first.parts.size());
        assertTrue(first.parts.get(0).getPath().endsWith("title.jxml"));
        String generated = new String(Files.readAllBytes(new File(out, "gen/ScreenView.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(generated.contains("title.jxml:1"), "the generated code points at the part");
        assertEquals(3, JXForge.compileDirectory(src, out, "gen", "View", true).upToDate.size());
        // a changed part regenerates the screens that include it
        long later = System.currentTimeMillis() + 5000;
        assertTrue(part.setLastModified(later));
        Files.write(part.toPath(), "<Label text=\"${name}\"/>".getBytes(StandardCharsets.UTF_8));
        part.setLastModified(later);
        JXForge.Result second = JXForge.compileDirectory(src, out, "gen", "View", true);
        List<String> again = new java.util.ArrayList<String>();
        for (File f : second.written) {
            again.add(f.getName());
        }
        assertTrue(again.contains("ScreenView.java") && again.contains("OwnedView.java"), again.toString());
        assertTrue(new String(Files.readAllBytes(new File(out, "gen/ScreenView.java").toPath()), StandardCharsets.UTF_8).contains("getName"));
    }
}
