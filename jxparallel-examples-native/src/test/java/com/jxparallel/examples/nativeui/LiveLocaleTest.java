package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.forge.JxmlCatalog;
import com.jxparallel.forge.JxmlLive;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.native2d.JXNativeNode;

/** Translations in a live screen: a locale change and a changed argument write the new text in place; nothing else formats. */
class LiveLocaleTest {
    private static final String CTRL = "com.jxparallel.examples.nativeui.LiveModel";
    private static final String ITEM = CTRL + ".Item";
    private static final String EN = "{\"@@locale\": \"en\", \"hello\": \"Hello\", \"greet\": \"Hello {name}\", \"count\": \"{n} items\"}";
    private static final String PT = "{\"@@locale\": \"pt\", \"hello\": \"Ola\", \"greet\": \"Ola {name}, tudo bem\", \"count\": \"{n} itens\"}";
    private static final Locale PTL = new Locale("pt");

    @TempDir
    Path dir;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void aLocaleChangeAndAChangedArgumentAreWrittenInPlaceAndAnUnchangedMessageIsNotFormattedAgain() throws Exception {
        File l10n = dir.resolve("l10n").toFile();
        Files.createDirectories(l10n.toPath());
        Files.write(l10n.toPath().resolve("app_en.arb"), EN.getBytes(StandardCharsets.UTF_8));
        Files.write(l10n.toPath().resolve("app_pt.arb"), PT.getBytes(StandardCharsets.UTF_8));
        JxmlCatalog catalog = JxmlCatalog.load(l10n);
        String xml = "<Column controller=\"" + CTRL + "\">\n<Label text=\"%{hello}\"/>\n<Label text=\"%{greet(${title})}\"/>\n"
                + "<For items=\"${items}\" var=\"i\" type=\"" + ITEM + "\"><Label text=\"%{greet(${i.name})}\"/></For>\n"
                + "<For items=\"${amounts}\" var=\"a\" type=\"java.lang.Double\"><Label text=\"%{count(${a})}\"/></For>\n</Column>";
        LiveCompile.Built built = LiveCompile.compile(xml, catalog);
        built.setLocale(Locale.ENGLISH);
        JxmlView view = built.view;
        LiveModel model = new LiveModel();
        JxmlLive live = view.live(model);
        JXNativeNode node = ForgeLoadBenchmark.ready(live.element());
        assertTrue(live.attach(node), live.problem());

        built.setLocale(PTL);
        assertTrue(live.refresh());
        node.layoutForBackend(LiveSupport.W, LiveSupport.H);
        assertEquals(0, live.fallbacks(), "a locale change is written in place");
        assertTrue(live.changes() > 0);
        assertEquals("Ola", node.getChildren().get(0).getProperty("value"));
        assertNull(LiveSupport.firstDifference(LiveSupport.describe(ForgeLoadBenchmark.ready(view.build(model))), LiveSupport.describe(node)));

        model.setTitle("Grace");
        model.getItems().get(2).edit("Linus", 1);
        assertTrue(live.refresh());
        node.layoutForBackend(LiveSupport.W, LiveSupport.H);
        assertEquals(0, live.fallbacks());
        assertEquals("Ola Grace, tudo bem", node.getChildren().get(1).getProperty("value"));
        assertEquals("Ola Linus, tudo bem", node.getChildren().get(4).getProperty("value"));
        assertArrayEquals(LiveSupport.paint(ForgeLoadBenchmark.ready(view.build(model))), LiveSupport.paint(node));

        built.setLocale(Locale.ENGLISH);
        assertTrue(live.refresh());
        node.layoutForBackend(LiveSupport.W, LiveSupport.H);
        assertEquals(0, live.fallbacks());
        assertNull(LiveSupport.firstDifference(LiveSupport.describe(ForgeLoadBenchmark.ready(view.build(model))), LiveSupport.describe(node)));

        // nothing changed: the messages are not made again (formatting a message allocates, a refresh that finds nothing does not)
        for (int i = 0; i < 2000; i++) {
            live.refresh();
        }
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
        for (int i = 0; i < 200; i++) {
            live.refresh();
        }
        long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
        System.out.println("LIVE translated screen, nothing changed: " + bytes / 200 + " B per refresh (" + live.describe() + ")");
        assertTrue(bytes / 200 < 2048, "bytes per refresh " + bytes / 200);
    }
}
