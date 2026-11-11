package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * Attributes that a binding may now give ({@code selectionMode}, {@code variant}, the root's {@code theme} and with it
 * every theme token, a number for a {@code Spinner}), what a {@code Slot} may carry, the attributes the composites pass
 * on, and the pickers and menus that open by themselves when no {@code showing} is written. The interpreter and the
 * generated code (with and without hoisting) must build the same tree for each, and the mounted nodes must follow.
 */
class JxmlBindableAttributesTest {
    private static final String CTRL = "com.jxparallel.forge.samples.SampleController";

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

    @SuppressWarnings("unchecked")
    private static void accept(JXElement e, String prop, Object value) {
        ((Consumer<Object>) e.getProps().get(prop)).accept(value);
    }

    private static JxmlException error(String xml) {
        return assertThrows(JxmlException.class, () -> ViewCompiler.read(xml, "t.jxml"));
    }

    // ---- selectionMode and variant ------------------------------------------------------------------

    private static final String TABLE = "<TableView items=\"${rows}\" var=\"r\" rows=\"3\" selectionMode=\"${mode}\">"
            + "<TableRow><TableCell text=\"${r}\"/></TableRow></TableView>\n"
            + "<Button text=\"b\" variant=\"${variant}\"/>";

    @Test
    void selectionModeAndVariantAreBoundAndCheckedWhenTheTreeIsBuilt() throws Exception {
        JXElement multiple = screen(TABLE, c -> { });
        assertEquals("multiple", multiple.getChildren().get(0).getProps().get("selectionMode"));
        assertEquals("tonal", multiple.getChildren().get(1).getProps().get("variant"));
        JXElement single = screen(TABLE, c -> {
            c.mode = "single";
            c.variant = "outlined";
        });
        assertEquals("single", single.getChildren().get(0).getProps().get("selectionMode"));
        assertEquals("outlined", single.getChildren().get(1).getProps().get("variant"));
        // empty is "not written": no prop, a plain button and single selection
        JXElement none = screen(TABLE, c -> {
            c.mode = "";
            c.variant = null;
        });
        assertNull(none.getChildren().get(0).getProps().get("selectionMode"));
        assertFalse(none.getChildren().get(1).getProps().asMap().containsKey("variant"));
    }

    @Test
    void aBoundValueOutsideTheChoicesIsAnErrorThatNamesThem() throws Exception {
        String xml = "<Column controller=\"" + CTRL + "\">\n" + TABLE + "\n</Column>";
        List<JxmlView<Object>> views = new ViewCompiler(dir).views(xml, "t.jxml");
        SampleController c = new SampleController();
        c.variant = "huge";
        JxmlException interpreted = assertThrows(JxmlException.class, () -> views.get(0).build(c));
        assertTrue(interpreted.getMessage().startsWith("t.jxml:3: "), interpreted.getMessage());
        assertTrue(interpreted.getMessage().contains("variant must be one of [filled, tonal, outlined, text, elevated, fab]"),
                interpreted.getMessage());
        assertTrue(interpreted.getMessage().contains("\"huge\""), interpreted.getMessage());
        for (int i = 1; i < views.size(); i++) {
            IllegalArgumentException compiled = assertThrows(IllegalArgumentException.class, () -> views.get(1).build(c));
            assertTrue(compiled.getMessage().contains("variant must be one of"), compiled.getMessage());
        }
        c.variant = "text";
        c.mode = "triple";
        JxmlException mode = assertThrows(JxmlException.class, () -> views.get(0).build(c));
        assertTrue(mode.getMessage().contains("selectionMode must be one of [single, multiple]"), mode.getMessage());
        // a literal is still checked when the file is read
        assertTrue(error("<Column>\n<Button variant=\"huge\"/></Column>").getMessage().contains("is not one of"));
        assertTrue(error("<Column>\n<TableView items=\"${rows}\" var=\"r\" selectionMode=\"some\"><TableRow/></TableView></Column>")
                .getMessage().contains("is not one of [single, multiple]"));
        // IconButton and Fab keep a literal variant: what the icon looks like depends on it when the file is read
        assertTrue(error("<Column>\n<IconButton icon=\"add\" variant=\"${variant}\"/></Column>").getMessage().contains("cannot be bound"));
    }

    // ---- theme ------------------------------------------------------------------------------------------------

    private static final String THEMED = "<Column controller=\"" + CTRL + "\" theme=\"${themeName}\" background=\"surface\">\n"
            + "<Label text=\"x\" textFill=\"primary\"/>\n<IconButton icon=\"add\" variant=\"filled\"/>\n</Column>";

    @Test
    void everyTokenFollowsABoundTheme() throws Exception {
        for (String name : new String[] {"dark", "light"}) {
            JXElement root = agree(THEMED, c -> c.themeName = name);
            assertEquals(name, root.getProps().get("theme"));
            assertEquals(Integer.valueOf(JxmlThemes.color(name, "surface")), root.getProps().get("background"));
            assertEquals(Integer.valueOf(JxmlThemes.color(name, "primary")), root.getChildren().get(0).getProps().get("textFill"));
            JXElement icon = root.getChildren().get(1).getChildren().get(0); // the IconButton's own default color: onPrimary
            assertEquals(Integer.valueOf(JxmlThemes.color(name, "onPrimary")), icon.getProps().get("color"));
        }
        assertNotEquals(JxmlThemes.color("dark", "surface"), JxmlThemes.color("light", "surface"));
        // no theme, or an empty one: the light tokens and no theme prop
        JXElement none = agree(THEMED, c -> c.themeName = "");
        assertNull(none.getProps().get("theme"));
        assertEquals(Integer.valueOf(JxmlThemes.color("light", "surface")), none.getProps().get("background"));
    }

    @Test
    void aWrittenThemeStillResolvesItsTokensWhenTheFileIsRead() throws Exception {
        JXElement root = agree("<Column controller=\"" + CTRL + "\" theme=\"dark\" background=\"surface\"/>", c -> { });
        assertEquals("dark", root.getProps().get("theme"));
        assertEquals(Integer.valueOf(JxmlThemes.color("dark", "surface")), root.getProps().get("background"));
        JxmlTemplate template = ViewCompiler.read("<Column controller=\"" + CTRL + "\" theme=\"dark\" background=\"surface\"/>", "t.jxml");
        assertTrue(template.root.constant() || template.root.props.stream().allMatch(p -> p.kind == JxmlTemplate.Kind.LITERAL));
    }

    @Test
    void aBoundThemeMustNameAThemeAndOnlyTheRootHasOne() throws Exception {
        List<JxmlView<Object>> views = new ViewCompiler(dir).views(THEMED, "t.jxml");
        SampleController c = new SampleController();
        c.themeName = "blue";
        JxmlException e = assertThrows(JxmlException.class, () -> views.get(0).build(c));
        assertTrue(e.getMessage().contains("theme must be one of [light, dark]"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> views.get(1).build(c));
        assertTrue(error("<Column theme=\"@{themeName}\"/>").getMessage().contains("cannot be bound two ways"));
        assertTrue(error("<Column theme=\"blue\"/>").getMessage().contains("not one of"));
        assertTrue(error("<Column>\n<Row theme=\"${themeName}\"/></Column>").getMessage().contains("only allowed on the root"));
        // a memoized loop cannot hold a token while the theme is bound: it would keep the first theme's color
        JxmlException memo = error("<Column theme=\"${themeName}\">\n<For items=\"${rows}\" var=\"r\" memo=\"true\">"
                + "<Label text=\"${r}\" textFill=\"primary\"/></For></Column>");
        assertTrue(memo.getMessage().contains("theme token primary"), memo.getMessage());
        // with the theme written, the same loop is fine: the token is resolved when the file is read
        ViewCompiler.read("<Column theme=\"dark\">\n<For items=\"${rows}\" var=\"r\" memo=\"true\">"
                + "<Label text=\"${r}\" textFill=\"primary\"/></For></Column>", "t.jxml");
    }

    // ---- Spinner ---------------------------------------------------------------------------------------------

    @Test
    void aSpinnerHoldsANumberAsTextAndReportsItToAnIntOrADoubleProperty() throws Exception {
        SampleController[] seen = new SampleController[1];
        JXElement column = screen("<Spinner value=\"@{qty}\" integer=\"true\" min=\"0\" max=\"9\"/>\n"
                + "<Spinner value=\"@{rating}\"/>\n<Spinner value=\"${rating}\" onChange=\"#setRating\"/>\n"
                + "<Spinner value=\"${title}\"/>\n<Spinner value=\"4\"/>\n<Spinner value=\"${qty}\" integer=\"true\" onChange=\"#setQty\"/>", c -> {
                    seen[0] = c;
                    c.rating = 2.5;
                });
        List<JXElement> spinners = column.getChildren();
        assertEquals("3", spinners.get(0).getProps().get("value"), "an int");
        assertEquals("2.5", spinners.get(1).getProps().get("value"), "a double with a fraction keeps it");
        assertEquals("2.5", spinners.get(2).getProps().get("value"));
        assertEquals("Sample", spinners.get(3).getProps().get("value"), "a string works as before");
        assertEquals("4", spinners.get(4).getProps().get("value"));
        assertEquals(Boolean.TRUE, spinners.get(0).getProps().get("integer"));
        accept(spinners.get(0), "onChange", Double.valueOf(7));
        assertEquals(7, seen[0].qty, "the renderer reports a Double; an integer spinner hands the int property an int");
        accept(spinners.get(1), "onChange", Double.valueOf(4));
        assertEquals(4.0, seen[0].rating);
        accept(spinners.get(2), "onChange", Double.valueOf(1.5));
        assertEquals(1.5, seen[0].rating);
        accept(spinners.get(5), "onChange", Double.valueOf(8));
        assertEquals(8, seen[0].qty);
        // a whole double is written without ".0"
        assertEquals("3", screen("<Spinner value=\"${rating}\"/>", c -> c.rating = 3).getChildren().get(0).getProps().get("value"));
        assertTrue(error("<Column>\n<Spinner integer=\"${busy}\"/></Column>").getMessage().contains("cannot be bound"));
    }

    @Test
    void numberTextIsWrittenTheWayASpinnerShowsIt() {
        assertEquals("3", JxmlValues.asNumberText(Double.valueOf(3)));
        assertEquals("-3", JxmlValues.asNumberText(Double.valueOf(-3)));
        assertEquals("0.25", JxmlValues.asNumberText(Double.valueOf(0.25)));
        assertEquals("12", JxmlValues.asNumberText(Integer.valueOf(12)));
        assertEquals("12", JxmlValues.asNumberText(Long.valueOf(12)));
        assertEquals("1.50", JxmlValues.asNumberText(new java.math.BigDecimal("1.50")));
        assertEquals("", JxmlValues.asNumberText(null));
        assertEquals("abc", JxmlValues.asNumberText("abc"));
        assertEquals("NaN", JxmlValues.asNumberText(Double.valueOf(Double.NaN)));
    }

    // ---- Slot --------------------------------------------------------------------------------------------------

    @Test
    void aSlotPlacesTheElementItShows() throws Exception {
        JXElement border = agree("<Border controller=\"" + CTRL + "\">\n"
                + "<Slot slot=\"center\" content=\"${marked}\" margin=\"3\" hgrow=\"always\" prefWidth=\"${gap}\" hidden=\"${busy}\"/>\n"
                + "<Slot slot=\"top\" content=\"${banner}\"/>\n<Slot slot=\"bottom\" content=\"${nothing}\" margin=\"2\"/>\n</Border>", c -> { });
        assertEquals(2, border.getChildren().size(), "a slot with nothing to show shows nothing");
        JXElement center = border.getChildren().get(0);
        assertEquals("#text", center.getType());
        assertEquals("marked", center.getProps().get("value"), "what the element set itself stays");
        assertEquals("center", center.getProps().get("position"));
        assertEquals(Double.valueOf(3), center.getProps().get("margin"), "the slot's margin wins over the element's");
        assertEquals("always", center.getProps().get("hgrow"));
        assertEquals(Double.valueOf(6), center.getProps().get("prefWidth"));
        assertEquals(Boolean.FALSE, center.getProps().get("hidden"));
        JXElement top = border.getChildren().get(1);
        assertEquals("top", top.getProps().get("position"));
        assertEquals("hand-written", top.getProps().get("value"));
        // a slot that writes nothing but content is still the element itself
        JXElement plain = screen("<Slot content=\"${marked}\"/>", c -> { }).getChildren().get(0);
        assertEquals(Double.valueOf(1), plain.getProps().get("margin"));
    }

    @Test
    void aSlotTakesOnlyThePlacementAttributes() {
        for (String attr : JxmlTemplate.SLOT_ATTRS) {
            JxmlSchema.Attr a = JxmlSchema.LAYOUT.get(attr);
            assertTrue(a != null, attr + " is a layout attribute");
        }
        JxmlException e = error("<Column>\n<Slot content=\"${banner}\" key=\"k\"/></Column>");
        assertTrue(e.getMessage().contains("<Slot> has no attribute key"), e.getMessage());
        assertEquals(2, e.getLine());
        assertTrue(error("<Column>\n<Slot content=\"${banner}\" fontSize=\"3\"/></Column>").getMessage().contains("has no attribute fontSize"));
    }

    // ---- composites ---------------------------------------------------------------------------------------------

    @Test
    void compositesPassTheAttributesTheirPrimitiveTakes() throws Exception {
        JXElement stack = agree("<Stack controller=\"" + CTRL + "\">\n"
                + "<Dialog title=\"T\" showing=\"false\" padding=\"6\" gap=\"${gap}\" alignment=\"CENTER\" fillWidth=\"true\" minWidth=\"300\"/>\n"
                + "<AlertDialog message=\"m\" showing=\"false\" padding=\"2 3\" gap=\"5\" alignment=\"CENTER\" fillWidth=\"false\"/>\n"
                + "<Modal showing=\"false\" padding=\"6\" gap=\"1\" alignment=\"CENTER\" fillWidth=\"true\"/>\n"
                + "<Row><IconButton icon=\"add\" padding=\"3\" accelerator=\"Ctrl+N\"/><Fab icon=\"add\" padding=\"9\" accelerator=\"Ctrl+M\"/></Row>\n"
                + "<NavigationBar alignment=\"CENTER_LEFT\"><Button text=\"a\"/></NavigationBar>\n</Stack>", c -> { });
        JXElement dialog = stack.getChildren().get(0);
        assertEquals("dialog", dialog.getType());
        assertEquals(Double.valueOf(6), dialog.getProps().get("padding"));
        assertEquals(Double.valueOf(6), dialog.getProps().get("gap"));
        assertEquals("CENTER", dialog.getProps().get("alignment"));
        assertEquals(Boolean.TRUE, dialog.getProps().get("fillWidth"));
        assertEquals(Double.valueOf(300), dialog.getProps().get("minWidth"));
        // what a Dialog does not write keeps the card's look it was given
        JXElement alert = stack.getChildren().get(1);
        assertEquals(Boolean.FALSE, alert.getProps().get("fillWidth"));
        assertEquals(Double.valueOf(5), alert.getProps().get("gap"));
        assertEquals(Double.valueOf(1), stack.getChildren().get(2).getProps().get("gap"));
        JXElement row = stack.getChildren().get(3);
        assertEquals(Double.valueOf(3), row.getChildren().get(0).getProps().get("padding"));
        assertEquals("Ctrl+N", row.getChildren().get(0).getProps().get("accelerator"));
        assertEquals(Double.valueOf(9), row.getChildren().get(1).getProps().get("padding"));
        assertEquals("Ctrl+M", row.getChildren().get(1).getProps().get("accelerator"));
        assertEquals("CENTER_LEFT", stack.getChildren().get(4).getProps().get("alignment"));
    }

    @Test
    void dialogsAndDrawersStayControlled() throws Exception {
        JXElement stack = agree("<Stack controller=\"" + CTRL + "\">\n<Dialog title=\"d\"/>\n<Modal/>\n<Drawer/>\n<SnackBar message=\"m\"/>\n"
                + "<AlertDialog message=\"a\"/>\n</Stack>", c -> { });
        List<String> notShown = new ArrayList<String>();
        for (JXElement layer : stack.getChildren()) {
            if (Boolean.FALSE.equals(layer.getProps().get("showing"))) {
                notShown.add(layer.getType());
            }
        }
        assertEquals(Arrays.asList("dialog", "dialog", "drawer", "snackbar", "dialog"), notShown,
                "closed unless the application says it is open");
    }
}
