package com.jxparallel.ui.resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Decodes picture files (PNG, JPEG, and the first frame of GIF and BMP, among the formats stb_image reads) into
 * the ARGB pixels an {@code Image} shows. stb_image (through {@code lwjgl-stb}) is the one decoder because it
 * needs neither AWT nor a graphics context, so it runs the same for Skia, NanoVG and the software painter,
 * on Java 8 and on 32-bit; SVG is not read.
 *
 * <p>Every method may be called from any thread. The native memory stb_image allocates is freed before a
 * method returns; what stays is the {@code int[]} of the {@link JXImage}.
 *
 * <p>Pictures loaded by path, by resource or by {@linkplain #find(String) source} are kept in a small cache
 * (64 pictures and 64 MB of pixels unless {@link #setCacheLimits} says otherwise, least recently used out
 * first), so the same file is decoded once and the renderers see one pixel array for it. The cache does not
 * watch the file: {@link #invalidate} or {@link #clear} makes the next load read it again. A source that failed
 * is remembered too (it is not read again and reported once) until it is invalidated. Loading from a stream
 * or an array is not cached. An entry is kept under the spelling of its source: {@code load(Path)},
 * {@code file:<same absolute path>} and a resource loaded as {@code classpath:x.png} are each one entry, but
 * the same spelling always gives the same {@link JXImage}.
 *
 * <p>A <em>source</em> is a string: {@code classpath:dir/logo.png}, {@code file:/home/me/logo.png}, or a plain
 * name, which is a class path resource if one has that name and else a file path.
 */
public final class JXImages {
    /** Largest file read, in bytes. */
    static final int MAX_FILE_BYTES = 64 << 20;
    /** Largest picture decoded, in pixels. */
    static final long MAX_PIXELS = 1L << 25;

    private static final Object LOCK = new Object();
    private static final Map<String, JXImage> CACHE = new LinkedHashMap<String, JXImage>(16, 0.75f, true);
    private static final Map<String, Failure> FAILURES = new LinkedHashMap<String, Failure>();
    private static final int MAX_FAILURES = 256;
    private static int maxEntries = 64;
    private static long maxBytes = 64L << 20;
    private static long cachedBytes;
    private static ExecutorService loader;

    private JXImages() {
    }

    /** What reads the encoded bytes of a source. */
    private interface Bytes {
        byte[] read() throws IOException;
    }

    private static final class Failure {
        final String message;
        boolean reported;

        Failure(String message) {
            this.message = message;
        }
    }

    // ---- loading -----------------------------------------------------------------------------

    /** Decodes the file at this path (cached). */
    public static JXImage load(final Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        return cached("file:" + path.toAbsolutePath().normalize(), new Bytes() {
            @Override
            public byte[] read() throws IOException {
                if (Files.size(path) > MAX_FILE_BYTES) {
                    throw new IOException("larger than " + (MAX_FILE_BYTES >> 20) + " MB");
                }
                return Files.readAllBytes(path);
            }
        });
    }

    /** Decodes a picture read from the stream (not cached); the stream is read to its end and left open. */
    public static JXImage load(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input is null");
        }
        return decode(readAll(input));
    }

    /** Decodes encoded picture bytes (not cached). */
    public static JXImage load(byte[] encoded) throws IOException {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded is null");
        }
        return decode(encoded);
    }

    /**
     * Decodes a class path resource found the way {@link Class#getResourceAsStream} finds it: a name with a
     * leading {@code /} is absolute, any other is relative to the package of {@code owner} (cached).
     */
    public static JXImage loadResource(Class<?> owner, String name) throws IOException {
        if (owner == null || name == null) {
            throw new IllegalArgumentException("owner and name are required");
        }
        String absolute = name.startsWith("/") ? name.substring(1) : packagePath(owner) + name;
        return loadResource(owner.getClassLoader(), absolute);
    }

    /** Decodes the class path resource of this name (a path with {@code /}, no leading one) (cached). */
    public static JXImage loadResource(final ClassLoader classLoader, String name) throws IOException {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        final String resource = name.startsWith("/") ? name.substring(1) : name;
        return cached("classpath:" + resource, new Bytes() {
            @Override
            public byte[] read() throws IOException {
                InputStream in = open(classLoader, resource);
                if (in == null) {
                    throw new IOException("no class path resource " + resource);
                }
                try {
                    return readAll(in);
                } finally {
                    in.close();
                }
            }
        });
    }

    /** Decodes the picture a source names (see the class comment); cached. */
    public static JXImage load(final String source) throws IOException {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("source is empty");
        }
        final String name = source.trim();
        return cached(name, new Bytes() {
            @Override
            public byte[] read() throws IOException {
                if (name.startsWith("classpath:")) {
                    return resource(name.substring("classpath:".length()));
                }
                if (name.startsWith("file:")) {
                    return file(name.substring("file:".length()));
                }
                InputStream in = open(null, name.startsWith("/") ? name.substring(1) : name);
                if (in != null) {
                    try {
                        return readAll(in);
                    } finally {
                        in.close();
                    }
                }
                return file(name);
            }
        });
    }

    /**
     * Like {@link #load(String)} for the screen: null, not an exception, when the source is empty or cannot be
     * read or decoded; the first failure of a source is reported on {@code System.err} ("JXParallel: ...",
     * as the other native messages), later ones are not.
     */
    public static JXImage find(String source) {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        try {
            return load(source);
        } catch (IOException e) {
            reportOnce(source.trim());
            return null;
        } catch (RuntimeException e) {
            reportOnce(source.trim());
            return null;
        }
    }

    /** Loads and decodes a source on a background thread, so the first frame that shows it finds it ready. */
    public static CompletableFuture<JXImage> preload(final String source) {
        final CompletableFuture<JXImage> result = new CompletableFuture<JXImage>();
        executor().execute(new Runnable() {
            @Override
            public void run() {
                result.complete(find(source));
            }
        });
        return result;
    }

    // ---- cache -------------------------------------------------------------------------------

    /** Forgets a source (or a failure) so that it is read again; the key is as {@link #load(String)} takes it, or {@code file:<absolute path>}. */
    public static void invalidate(String source) {
        synchronized (LOCK) {
            String key = source == null ? null : source.trim();
            JXImage removed = CACHE.remove(key);
            if (removed != null) {
                cachedBytes -= removed.bytes();
            }
            FAILURES.remove(key);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            CACHE.clear();
            FAILURES.clear();
            cachedBytes = 0;
        }
    }

    /** Sets how many pictures and how many bytes of pixels the cache keeps. */
    public static void setCacheLimits(int entries, long bytes) {
        synchronized (LOCK) {
            maxEntries = Math.max(1, entries);
            maxBytes = Math.max(1L, bytes);
            trim();
        }
    }

    public static int cacheSize() {
        synchronized (LOCK) {
            return CACHE.size();
        }
    }

    public static long cacheBytes() {
        synchronized (LOCK) {
            return cachedBytes;
        }
    }

    private static JXImage cached(String key, Bytes bytes) throws IOException {
        synchronized (LOCK) {
            JXImage hit = CACHE.get(key);
            if (hit != null) {
                return hit;
            }
            Failure failed = FAILURES.get(key);
            if (failed != null) {
                throw new IOException(failed.message);
            }
        }
        JXImage image;
        try {
            image = decode(bytes.read()); // outside the lock: a slow file does not stop the others
        } catch (IOException e) {
            fail(key, e);
            throw e;
        } catch (RuntimeException e) {
            fail(key, e);
            throw e;
        }
        synchronized (LOCK) {
            JXImage other = CACHE.get(key);
            if (other != null) {
                return other; // another thread decoded it meanwhile: one array for everybody
            }
            if (image.bytes() <= maxBytes) {
                CACHE.put(key, image);
                cachedBytes += image.bytes();
                trim();
            }
        }
        return image;
    }

    private static void fail(String key, Exception e) {
        synchronized (LOCK) {
            if (FAILURES.size() >= MAX_FAILURES) {
                FAILURES.remove(FAILURES.keySet().iterator().next());
            }
            FAILURES.put(key, new Failure(key + ": " + e.getMessage()));
        }
    }

    private static void reportOnce(String key) {
        String message = null;
        synchronized (LOCK) {
            Failure failure = FAILURES.get(key);
            if (failure != null && !failure.reported) {
                failure.reported = true;
                message = failure.message;
            }
        }
        if (message != null) {
            System.err.println("JXParallel: cannot load image " + message);
        }
    }

    /** Caller holds the lock. */
    private static void trim() {
        while (CACHE.size() > maxEntries || cachedBytes > maxBytes) {
            String eldest = CACHE.keySet().iterator().next();
            cachedBytes -= CACHE.remove(eldest).bytes();
        }
    }

    // ---- reading and decoding ----------------------------------------------------------------

    private static byte[] resource(String name) throws IOException {
        InputStream in = open(null, name.startsWith("/") ? name.substring(1) : name);
        if (in == null) {
            throw new IOException("no class path resource " + name);
        }
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    private static byte[] file(String name) throws IOException {
        File file = new File(name);
        if (!file.isFile()) {
            throw new IOException("no file " + file.getAbsolutePath());
        }
        if (file.length() > MAX_FILE_BYTES) {
            throw new IOException("larger than " + (MAX_FILE_BYTES >> 20) + " MB");
        }
        return Files.readAllBytes(file.toPath());
    }

    /** The resource from {@code classLoader}, or when that is null from the context and then this library's loader. */
    private static InputStream open(ClassLoader classLoader, String name) {
        if (classLoader != null) {
            return classLoader.getResourceAsStream(name);
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        InputStream in = context == null ? null : context.getResourceAsStream(name);
        return in != null ? in : JXImages.class.getClassLoader().getResourceAsStream(name);
    }

    private static String packagePath(Class<?> owner) {
        String name = owner.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(0, dot).replace('.', '/') + "/";
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) > 0) {
            out.write(chunk, 0, n);
            if (out.size() > MAX_FILE_BYTES) {
                throw new IOException("larger than " + (MAX_FILE_BYTES >> 20) + " MB");
            }
        }
        return out.toByteArray();
    }

    private static JXImage decode(byte[] encoded) throws IOException {
        if (encoded.length == 0) {
            throw new IOException("empty file");
        }
        ByteBuffer data = MemoryUtil.memAlloc(encoded.length);
        ByteBuffer rgba = null;
        try {
            data.put(encoded).flip();
            MemoryStack stack = MemoryStack.stackPush();
            try {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);
                // always 4 channels: whatever the file has (gray, RGB, palette...) comes out as RGBA
                rgba = STBImage.stbi_load_from_memory(data, w, h, comp, 4);
                if (rgba == null) {
                    String reason = STBImage.stbi_failure_reason();
                    throw new IOException("not a picture this decoder reads (PNG, JPEG, GIF, BMP...): "
                            + (reason == null ? "unknown error" : reason));
                }
                int width = w.get(0);
                int height = h.get(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("picture of " + width + "x" + height + " is too large");
                }
                int[] pixels = new int[width * height];
                for (int i = 0, o = 0; i < pixels.length; i++, o += 4) {
                    int r = rgba.get(o) & 0xFF;
                    int g = rgba.get(o + 1) & 0xFF;
                    int b = rgba.get(o + 2) & 0xFF;
                    int a = rgba.get(o + 3) & 0xFF;
                    pixels[i] = a << 24 | r << 16 | g << 8 | b;
                }
                return new JXImage(width, height, pixels);
            } finally {
                stack.close();
            }
        } finally {
            if (rgba != null) {
                STBImage.stbi_image_free(rgba);
            }
            MemoryUtil.memFree(data);
        }
    }

    private static synchronized ExecutorService executor() {
        if (loader == null) {
            loader = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "jx-image-loader");
                    thread.setDaemon(true);
                    return thread;
                }
            });
        }
        return loader;
    }
}
