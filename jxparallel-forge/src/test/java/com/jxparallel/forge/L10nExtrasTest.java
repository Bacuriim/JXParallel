package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;

/**
 * More translations, end to end: a translated Tab title, a translated TableView, arrays and the new ICU forms in the
 * generated {@code Strings}, FXML resource keys against a catalog, and the catalog of a {@link JxmlLiveView}.
 */
class L10nExtrasTest {
    private static final String CTRL = "com.jxparallel.forge.L10nController";
    private static final Locale PT = new Locale("pt");

    @TempDir
    Path dir;
    private int builds;

    // ---- helpers ---------------------------------------------------------------------------------------

    /** Writes the files (name, text, name, text...) under a fresh folder and returns it. */
    private File l10n(String... files) throws IOException {
        File root = dir.resolve("l10n" + (builds++)).toFile();
        for (int i = 0; i < files.length; i += 2) {
            Path file = root.toPath().resolve(files[i]);
            Files.createDirectories(file.getParent());
            Files.write(file, files[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    private static JxmlTemplate read(String xml, String name, JxmlCatalog catalog) {
        return JxmlTemplate.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), name, null, catalog);
    }

    private static String screen(String body) {
        return "<Column controller=\"" + CTRL + "\">\n" + body + "\n</Column>";
    }

    private static List<String> values(JXElement column) {
        List<String> out = new ArrayList<String>();
        for (JXElement child : column.getChildren()) {
            out.add(String.valueOf(child.getProps().get("value")));
        }
        return out;
    }

    private static String location(Class<?> type) {
        try {
            return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The generated Strings and the screens (with and without hoisting), compiled by javac into a class loader of their own. */
    private final class Built {
        final URLClassLoader loader;
        final Map<String, JxmlTemplate> templates = new LinkedHashMap<String, JxmlTemplate>();

        Built(JxmlCatalog catalog, Map<String, String> screens, boolean hoist) throws Exception {
            Path out = dir.resolve("b" + (builds++));
            Path gen = out.resolve("gen");
            Files.createDirectories(gen);
            List<String> sources = new ArrayList<String>();
            Files.write(gen.resolve("Strings.java"), catalog.generateStrings("gen").getBytes(StandardCharsets.UTF_8));
            sources.add(gen.resolve("Strings.java").toString());
            for (Map.Entry<String, String> s : screens.entrySet()) {
                JxmlTemplate template = read(s.getValue(), "t.jxml", catalog);
                templates.put(s.getKey(), template);
                Files.write(gen.resolve(s.getKey() + ".java"),
                        JxmlCompiler.compile(template, new JxmlCompiler.Options("gen", s.getKey(), hoist)).getBytes(StandardCharsets.UTF_8));
                sources.add(gen.resolve(s.getKey() + ".java").toString());
            }
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            assertNotNull(javac, "tests need a JDK");
            String classpath = location(L10nController.class) + File.pathSeparator + location(JxmlView.class)
                    + File.pathSeparator + location(JXElement.class);
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            List<String> args = new ArrayList<String>(Arrays.asList("-encoding", "UTF-8", "-source", "8", "-target", "8", "-nowarn",
                    "-classpath", classpath, "-d", out.toString()));
            args.addAll(sources);
            if (javac.run(null, null, errors, args.toArray(new String[0])) != 0) {
                throw new AssertionError(new String(errors.toByteArray(), StandardCharsets.UTF_8) + "\n"
                        + new String(Files.readAllBytes(gen.resolve("Strings.java")), StandardCharsets.UTF_8));
            }
            loader = new URLClassLoader(new URL[] {out.toUri().toURL()}, getClass().getClassLoader());
            setLocale(Locale.ENGLISH);
        }

        @SuppressWarnings("unchecked")
        JxmlView<Object> view(String name) throws Exception {
            return (JxmlView<Object>) (JxmlView<?>) loader.loadClass("gen." + name).getDeclaredConstructor().newInstance();
        }

        Class<?> strings() throws Exception {
            return loader.loadClass("gen.Strings");
        }

        void setLocale(Locale locale) throws Exception {
            strings().getMethod("setLocale", Locale.class).invoke(null, locale);
        }
    }

    /** The interpreter and the compiled views (hoisted or not) build the same tree in both locales. */
    private void assertSameTrees(JxmlCatalog catalog, String xml, Object controller, Locale other, String... expectedFirstLabels) throws Exception {
        for (boolean hoist : new boolean[] {true, false}) {
            Map<String, String> screens = new LinkedHashMap<String, String>();
            screens.put("Screen", xml);
            Built built = new Built(catalog, screens, hoist);
            JxmlTemplate interpreted = built.templates.get("Screen");
            for (Locale locale : new Locale[] {Locale.ENGLISH, other, Locale.ENGLISH}) {
                built.setLocale(locale);
                catalog.messages().setLocale(locale);
                JXElement expected = interpreted.build(controller);
                JXElement compiled = built.view("Screen").build(controller);
                assertNull(JxmlTrees.diff(expected, compiled), "hoist " + hoist + ", " + locale);
            }
        }
    }

    private static final String ARB_EN = "{\"@@locale\": \"en\", \"tabOne\": \"First\", \"tabTwo\": \"Second\", \"colId\": \"Id\", \"colQty\": \"Quantity\"}";
    private static final String ARB_PT = "{\"@@locale\": \"pt\", \"tabOne\": \"Primeiro\", \"tabTwo\": \"Segundo\", \"colId\": \"Codigo\", \"colQty\": \"Quantidade\"}";

    // ---- Tab title ---------------------------------------------------------------------------------

    @Test
    void aTabTitleIsATranslationOrABindingReadWhenTheScreenIsBuiltBothWays() throws Exception {
        JxmlCatalog catalog = JxmlCatalog.load(l10n("app_en.arb", ARB_EN, "app_pt.arb", ARB_PT));
        String xml = screen("<Tabs>\n<Tab title=\"%{tabOne}\"><Label text=\"a\"/></Tab>\n<Tab title=\"Static\" closable=\"false\"><Label text=\"b\"/></Tab>\n"
                + "<Tab title=\"${gender}\"><Label text=\"c\"/></Tab>\n<Tab title=\"%{tabTwo}\"><Label text=\"d\"/></Tab>\n</Tabs>");
        L10nController controller = new L10nController();
        catalog.messages().setLocale(Locale.ENGLISH);
        JXElement tabs = read(xml, "t.jxml", catalog).build(controller).getChildren().get(0);
        assertEquals("tabs", tabs.getType());
        assertEquals(Arrays.asList("First", "Static", "female", "Second"), Arrays.asList((String[]) tabs.getProps().get("titles")));
        catalog.messages().setLocale(PT);
        tabs = read(xml, "t.jxml", catalog).build(controller).getChildren().get(0);
        assertEquals(Arrays.asList("Primeiro", "Static", "female", "Segundo"), Arrays.asList((String[]) tabs.getProps().get("titles")));
        controller.gender = "male";
        tabs = read(xml, "t.jxml", catalog).build(controller).getChildren().get(0);
        assertEquals("male", ((String[]) tabs.getProps().get("titles"))[2]);
        assertSameTrees(catalog, xml, new L10nController(), PT);
    }

    @Test
    void aTabsWhoseOnlyTranslationIsATitleIsNotHoistedAndStillChangesWithTheLocale() throws Exception {
        JxmlCatalog catalog = JxmlCatalog.load(l10n("app_en.arb", ARB_EN, "app_pt.arb", ARB_PT));
        String xml = "<Column controller=\"" + CTRL + "\">\n<Tabs><Tab title=\"%{tabOne}\"><Label text=\"a\"/></Tab></Tabs>\n</Column>";
        Map<String, String> screens = new LinkedHashMap<String, String>();
        screens.put("Screen", xml);
        Built built = new Built(catalog, screens, true);
        String java = JxmlCompiler.compile(built.templates.get("Screen"), new JxmlCompiler.Options("gen", "Screen", true));
        assertTrue(java.contains("Strings.tabOne()"), java);
        built.setLocale(Locale.ENGLISH);
        assertEquals("First", ((String[]) built.view("Screen").build(new L10nController()).getChildren().get(0).getProps().get("titles"))[0]);
        built.setLocale(PT);
        assertEquals("Primeiro", ((String[]) built.view("Screen").build(new L10nController()).getChildren().get(0).getProps().get("titles"))[0]);
        // the same view object, built again after the locale changed
        JxmlView<Object> view = built.view("Screen");
        built.setLocale(Locale.ENGLISH);
        assertEquals("First", ((String[]) view.build(new L10nController()).getChildren().get(0).getProps().get("titles"))[0]);
    }

    @Test
    void aTranslatedTabTitleCannotBeInsideAMemoLoopButABoundOneCannotEither() throws Exception {
        JxmlCatalog catalog = JxmlCatalog.load(l10n("app_en.arb", ARB_EN));
        JxmlException e = assertThrows(JxmlException.class, () -> read(screen("<For items=\"${user}\" var=\"u\" memo=\"true\">\n"
                + "<Tabs><Tab title=\"%{tabOne}\"><Label text=\"a\"/></Tab></Tabs></For>"), "t.jxml", catalog));
        assertTrue(e.getMessage().startsWith("t.jxml:3: ") && e.getMessage().contains("<For memo>") && e.getMessage().contains("title"), e.getMessage());
        e = assertThrows(JxmlException.class, () -> read(screen("<For items=\"${user}\" var=\"u\" memo=\"true\">\n"
                + "<Tabs><Tab title=\"${gender}\"><Label text=\"a\"/></Tab></Tabs></For>"), "t.jxml", catalog));
        assertTrue(e.getMessage().contains("<For memo>") && e.getMessage().contains("controller"), e.getMessage());
        // a translated title elsewhere in the tab pane is fine, and a title that is neither is the plain text it was
        assertEquals(Arrays.asList("Plain"), Arrays.asList((String[]) read(screen("<Tabs><Tab title=\"Plain\"><Label/></Tab></Tabs>"), "t.jxml", catalog)
                .build(new L10nController()).getChildren().get(0).getProps().get("titles")));
        e = assertThrows(JxmlException.class, () -> read(screen("<Tabs><Tab title=\"%{nope}\"><Label/></Tab></Tabs>"), "t.jxml", catalog));
        assertTrue(e.getMessage().startsWith("t.jxml:2: ") && e.getMessage().contains("nope"), e.getMessage());
    }

    // ---- TableView columns ---------------------------------------------------------------------------

    @Test
    void theColumnsOfATableViewAreTranslatableAsAList() throws Exception {
        JxmlCatalog catalog = JxmlCatalog.load(l10n("app_en.arb", ARB_EN, "app_pt.arb", ARB_PT));
        String xml = "<TableView controller=\"" + SampleController.class.getName() + "\" items=\"${rows}\" var=\"r\" "
                + "type=\"" + SampleController.class.getName() + ".Row\" key=\"${r.id}\" first=\"${top}\" rows=\"8\" rowHeight=\"24\" "
                + "columns=\"%{colId}, Name, %{colQty}\" columnWidths=\"${widths}\" constrained=\"true\" prefHeight=\"150\">\n"
                + "<TableRow selected=\"${r.selected}\"><TableCell text=\"${r.id}\"/><TableCell text=\"${r.name}\"/><TableCell text=\"${r.price}\"/></TableRow>\n"
                + "</TableView>";
        catalog.messages().setLocale(Locale.ENGLISH);
        JXElement table = read(xml, "t.jxml", catalog).build(new SampleController());
        assertEquals(Arrays.asList("Id", "Name", "Quantity"), Arrays.asList((String[]) table.getProps().get("columns")));
        catalog.messages().setLocale(PT);
        table = read(xml, "t.jxml", catalog).build(new SampleController());
        assertEquals(Arrays.asList("Codigo", "Name", "Quantidade"), Arrays.asList((String[]) table.getProps().get("columns")));
        Map<String, String> screens = new LinkedHashMap<String, String>();
        screens.put("Table", xml);
        for (boolean hoist : new boolean[] {true, false}) {
            Built built = new Built(catalog, screens, hoist);
            for (Locale locale : new Locale[] {Locale.ENGLISH, PT}) {
                built.setLocale(locale);
                catalog.messages().setLocale(locale);
                assertNull(JxmlTrees.diff(built.templates.get("Table").build(new SampleController()), built.view("Table").build(new SampleController())),
                        hoist + " " + locale);
            }
        }
    }

    // ---- arrays, ordinals, offsets and styles in the generated Strings ----------------------------------

    private static final String ARB_RICH = "{\"@@locale\": \"en\",\n"
            + "\"days[0]\": \"Mon\", \"days[1]\": \"Tue\", \"days[2]\": \"Wed\",\n"
            + "\"rank\": \"{n, selectordinal, one{#st} two{#nd} few{#rd} other{#th}}\",\n"
            + "\"share\": \"{p, number, percent} of {total, number}\",\n"
            + "\"stamp\": \"{d, date, short}\",\n"
            + "\"others\": \"{n, plural, offset:1 =0{Nobody} =1{Only you} one{You and # other} other{You and # others}}\"}";
    private static final String ARB_RICH_PT = "{\"@@locale\": \"pt\",\n"
            + "\"days[0]\": \"Seg\", \"days[1]\": \"Ter\", \"days[2]\": \"Qua\",\n"
            + "\"rank\": \"{n, selectordinal, other{#o}}\", \"share\": \"{p, number, percent} de {total, number}\",\n"
            + "\"stamp\": \"{d, date, long}\"," + "\"others\": \"{n, plural, offset:1 =0{Ninguem} =1{So voce} one{Voce e # outro} other{Voce e # outros}}\"}";

    @Test
    void theGeneratedStringsHasTheTypedMethodsOfArraysOrdinalsOffsetsAndStyles() throws Exception {
        JxmlCatalog catalog = JxmlCatalog.load(l10n("app_en.arb", ARB_RICH, "app_pt.arb", ARB_RICH_PT));
        assertEquals("p: num, total: num", catalog.signature("share").describe());
        assertEquals("d: DateTime", catalog.signature("stamp").describe());
        assertEquals("n: int", catalog.signature("rank").describe());
        Map<String, String> screens = new LinkedHashMap<String, String>();
        screens.put("Screen", screen("<Label text=\"%{days[1]}\"/>\n<Label text=\"%{rank(${count})}\"/>\n<Label text=\"%{share(0.25, ${price})}\"/>\n"
                + "<Label text=\"%{stamp(${when})}\"/>\n<Label text=\"%{others(${count})}\"/>\n<ComboBox items=\"%{days[0]}, %{days[1]}, plain\"/>"));
        for (boolean hoist : new boolean[] {true, false}) {
            Built built = new Built(catalog, screens, hoist);
            Class<?> strings = built.strings();
            assertEquals(Arrays.asList("Mon", "Tue", "Wed"), Arrays.asList((String[]) strings.getMethod("days").invoke(null)));
            assertEquals("Tue", strings.getMethod("days_1_").invoke(null));
            assertEquals("22nd", strings.getMethod("rank", int.class).invoke(null, 22));
            assertEquals(NumberFormat.getPercentInstance(Locale.ENGLISH).format(0.5) + " of " + NumberFormat.getInstance(Locale.ENGLISH).format(1234.5),
                    strings.getMethod("share", double.class, double.class).invoke(null, 0.5, 1234.5));
            Date moment = new Date(1700000000000L);
            assertEquals(DateFormat.getDateInstance(DateFormat.SHORT, Locale.ENGLISH).format(moment), strings.getMethod("stamp", Date.class).invoke(null, moment));
            assertEquals("Only you", strings.getMethod("others", int.class).invoke(null, 1));
            assertEquals("You and 1 other", strings.getMethod("others", int.class).invoke(null, 2));
            assertEquals("You and 4 others", strings.getMethod("others", int.class).invoke(null, 5));
            built.setLocale(PT);
            assertEquals(Arrays.asList("Seg", "Ter", "Qua"), Arrays.asList((String[]) strings.getMethod("days").invoke(null)));
            assertEquals("Voce e 2 outros", strings.getMethod("others", int.class).invoke(null, 3));
            assertEquals("22o", strings.getMethod("rank", int.class).invoke(null, 22));
            assertEquals(DateFormat.getDateInstance(DateFormat.LONG, PT).format(moment), strings.getMethod("stamp", Date.class).invoke(null, moment));
            // the interpreter and the generated screen show the same
            for (Locale locale : new Locale[] {Locale.ENGLISH, PT}) {
                built.setLocale(locale);
                catalog.messages().setLocale(locale);
                L10nController c = new L10nController();
                JXElement expected = built.templates.get("Screen").build(c);
                assertNull(JxmlTrees.diff(expected, built.view("Screen").build(c)), hoist + " " + locale);
            }
            built.setLocale(Locale.ENGLISH);
            catalog.messages().setLocale(Locale.ENGLISH);
            List<String> labels = values(built.templates.get("Screen").build(new L10nController()));
            assertEquals("Tue", labels.get(0));
            assertEquals("2nd", labels.get(1));
            assertEquals("You and 1 other", labels.get(4));
        }
    }

    // ---- FXML resource keys against a catalog ----------------------------------------------------------

    private static final String FXML = "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + SampleController.class.getName() + "\">\n"
            + "<TabPane><Tab text=\"%tabOne\"><Label text=\"%colId\" /></Tab></TabPane>\n"
            + "<TableView fx:id=\"rows\"><columns><TableColumn text=\"%colId\"/><TableColumn text=\"Fixed\"/><TableColumn text=\"%colQty\"/></columns></TableView>\n"
            + "</VBox>";

    @Test
    void anFxmlScreenWithResourceKeysBuildsTheTranslatedTreeBothWays() throws Exception {
        JxmlCatalog catalog = JxmlCatalog.load(l10n("app_en.arb", ARB_EN, "app_pt.arb", ARB_PT));
        catalog.messages().setLocale(PT);
        JxmlTemplate template = read(FXML, "t.fxml", catalog);
        JXElement root = template.build(new SampleController());
        assertEquals(Arrays.asList("Primeiro"), Arrays.asList((String[]) root.getChildren().get(0).getProps().get("titles")));
        assertEquals(Arrays.asList("Codigo", "Fixed", "Quantidade"), Arrays.asList((String[]) root.getChildren().get(1).getProps().get("columns")));
        assertEquals("Codigo", root.getChildren().get(0).getChildren().get(0).getProps().get("value"));
    }

    @Test
    void fxmlToJxmlPrintsTheKeyAsATranslationAndChecksItAgainstTheCatalog() throws Exception {
        File l10n = l10n("app_en.arb", "{\"@@locale\": \"en\", \"tabOne\": \"First\", \"colId\": \"Id\", \"colQty\": \"Quantity\","
                + " \"hello\": \"Hello {name}\", \"@hello\": {\"placeholders\": {\"name\": {\"type\": \"String\"}}}}");
        Path fxml = dir.resolve("screen.fxml");
        Files.write(fxml, FXML.getBytes(StandardCharsets.UTF_8));
        List<String> warnings = new ArrayList<String>();
        String jxml = JXForge.fxmlToJxml(fxml.toFile(), warnings, l10n);
        assertTrue(jxml.contains("<Tab title=\"%{tabOne}\">") && jxml.contains("columns=\"%{colId}, Fixed, %{colQty}\""), jxml);
        assertEquals(jxml, JXForge.fxmlToJxml(fxml.toFile(), null, null)); // without a catalog the keys are printed unchecked
        // the same through the command line
        Path out = dir.resolve("screen.jxml");
        JXForge.main(new String[] {"--fxml-to-jxml", fxml.toString(), "--l10n", l10n.getPath(), out.toString()});
        assertEquals(jxml, new String(Files.readAllBytes(out), StandardCharsets.UTF_8));
        // and the printed JXML reads with the same catalog to the same tree as the FXML does
        JxmlCatalog catalog = JxmlCatalog.load(l10n);
        catalog.messages().setLocale(Locale.ENGLISH);
        Files.write(dir.resolve("copy.jxml"), jxml.getBytes(StandardCharsets.UTF_8));
        assertNull(JxmlTrees.diff(read(FXML, "t.fxml", catalog).build(new SampleController()),
                read(jxml, "copy.jxml", catalog).build(new SampleController())));

        Path missing = dir.resolve("missing.fxml");
        Files.write(missing, ("<VBox xmlns:fx=\"http://javafx.com/fxml\">\n<Label text=\"%colId\"/>\n<Label text=\"%nope\"/>\n</VBox>").getBytes(StandardCharsets.UTF_8));
        JxmlException e = assertThrows(JxmlException.class, () -> JXForge.fxmlToJxml(missing.toFile(), null, l10n));
        assertTrue(e.getMessage().startsWith("missing.fxml:3: ") && e.getMessage().contains("%nope") && e.getMessage().contains("not defined in the default locale en"),
                e.getMessage());
        assertTrue(JXForge.fxmlToJxml(missing.toFile(), null, null).contains("%{nope}")); // no catalog, no check
        Path args = dir.resolve("args.fxml");
        Files.write(args, ("<VBox xmlns:fx=\"http://javafx.com/fxml\">\n<Label text=\"%hello\"/>\n</VBox>").getBytes(StandardCharsets.UTF_8));
        e = assertThrows(JxmlException.class, () -> JXForge.fxmlToJxml(args.toFile(), null, l10n));
        assertTrue(e.getMessage().startsWith("args.fxml:2: ") && e.getMessage().contains("takes arguments") && e.getMessage().contains("name: String"), e.getMessage());
    }

    @Test
    void anFxmlDirectoryWithKeysGeneratesStringsAndRegeneratesOnlyWhatChanged() throws Exception {
        File src = dir.resolve("fxsrc").toFile();
        File out = dir.resolve("fxout").toFile();
        Files.createDirectories(src.toPath());
        Files.write(src.toPath().resolve("main.fxml"), FXML.getBytes(StandardCharsets.UTF_8));
        File l10n = l10n("app_en.arb", ARB_EN, "app_pt.arb", ARB_PT);
        JXForge.Result first = JXForge.compileDirectory(src, out, "gen", "View", true, l10n);
        assertEquals(2, first.written.size(), first.written.toString()); // MainView and Strings
        assertTrue(new String(Files.readAllBytes(new File(out, "gen/MainView.java").toPath()), StandardCharsets.UTF_8).contains("Strings.tabOne()"));
        Files.write(new File(l10n, "app_pt.arb").toPath(), ARB_PT.replace("Primeiro", "Primeira").getBytes(StandardCharsets.UTF_8));
        JXForge.Result second = JXForge.compileDirectory(src, out, "gen", "View", true, l10n);
        assertEquals(1, second.written.size(), second.written.toString()); // only Strings: the screen names methods, not texts
        assertEquals("Strings.java", second.written.get(0).getName());
        assertEquals(1, second.upToDate.size());
    }

    // ---- JxmlLiveView with a catalog -----------------------------------------------------------------

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 15000;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < end, "timed out waiting for " + what);
            Thread.sleep(20);
        }
    }

    private File liveScreen() throws IOException {
        Path screen = dir.resolve("live/screen.jxml");
        Files.createDirectories(screen.getParent());
        Files.write(screen, ("<Column controller=\"" + CTRL + "\">\n<Label text=\"%{tabOne}\"/>\n<Label text=\"%{greet(${user.name})}\"/>\n</Column>")
                .getBytes(StandardCharsets.UTF_8));
        return screen.toFile();
    }

    private static final String LIVE_EN = "{\"@@locale\": \"en\", \"tabOne\": \"First\", \"greet\": \"Hi {who}\", "
            + "\"@greet\": {\"placeholders\": {\"who\": {\"type\": \"String\"}}}}";
    private static final String LIVE_PT = "{\"@@locale\": \"pt\", \"tabOne\": \"Primeiro\", \"greet\": \"Oi {who}\", "
            + "\"@greet\": {\"placeholders\": {\"who\": {\"type\": \"String\"}}}}";

    private File liveL10n() throws IOException {
        File l10n = dir.resolve("live/l10n").toFile();
        Files.createDirectories(l10n.toPath());
        Files.write(new File(l10n, "app_en.arb").toPath(), LIVE_EN.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(l10n, "app_pt.arb").toPath(), LIVE_PT.getBytes(StandardCharsets.UTF_8));
        return l10n;
    }

    @Test
    void aLiveViewReadsTheCatalogWithTheScreenAndKeepsTheLocaleWhenTheTextsAreReloaded() throws Exception {
        File l10n = liveL10n();
        try (JxmlLiveView live = JxmlLiveView.open(liveScreen(), l10n)) {
            live.setLocale(Locale.ENGLISH);
            assertEquals(Arrays.asList("First", "Hi Ada"), values(live.build(new L10nController())));
            assertNotNull(live.catalog());
            assertEquals(Arrays.asList("en", "pt"), live.catalog().tags());
            live.setLocale(PT);
            assertEquals(Arrays.asList("Primeiro", "Oi Ada"), values(live.build(new L10nController())));
            long version = live.version();
            Files.write(new File(l10n, "app_pt.arb").toPath(), LIVE_PT.replace("Primeiro", "Primeira").getBytes(StandardCharsets.UTF_8));
            assertTrue(live.reload());
            assertEquals(version + 1, live.version());
            assertEquals(PT, live.locale());
            assertEquals(Arrays.asList("Primeira", "Oi Ada"), values(live.build(new L10nController()))); // still Portuguese
            live.setLocale(Locale.ENGLISH);
            assertEquals(Arrays.asList("First", "Hi Ada"), values(live.build(new L10nController())));
        }
    }

    @Test
    void aMistakeInTheTextsOrAKeyTheScreenNeedsKeepsTheLastGoodScreen() throws Exception {
        File l10n = liveL10n();
        List<JxmlException> errors = java.util.Collections.synchronizedList(new ArrayList<JxmlException>());
        try (JxmlLiveView live = JxmlLiveView.open(liveScreen(), l10n).onError(errors::add)) {
            live.setLocale(PT);
            Files.write(new File(l10n, "app_pt.arb").toPath(), "{\"@@locale\": \"pt\", \"tabOne\": ".getBytes(StandardCharsets.UTF_8));
            assertFalse(live.reload());
            assertTrue(live.lastError().getMessage().contains("app_pt.arb"), live.lastError().getMessage());
            assertEquals(Arrays.asList("Primeiro", "Oi Ada"), values(live.build(new L10nController()))); // the last good one, in Portuguese
            assertEquals(1, errors.size());
            Files.write(new File(l10n, "app_pt.arb").toPath(), LIVE_PT.getBytes(StandardCharsets.UTF_8));
            assertTrue(live.reload());
            assertNull(live.lastError());
            // the default locale loses a key that the screen uses: the error is at the screen's line
            Files.write(new File(l10n, "app_en.arb").toPath(), "{\"@@locale\": \"en\", \"greet\": \"Hi {who}\", \"@greet\": {\"placeholders\": {\"who\": {\"type\": \"String\"}}}}"
                    .getBytes(StandardCharsets.UTF_8));
            assertFalse(live.reload());
            assertTrue(live.lastError().getMessage().contains("screen.jxml:2: ") && live.lastError().getMessage().contains("tabOne"), live.lastError().getMessage());
            assertEquals(Arrays.asList("Primeiro", "Oi Ada"), values(live.build(new L10nController())));
        }
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlLiveView.open(liveScreen(), dir.resolve("nowhere").toFile()));
        assertTrue(e.getMessage().contains("l10n directory does not exist"), e.getMessage());
    }

    @Test
    void theWatcherReloadsWhenAnL10nFileChangesAndWhenALocaleFileOrFolderIsAdded() throws Exception {
        File l10n = liveL10n();
        File androidDir = new File(l10n, "values-es");
        AtomicInteger reloads = new AtomicInteger();
        try (JxmlLiveView live = JxmlLiveView.open(liveScreen(), l10n, 30).onReload(reloads::incrementAndGet).start()) {
            live.setLocale(Locale.ENGLISH);
            int base = reloads.get(); // setLocale tells the listeners too
            Files.write(new File(l10n, "app_en.arb").toPath(), LIVE_EN.replace("First", "Initial").getBytes(StandardCharsets.UTF_8));
            await("the edit of an l10n file", () -> reloads.get() > base);
            assertEquals("Initial", values(live.build(new L10nController())).get(0));
            assertEquals(Locale.ENGLISH, live.locale());
            int afterEdit = reloads.get();
            Files.write(new File(l10n, "app_fr.arb").toPath(), ("{\"@@locale\": \"fr\", \"tabOne\": \"Premier\", \"greet\": \"Salut {who}\", "
                    + "\"@greet\": {\"placeholders\": {\"who\": {\"type\": \"String\"}}}}").getBytes(StandardCharsets.UTF_8));
            await("a new locale file", () -> reloads.get() > afterEdit);
            assertEquals(Arrays.asList("en", "fr", "pt"), live.catalog().tags());
            live.setLocale(Locale.FRENCH);
            assertEquals("Premier", values(live.build(new L10nController())).get(0));
            int afterFile = reloads.get();
            assertTrue(androidDir.mkdirs());
            await("a new values folder", () -> reloads.get() > afterFile); // the folder is registered by this read
            int afterFolder = reloads.get();
            Files.write(new File(androidDir, "strings.xml").toPath(),
                    "<resources><string name=\"tabOne\">Primero</string></resources>".getBytes(StandardCharsets.UTF_8));
            await("a file in the new folder", () -> reloads.get() > afterFolder && live.catalog().tags().contains("es"));
            live.setLocale(new Locale("es"));
            assertEquals(Arrays.asList("Primero", "Hi Ada"), values(live.build(new L10nController()))); // greet falls back to the default
        }
    }

    @Test
    void aLiveViewWithoutAnL10nDirectoryHasNoCatalogAndIgnoresSetLocale() throws Exception {
        Path screen = dir.resolve("plain.jxml");
        Files.write(screen, "<Column>\n<Label text=\"x\"/>\n</Column>".getBytes(StandardCharsets.UTF_8));
        try (JxmlLiveView live = JxmlLiveView.open(screen.toFile())) {
            assertNull(live.catalog());
            assertNull(live.locale());
            live.setLocale(PT);
            assertNull(live.locale());
            assertEquals(Arrays.asList("x"), values(live.build(null)));
        }
    }
}
