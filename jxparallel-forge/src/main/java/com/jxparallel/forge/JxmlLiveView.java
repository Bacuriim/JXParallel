package com.jxparallel.forge;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.jxparallel.ui.JXElement;

/**
 * Development mode: a JXML file read by the interpreter that is read again when it is saved. The application asks
 * this object for the screen every time it renders ({@link #build}), so a saved edit shows on the next render;
 * {@link #onReload} tells it when to render.
 *
 * <pre>{@code
 * JxmlLiveView live = JxmlLiveView.open(new File("src/main/jxml/customers.jxml"))
 *         .onError(e -> System.err.println(e.getMessage()))
 *         .onReload(() -> window.invokeLater(() -> window.setContent(live.build(controller))))
 *         .start();
 * window.setContent(live.build(controller));
 * }</pre>
 *
 * <ul>
 * <li>The file and every file it includes are watched with a {@link WatchService}, on a daemon thread; a save is
 * read after {@code debounceMillis} without another event (editors and operating systems report one save as several
 * events). {@link #reload()} does the same read at once, on the calling thread.</li>
 * <li>A file that cannot be read (half written, a mistake in the tag or an attribute, a missing include) keeps the last
 * good screen: {@link #current()} and {@link #build} go on using it, {@link #lastError()} holds the
 * {@link JxmlException} (the file and line of the mistake) and the {@link #onError} listeners get it. The next good
 * save replaces the screen and clears the error.</li>
 * <li>Thread safe: any thread may call any method. Listeners run on the watcher thread (or on the thread that called
 * {@link #reload()}); a window's {@code invokeLater} brings the work back to the display thread. A listener that throws
 * is ignored.</li>
 * </ul>
 * The screen comes from the interpreter ({@link JxmlTemplate}), exactly the tree the generated class of the same file builds.
 */
public final class JxmlLiveView implements JxmlView<Object>, AutoCloseable {
    /** The time without a new event after which a saved file is read, unless {@link #open(File, long)} says another. */
    public static final long DEBOUNCE_MILLIS = 80;

    private final File file;
    private final File l10nDir;
    private final String name;
    private final long debounceMillis;
    /** The translations the last good read used, or null when the screen has none; the locale is kept across reloads. */
    private volatile JxmlCatalog catalog;
    private volatile Locale locale;
    /** The l10n folders (the directory and the ones inside it): any change in them reads the screen again. */
    private volatile Set<Path> l10nFolders = Collections.emptySet();
    private final CopyOnWriteArrayList<Runnable> reloadListeners = new CopyOnWriteArrayList<Runnable>();
    private final CopyOnWriteArrayList<Consumer<JxmlException>> errorListeners = new CopyOnWriteArrayList<Consumer<JxmlException>>();
    private final Object lock = new Object();
    private volatile JxmlTemplate current;
    private volatile JxmlException lastError;
    private volatile long version;
    /** The files read (the file and its includes), as absolute paths; replaced whole, never changed. */
    private volatile Set<Path> files = Collections.emptySet();
    private volatile boolean closed;
    private WatchService service;
    private Thread thread;
    private final Map<WatchKey, Path> directories = new HashMap<WatchKey, Path>();

    private JxmlLiveView(File file, File l10nDir, long debounceMillis) {
        this.file = file;
        this.l10nDir = l10nDir;
        this.name = file.getPath().replace('\\', '/');
        this.debounceMillis = Math.max(0, debounceMillis);
    }

    /** Reads {@code file} now; a {@link JxmlException} if it cannot be read (there is no good screen to keep yet). */
    public static JxmlLiveView open(File file) {
        return open(file, DEBOUNCE_MILLIS);
    }

    public static JxmlLiveView open(File file, long debounceMillis) {
        return open(file, null, debounceMillis);
    }

    /**
     * As {@link #open(File)}, for a screen with translations ({@code %{key}}): the files of {@code l10nDir} (see
     * {@link JxmlCatalog}) are read with it and watched with it, so a saved edit of a text, a new locale file or a new key
     * shows on the next build. The locale chosen with {@link #setLocale} stays across the reads. An l10n file with a
     * mistake, or a screen that uses a key the files no longer have, keeps the last good screen like any other mistake.
     */
    public static JxmlLiveView open(File file, File l10nDir) {
        return open(file, l10nDir, DEBOUNCE_MILLIS);
    }

    public static JxmlLiveView open(File file, File l10nDir, long debounceMillis) {
        JxmlLiveView view = new JxmlLiveView(file, l10nDir, debounceMillis);
        JxmlException error = view.read();
        if (error != null) {
            throw error;
        }
        return view;
    }

    /** The translations of the last good read (the catalog the screen was built with), or null without an l10n directory. */
    public JxmlCatalog catalog() {
        return catalog;
    }

    /** The locale the texts are in: the best match for the system's until {@link #setLocale} chooses one. */
    public Locale locale() {
        return locale;
    }

    /**
     * Shows the texts in another locale, now and after every later read, and calls the reload listeners so the application
     * builds the screen again. Does nothing without an l10n directory.
     */
    public void setLocale(Locale wanted) {
        if (wanted == null) {
            throw new NullPointerException("locale");
        }
        synchronized (lock) {
            if (catalog == null) {
                return;
            }
            locale = wanted;
            catalog.messages().setLocale(wanted);
        }
        fireReload();
    }

    private void fireReload() {
        for (Runnable listener : reloadListeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // a listener's mistake must not stop the watching
            }
        }
    }

    /** Every folder under {@code dir}, itself included, as absolute paths; and the files in them added to {@code files}. */
    private static void walk(File dir, Set<Path> folders, Set<Path> files) {
        folders.add(dir.toPath().toAbsolutePath().normalize());
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                walk(entry, folders, files);
            } else {
                files.add(entry.toPath().toAbsolutePath().normalize());
            }
        }
    }

    /** The screen as the last good file builds it, with the bindings and handlers of {@code controller}. */
    @Override
    public JXElement build(Object controller) {
        return current.build(controller);
    }

    /** The last good template. */
    public JxmlTemplate current() {
        return current;
    }

    /** How many times a file was read successfully, the first read included. */
    public long version() {
        return version;
    }

    /** The mistake of the last read, or null when it was good. */
    public JxmlException lastError() {
        return lastError;
    }

    /** The files in use (the file itself and its includes), as absolute paths. */
    public Set<Path> files() {
        return files;
    }

    /** {@code listener} runs after each good read that replaced the screen. */
    public JxmlLiveView onReload(Runnable listener) {
        reloadListeners.add(listener);
        return this;
    }

    /** {@code listener} gets the mistake of each read that failed; the last good screen stays. */
    public JxmlLiveView onError(Consumer<JxmlException> listener) {
        errorListeners.add(listener);
        return this;
    }

    /**
     * Reads the file again now. True when the new screen replaced the old one; false when the file could not be read:
     * the old screen stays and {@link #lastError()} says why.
     */
    public boolean reload() {
        return read() == null;
    }

    /** Starts watching the file and its includes; does nothing when already started. */
    public JxmlLiveView start() {
        synchronized (lock) {
            if (thread != null || closed) {
                return this;
            }
            try {
                service = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                throw new JxmlException(name, 0, "cannot watch the file: " + e.getMessage(), e);
            }
            register(files);
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    watch(service);
                }
            }, "jxml-live-" + file.getName());
            thread.setDaemon(true);
            thread.start();
        }
        return this;
    }

    /** Stops watching; the last good screen can still be built. */
    @Override
    public void close() {
        Thread stopped;
        synchronized (lock) {
            closed = true;
            stopped = thread;
            thread = null;
            if (service != null) {
                try {
                    service.close();
                } catch (IOException ignored) {
                    // nothing left to release
                }
            }
        }
        if (stopped != null && stopped != Thread.currentThread()) {
            try {
                stopped.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---- reading -------------------------------------------------------------------------------------

    /** Reads the file; null on success (the screen is replaced and the listeners told), else the mistake. */
    private JxmlException read() {
        final Set<String> opened = new LinkedHashSet<String>();
        JxmlException error = null;
        JxmlTemplate template = null;
        JxmlCatalog loaded = null;
        Set<Path> folders = new LinkedHashSet<Path>();
        Set<Path> l10nFiles = new LinkedHashSet<Path>();
        if (l10nDir != null) {
            walk(l10nDir, folders, l10nFiles); // also when the read fails: fixing a file is how the mistake goes away
        }
        try {
            if (l10nDir != null) {
                if (!l10nDir.isDirectory()) {
                    throw new JxmlException(l10nDir.getPath().replace('\\', '/'), 0, "the l10n directory does not exist");
                }
                loaded = JxmlCatalog.load(l10nDir);
                Locale chosen = locale;
                if (chosen != null) {
                    loaded.messages().setLocale(chosen);
                }
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            JxmlLoader recorder = new JxmlLoader() {
                @Override
                public InputStream open(String part) throws IOException {
                    synchronized (opened) {
                        opened.add(part);
                    }
                    return JxmlLoader.files().open(part);
                }
            };
            template = JxmlTemplate.read(new ByteArrayInputStream(bytes), name, recorder, loaded);
        } catch (JxmlException e) {
            error = e;
        } catch (IOException e) {
            error = new JxmlException(name, 0, "unreadable: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            error = new JxmlException(name, 0, "cannot be read: " + e, e);
        }
        Set<Path> read = new LinkedHashSet<Path>();
        read.add(absolute(name));
        synchronized (opened) {
            for (String part : opened) {
                read.add(absolute(part));
            }
        }
        read.addAll(l10nFiles);
        if (error != null) {
            read.addAll(files); // keep watching what the last good screen used: fixing it is the way out
        }
        synchronized (lock) {
            files = Collections.unmodifiableSet(read);
            l10nFolders = Collections.unmodifiableSet(folders);
            if (error == null) {
                current = template;
                catalog = loaded;
                if (loaded != null) {
                    locale = loaded.messages().locale(); // the first read picks the system's; later reads keep the chosen one
                }
                lastError = null;
                version++;
            } else {
                lastError = error;
            }
            if (service != null && !closed) {
                register(read);
            }
        }
        if (error == null) {
            fireReload();
        } else {
            for (Consumer<JxmlException> listener : errorListeners) {
                try {
                    listener.accept(error);
                } catch (RuntimeException ignored) {
                    // as above
                }
            }
        }
        return error;
    }

    private static Path absolute(String path) {
        return new File(path).toPath().toAbsolutePath().normalize();
    }

    // ---- watching ------------------------------------------------------------------------------------

    /** Watches the folder of every file; the lock is held. */
    private void register(Set<Path> paths) {
        Set<Path> folders = new LinkedHashSet<Path>();
        for (Path path : paths) {
            folders.add(path.getParent());
        }
        folders.addAll(l10nFolders); // a WatchService is not recursive: every folder of the l10n directory is registered
        for (Path directory : folders) {
            if (directory == null || directories.containsValue(directory)) {
                continue;
            }
            try {
                WatchKey key = directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                directories.put(key, directory);
            } catch (IOException ignored) {
                // a folder that is gone is watched again when a read finds the file back
            }
        }
    }

    private void watch(WatchService watcher) {
        try {
            while (!closed) {
                WatchKey key = watcher.take();
                boolean changed = touches(key);
                // one save is several events: read after a quiet moment
                while (!closed) {
                    WatchKey more = watcher.poll(debounceMillis, TimeUnit.MILLISECONDS);
                    if (more == null) {
                        break;
                    }
                    changed |= touches(more);
                }
                if (changed && !closed) {
                    read();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException e) {
            // close()
        }
    }

    /** Whether the events of {@code key} concern one of the files in use; the key is reset. */
    private boolean touches(WatchKey key) {
        Path directory;
        synchronized (lock) {
            directory = directories.get(key);
        }
        boolean touched = false;
        Set<Path> watched = files;
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW || directory == null) {
                touched = true;
            } else if (l10nFolders.contains(directory)) {
                touched = true; // any change in the translations: a new locale file or folder, a key, a text
            } else if (event.context() instanceof Path) {
                touched |= watched.contains(directory.resolve((Path) event.context()).toAbsolutePath().normalize());
            }
        }
        if (!key.reset()) {
            synchronized (lock) {
                directories.remove(key);
            }
        }
        return touched;
    }

    /** The folders being watched (for tests and diagnostics). */
    Set<Path> watchedDirectories() {
        synchronized (lock) {
            return new HashSet<Path>(directories.values());
        }
    }
}
