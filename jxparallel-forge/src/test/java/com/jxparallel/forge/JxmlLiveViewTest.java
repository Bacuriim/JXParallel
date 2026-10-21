package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.ui.JXElement;

/** Development mode: the file is read again when it is saved, a mistake keeps the last good screen. */
class JxmlLiveViewTest {
    private static final long WAIT_MILLIS = 15000;

    @TempDir
    Path dir;

    private File write(String name, String text) throws IOException {
        Path path = dir.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        return path.toFile();
    }

    private static String screen(String text) {
        return "<Column>\n<Label text=\"" + text + "\"/>\n</Column>";
    }

    private static String labelText(JxmlView<Object> view) {
        JXElement root = view.build(null);
        return String.valueOf(root.getChildren().get(0).getProps().get("value"));
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + WAIT_MILLIS;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < end, "timed out waiting for " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void aSavedEditShowsInTheNextBuild() throws Exception {
        File file = write("screen.jxml", screen("one"));
        try (JxmlLiveView live = JxmlLiveView.open(file)) {
            assertEquals("one", labelText(live));
            assertEquals(1, live.version());
            write("screen.jxml", screen("two"));
            assertTrue(live.reload());
            assertEquals("two", labelText(live));
            assertEquals(2, live.version());
            assertNull(live.lastError());
        }
    }

    @Test
    void theLiveViewBuildsTheTreeTheTemplateBuilds() throws Exception {
        File file = write("screen.jxml", "<Column gap=\"4\">\n<Label text=\"a\"/>\n<Button text=\"b\"/>\n</Column>");
        try (JxmlLiveView live = JxmlLiveView.open(file)) {
            assertNull(JxmlTrees.diff(JxmlTemplate.read(file).build(null), live.build(null)));
            assertSame(live.current(), live.current());
        }
    }

    @Test
    void aHalfWrittenFileKeepsTheLastGoodScreenAndReportsTheErrorWithFileAndLine() throws Exception {
        File file = write("screen.jxml", screen("one"));
        List<JxmlException> errors = Collections.synchronizedList(new ArrayList<JxmlException>());
        try (JxmlLiveView live = JxmlLiveView.open(file).onError(errors::add)) {
            JxmlTemplate good = live.current();
            write("screen.jxml", "<Column>\n<Label text=\"tw");
            assertFalse(live.reload());
            assertSame(good, live.current(), "the last good template stays");
            assertEquals("one", labelText(live));
            assertEquals(1, live.version());
            assertEquals(1, errors.size());
            JxmlException error = errors.get(0);
            assertSame(error, live.lastError());
            assertTrue(error.getSource().endsWith("screen.jxml"), error.getSource());
            assertTrue(error.getLine() >= 2, "line " + error.getLine());
            assertTrue(error.getMessage().contains("screen.jxml:" + error.getLine()), error.getMessage());
        }
    }

    @Test
    void aMistakeInATagIsReportedAtItsLineAndTheNextGoodSaveClearsIt() throws Exception {
        File file = write("screen.jxml", screen("one"));
        try (JxmlLiveView live = JxmlLiveView.open(file)) {
            write("screen.jxml", "<Column>\n<Label text=\"x\"/>\n<Bogus/>\n</Column>");
            assertFalse(live.reload());
            assertEquals(3, live.lastError().getLine());
            assertTrue(live.lastError().getMessage().contains("unknown tag <Bogus>"), live.lastError().getMessage());
            assertEquals("one", labelText(live));
            write("screen.jxml", screen("three"));
            assertTrue(live.reload());
            assertNull(live.lastError());
            assertEquals("three", labelText(live));
        }
    }

    @Test
    void aFileThatIsMissingOnOpenIsAnError() {
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlLiveView.open(dir.resolve("none.jxml").toFile()));
        assertTrue(e.getMessage().contains("none.jxml"), e.getMessage());
        assertThrows(JxmlException.class, () -> {
            write("broken.jxml", "<Column><Label");
            JxmlLiveView.open(dir.resolve("broken.jxml").toFile());
        });
    }

    @Test
    void anIncludeIsPartOfWhatIsRead() throws Exception {
        write("parts/row.jxml", "<Label text=\"part one\"/>");
        File file = write("screen.jxml", "<Column>\n<Include src=\"parts/row.jxml\"/>\n</Column>");
        try (JxmlLiveView live = JxmlLiveView.open(file)) {
            assertEquals("part one", labelText(live));
            assertTrue(live.files().contains(dir.resolve("parts/row.jxml").toAbsolutePath().normalize()), live.files().toString());
            write("parts/row.jxml", "<Label text=\"part two\"/>");
            assertTrue(live.reload());
            assertEquals("part two", labelText(live));
            write("parts/row.jxml", "<Label text=");
            assertFalse(live.reload());
            assertTrue(live.lastError().getSource().endsWith("row.jxml"), live.lastError().getSource());
            assertEquals("part two", labelText(live));
            assertTrue(live.files().contains(dir.resolve("parts/row.jxml").toAbsolutePath().normalize()),
                    "the include of the last good screen is still watched");
        }
    }

    @Test
    void savingTheFileOnDiskIsSeenByTheWatcherAndTellsTheListeners() throws Exception {
        File file = write("screen.jxml", screen("one"));
        final CountDownLatch reloaded = new CountDownLatch(1);
        try (JxmlLiveView live = JxmlLiveView.open(file, 20).onReload(reloaded::countDown).start()) {
            assertTrue(live.watchedDirectories().contains(dir.toAbsolutePath().normalize()), live.watchedDirectories().toString());
            write("screen.jxml", screen("saved"));
            assertTrue(reloaded.await(WAIT_MILLIS, TimeUnit.MILLISECONDS), "the watcher read the saved file");
            await("the new screen", () -> "saved".equals(labelText(live)));
            assertTrue(live.version() >= 2);
        }
    }

    @Test
    void theWatcherSeesAnEditOfAnIncludeInAnotherFolderAndSurvivesABrokenSave() throws Exception {
        write("parts/row.jxml", "<Label text=\"p1\"/>");
        File file = write("screen.jxml", "<Column>\n<Include src=\"parts/row.jxml\"/>\n</Column>");
        final List<JxmlException> errors = Collections.synchronizedList(new ArrayList<JxmlException>());
        try (JxmlLiveView live = JxmlLiveView.open(file, 20).onError(errors::add).start()) {
            assertTrue(live.watchedDirectories().contains(dir.resolve("parts").toAbsolutePath().normalize()),
                    live.watchedDirectories().toString());
            write("parts/row.jxml", "<Label text=\"p2\"/>");
            await("the include's new text", () -> "p2".equals(labelText(live)));
            write("parts/row.jxml", "<Label text=\"p3\" bogus=\"1\"/>");
            await("the error", () -> live.lastError() != null && live.lastError().getMessage().contains("bogus"));
            assertEquals("p2", labelText(live), "the last good screen stays");
            assertTrue(live.lastError().getSource().endsWith("row.jxml"), live.lastError().getSource());
            assertEquals(1, live.lastError().getLine());
            write("parts/row.jxml", "<Label text=\"p4\"/>");
            await("the fix", () -> "p4".equals(labelText(live)) && live.lastError() == null);
            assertFalse(errors.isEmpty());
        }
    }

    @Test
    void closingStopsTheWatcherAndTheLastScreenStillBuilds() throws Exception {
        File file = write("screen.jxml", screen("one"));
        final AtomicInteger reloads = new AtomicInteger();
        JxmlLiveView live = JxmlLiveView.open(file, 10).onReload(reloads::incrementAndGet).start();
        Thread watcher = null;
        for (Thread t : allThreads()) {
            if (t.getName().equals("jxml-live-screen.jxml")) {
                watcher = t;
            }
        }
        assertNotNull(watcher);
        assertTrue(watcher.isDaemon());
        live.close();
        assertFalse(watcher.isAlive());
        write("screen.jxml", screen("late"));
        Thread.sleep(150);
        assertEquals(0, reloads.get(), "nothing is read after close");
        assertEquals("one", labelText(live));
        live.close(); // twice is fine
        assertSame(live, live.start(), "a closed view does not start again");
        Thread.sleep(50);
        for (Thread t : allThreads()) {
            assertFalse(t.getName().equals("jxml-live-screen.jxml") && t.isAlive(), "no watcher thread after close");
        }
    }

    private static List<Thread> allThreads() {
        return new ArrayList<Thread>(Thread.getAllStackTraces().keySet());
    }

    @Test
    void builderThreadsSeeEitherTheOldOrTheNewScreenWhileItIsReplaced() throws Exception {
        File file = write("screen.jxml", screen("a"));
        try (JxmlLiveView live = JxmlLiveView.open(file)) {
            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                final List<Future<Integer>> readers = new ArrayList<Future<Integer>>();
                final CountDownLatch go = new CountDownLatch(1);
                for (int i = 0; i < 3; i++) {
                    readers.add(pool.submit(() -> {
                        go.await();
                        int seen = 0;
                        for (int n = 0; n < 2000; n++) {
                            String text = labelText(live);
                            assertTrue("a".equals(text) || "b".equals(text) || "c".equals(text), text);
                            seen++;
                        }
                        return Integer.valueOf(seen);
                    }));
                }
                go.countDown();
                for (String text : new String[] {"b", "c", "b", "c"}) {
                    write("screen.jxml", screen(text));
                    assertTrue(live.reload());
                }
                for (Future<Integer> reader : readers) {
                    assertEquals(2000, reader.get(WAIT_MILLIS, TimeUnit.MILLISECONDS).intValue());
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void aListenerThatThrowsDoesNotStopTheOthers() throws Exception {
        File file = write("screen.jxml", screen("one"));
        final AtomicInteger told = new AtomicInteger();
        try (JxmlLiveView live = JxmlLiveView.open(file).onReload(() -> {
            throw new IllegalStateException("listener");
        }).onReload(told::incrementAndGet)) {
            assertTrue(live.reload());
            assertEquals(1, told.get());
        }
    }
}
