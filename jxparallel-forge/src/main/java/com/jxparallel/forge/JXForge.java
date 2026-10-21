package com.jxparallel.forge;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compiles a directory of JXML and FXML files into Java sources, one class per file, like {@code protoc}:
 * {@code src/main/jxml/orders/order-list.jxml} becomes {@code <package>.OrderListView}.
 *
 * <p>The run is incremental by content, not by time. The output directory keeps a {@value #MANIFEST} file with, for each
 * screen, the SHA-256 of its own bytes and of every file it includes (transitively), plus one fingerprint of the options
 * (package, suffix, hoist, forge version). On each run only the screens whose key changed, or whose generated file is
 * missing, are compiled again; a generated text equal to the file on disk is not written (so javac sees no change); the
 * generated files of sources that are gone are deleted. The warnings of every screen are kept in the manifest and shown
 * on each run, also for the screens that are up to date. A missing, corrupt or foreign manifest only means that
 * everything is generated again.
 *
 * <p>Used by the Maven plugin, the Gradle plugin and from the command line:
 *
 * <pre>java -cp jxparallel-forge.jar;jxparallel-core.jar com.jxparallel.forge.JXForge
 *     --src src/main/jxml --out target/generated-sources/jxforge --package com.acme.views [--no-hoist]</pre>
 */
public final class JXForge {
    /** The file in the output directory that records what the last run generated from what. */
    public static final String MANIFEST = ".jxforge-manifest";
    private static final String FORMAT = "jxforge-manifest 1";
    private static final String MISSING = "missing";

    /** What one run did. */
    public static final class Result {
        /** The generated files that were created or changed in this run. */
        public final List<File> written = new ArrayList<File>();
        /** The generated files that are current: not compiled again, or compiled to the text they already had. */
        public final List<File> upToDate = new ArrayList<File>();
        /** The generated files deleted because their source is gone (or the class moved, as when the package changed). */
        public final List<File> removed = new ArrayList<File>();
        /** Files that are only parts of others (included, with no root controller): they get no class. */
        public final List<File> parts = new ArrayList<File>();
        /**
         * What the files read but cannot carry over, as {@code file:line: message}: for FXML files, each {@code fx:id}
         * and each handler the controller must offer without arguments. The warnings of the screens that are up to date
         * are the ones recorded in the manifest, so they appear on every run.
         */
        public final List<String> warnings = new ArrayList<String>();
        public long nanos;
    }

    /** What the manifest records about one screen. */
    private static final class Entry {
        String className;
        String output; // relative to the output directory, with /
        String own;
        final Map<String, String> deps = new TreeMap<String, String>();
        final List<String> warnings = new ArrayList<String>();

        boolean sameKey(Entry other) {
            return className.equals(other.className) && output.equals(other.output) && own.equals(other.own)
                    && deps.equals(other.deps);
        }
    }

    /** The manifest of a run: the options fingerprint and the screens, by relative source path. */
    private static final class Manifest {
        String options;
        final Map<String, Entry> screens = new TreeMap<String, Entry>();
    }

    private JXForge() {
    }

    public static Result compileDirectory(File src, File out, String packageName, String suffix, boolean hoist)
            throws IOException {
        long start = System.nanoTime();
        Result result = new Result();
        List<File> files = new ArrayList<File>();
        collect(src, files);
        Set<String> included = new HashSet<String>();
        Set<String> withControllers = new HashSet<String>();
        Map<String, List<String>> direct = new HashMap<String, List<String>>();
        for (File file : files) {
            scan(file, relative(src, file), included, withControllers, direct);
        }
        Map<String, String> hashes = new HashMap<String, String>();
        String options = sha256(packageName + "\n" + suffix + "\n" + hoist + "\n" + version());
        Manifest old = readManifest(new File(out, MANIFEST));
        boolean sameOptions = old != null && options.equals(old.options);
        Manifest now = new Manifest();
        now.options = options;

        JxmlLoader loader = JxmlLoader.directory(src);
        Map<String, File> classes = new HashMap<String, File>();
        Map<String, String> outputs = new HashMap<String, String>(); // screen name -> relative output, failed ones too
        Set<String> outputPaths = new HashSet<String>();
        File packageDir = new File(out, packageName.replace('.', File.separatorChar));
        String packagePath = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
        JxmlException failure = null;
        for (File file : files) {
            String name = relative(src, file);
            if (included.contains(name) && !withControllers.contains(name)) {
                result.parts.add(file); // a part of another screen: its bindings need that screen's controller
                continue;
            }
            String className = JxmlCompiler.className(file.getName(), suffix);
            File previous = classes.put(className, file);
            if (previous != null) {
                throw new JxmlException(relative(src, file), 0, "same class name " + className + " as " + relative(src, previous));
            }
            File target = new File(packageDir, className + ".java");
            Entry key = new Entry();
            key.className = className;
            key.output = packagePath + className + ".java";
            key.own = hash(src, name, hashes);
            for (String dep : dependencies(name, direct)) {
                key.deps.put(dep, hash(src, dep, hashes));
            }
            outputs.put(name, key.output);
            outputPaths.add(key.output);

            Entry recorded = sameOptions ? old.screens.get(name) : null;
            if (recorded != null && recorded.sameKey(key) && target.isFile()) {
                result.upToDate.add(target);
                addWarnings(result, recorded.warnings);
                now.screens.put(name, recorded);
                continue;
            }
            try {
                JxmlTemplate template;
                InputStream input = new FileInputStream(file);
                try {
                    template = JxmlTemplate.read(input, name, loader);
                } finally {
                    input.close();
                }
                key.warnings.addAll(template.getWarnings());
                String java = JxmlCompiler.compile(template, new JxmlCompiler.Options(packageName, className, hoist));
                byte[] bytes = java.getBytes(StandardCharsets.UTF_8);
                if (target.isFile() && Arrays.equals(Files.readAllBytes(target.toPath()), bytes)) {
                    result.upToDate.add(target); // same text: leave the file (and its time) alone
                } else {
                    if (!packageDir.isDirectory() && !packageDir.mkdirs()) {
                        throw new IOException("Cannot create " + packageDir);
                    }
                    Files.write(target.toPath(), bytes);
                    result.written.add(target);
                }
                addWarnings(result, key.warnings);
                now.screens.put(name, key);
            } catch (JxmlException e) {
                if (failure == null) {
                    failure = e; // the other screens are still brought up to date; this one has no entry
                }
            }
        }
        // generated files whose source is gone, or whose class moved (another package or suffix)
        for (Map.Entry<String, Entry> e : old == null ? new TreeMap<String, Entry>().entrySet() : old.screens.entrySet()) {
            String output = e.getValue().output;
            if (output.equals(outputs.get(e.getKey())) || outputPaths.contains(output)) {
                continue;
            }
            File stale = new File(out, output.replace('/', File.separatorChar));
            if (inside(out, stale) && output.endsWith(".java") && stale.isFile() && stale.delete()) {
                result.removed.add(stale);
                prune(out, stale.getParentFile());
            }
        }
        writeManifest(new File(out, MANIFEST), now, old != null);
        result.nanos = System.nanoTime() - start;
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    private static void addWarnings(Result result, List<String> warnings) {
        for (String warning : warnings) {
            if (!result.warnings.contains(warning)) {
                result.warnings.add(warning);
            }
        }
    }

    // ---- the keys ---------------------------------------------------------------------------------------------

    /** The forge version from the jar's manifest, or a constant when running from classes. */
    private static String version() {
        Package pack = JXForge.class.getPackage();
        String version = pack == null ? null : pack.getImplementationVersion();
        return version == null ? "unversioned" : version;
    }

    /** The files {@code name} includes, directly or not (not itself), in a fixed order. */
    private static Set<String> dependencies(String name, Map<String, List<String>> direct) {
        Set<String> seen = new LinkedHashSet<String>();
        List<String> pending = new ArrayList<String>(direct.containsKey(name) ? direct.get(name) : new ArrayList<String>());
        while (!pending.isEmpty()) {
            String next = pending.remove(pending.size() - 1);
            if (next.equals(name) || !seen.add(next)) {
                continue;
            }
            List<String> more = direct.get(next);
            if (more != null) {
                pending.addAll(more);
            }
        }
        return seen;
    }

    private static String hash(File src, String name, Map<String, String> cache) throws IOException {
        String known = cache.get(name);
        if (known == null) {
            File file = new File(src, name);
            known = file.isFile() ? sha256(Files.readAllBytes(file.toPath())) : MISSING;
            cache.put(name, known);
        }
        return known;
    }

    private static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- the manifest -----------------------------------------------------------------------------------------

    /** The manifest on disk, or null when it is missing, unreadable, corrupt or of another format. */
    private static Manifest readManifest(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (!text.endsWith("\n")) {
                return null;
            }
            String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
            if (lines.length < 2 || !FORMAT.equals(lines[0]) || !lines[1].startsWith("options\t")) {
                return null;
            }
            Manifest manifest = new Manifest();
            manifest.options = lines[1].substring("options\t".length());
            String name = null;
            Entry entry = null;
            for (int i = 2; i < lines.length; i++) {
                String[] parts = lines[i].split("\t", -1);
                String tag = parts[0];
                if (entry == null) {
                    if (!"screen".equals(tag) || parts.length != 2) {
                        return null;
                    }
                    name = unescape(parts[1]);
                    entry = new Entry();
                } else if ("class".equals(tag) && parts.length == 2) {
                    entry.className = unescape(parts[1]);
                } else if ("output".equals(tag) && parts.length == 2) {
                    entry.output = unescape(parts[1]);
                } else if ("own".equals(tag) && parts.length == 2) {
                    entry.own = parts[1];
                } else if ("dep".equals(tag) && parts.length == 3) {
                    entry.deps.put(unescape(parts[1]), parts[2]);
                } else if ("warn".equals(tag) && parts.length == 2) {
                    entry.warnings.add(unescape(parts[1]));
                } else if ("end".equals(tag) && parts.length == 1) {
                    if (entry.className == null || entry.output == null || entry.own == null
                            || manifest.screens.put(name, entry) != null) {
                        return null;
                    }
                    entry = null;
                } else {
                    return null;
                }
            }
            return entry == null ? manifest : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Writes the manifest unless the file already has exactly this text. */
    private static void writeManifest(File file, Manifest manifest, boolean hadOne) throws IOException {
        if (manifest.screens.isEmpty() && !file.isFile() && !hadOne) {
            return; // nothing generated and nothing recorded: leave the directory as it is
        }
        StringBuilder text = new StringBuilder(FORMAT).append('\n').append("options\t").append(manifest.options).append('\n');
        for (Map.Entry<String, Entry> e : manifest.screens.entrySet()) {
            Entry entry = e.getValue();
            text.append("screen\t").append(escape(e.getKey())).append('\n');
            text.append("class\t").append(escape(entry.className)).append('\n');
            text.append("output\t").append(escape(entry.output)).append('\n');
            text.append("own\t").append(entry.own).append('\n');
            for (Map.Entry<String, String> dep : entry.deps.entrySet()) {
                text.append("dep\t").append(escape(dep.getKey())).append('\t').append(dep.getValue()).append('\n');
            }
            for (String warning : entry.warnings) {
                text.append("warn\t").append(escape(warning)).append('\n');
            }
            text.append("end\n");
        }
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        if (file.isFile() && Arrays.equals(Files.readAllBytes(file.toPath()), bytes)) {
            return;
        }
        if (!file.getAbsoluteFile().getParentFile().isDirectory() && !file.getAbsoluteFile().getParentFile().mkdirs()) {
            throw new IOException("Cannot create " + file.getAbsoluteFile().getParentFile());
        }
        Files.write(file.toPath(), bytes);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i); // a lone trailing backslash throws: the manifest is corrupt
            out.append(n == 'n' ? '\n' : n == 'r' ? '\r' : n == 't' ? '\t' : n);
        }
        return out.toString();
    }

    private static boolean inside(File root, File file) throws IOException {
        String base = root.getCanonicalPath();
        return file.getCanonicalPath().startsWith(base.endsWith(File.separator) ? base : base + File.separator);
    }

    /** Deletes {@code dir} and its parents below {@code root} while they are empty. */
    private static void prune(File root, File dir) throws IOException {
        File top = root.getCanonicalFile();
        while (dir != null && !dir.getCanonicalFile().equals(top) && inside(root, dir)) {
            String[] left = dir.list();
            if (left == null || left.length > 0 || !dir.delete()) {
                return;
            }
            dir = dir.getParentFile();
        }
    }

    // ---- the sources ------------------------------------------------------------------------------------------

    /** Notes, from the parsed root of a file, which files it includes and whether it has a controller. */
    private static void scan(File file, String name, Set<String> included, Set<String> withControllers,
                             Map<String, List<String>> direct) {
        try {
            InputStream input = new FileInputStream(file);
            try {
                JxmlParser.Node root = JxmlParser.parse(input, name);
                if (root.attrs.containsKey("controller")) {
                    withControllers.add(name);
                }
                List<String> targets = new ArrayList<String>();
                includes(root, name, targets);
                included.addAll(targets);
                direct.put(name, targets);
            } finally {
                input.close();
            }
        } catch (JxmlException | IOException e) {
            // reported when the file itself is compiled
        }
    }

    private static void includes(JxmlParser.Node node, String name, List<String> targets) {
        if ("Include".equals(node.tag) && node.attrs.get("src") != null) {
            String target = JxmlLoader.join(name, node.attrs.get("src").trim());
            if (target != null && !targets.contains(target)) {
                targets.add(target);
            }
        }
        for (JxmlParser.Node child : node.children) {
            includes(child, name, targets);
        }
    }

    private static void collect(File dir, List<File> files) {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        java.util.Arrays.sort(entries);
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collect(entry, files);
            } else if (entry.getName().endsWith(".jxml") || entry.getName().endsWith(".fxml")) {
                files.add(entry);
            }
        }
    }

    private static String relative(File root, File file) {
        String r = root.getAbsoluteFile().toURI().relativize(file.getAbsoluteFile().toURI()).getPath();
        return r.isEmpty() ? file.getName() : r;
    }

    /**
     * The JXML text of an FXML file, to migrate it by hand: the tree {@code JXForge} compiles for it, one element per
     * line ({@code <Include src>} names {@code .jxml} files). What the translation cannot carry over is added to
     * {@code warnings} (may be null) as {@code file:line: message}; what has no equivalent is a {@link JxmlException}.
     */
    public static String fxmlToJxml(File fxml, List<String> warnings) throws IOException {
        InputStream input = new FileInputStream(fxml);
        try {
            return FxmlTranslator.print(FxmlTranslator.translate(input, fxml.getName(), warnings));
        } finally {
            input.close();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && "--fxml-to-jxml".equals(args[0])) {
            if (args.length < 2 || args.length > 3) {
                System.err.println("usage: JXForge --fxml-to-jxml <in.fxml> [<out.jxml>]   (prints to the console without <out>)");
                System.exit(2);
            }
            try {
                List<String> warnings = new ArrayList<String>();
                String jxml = fxmlToJxml(new File(args[1]), warnings);
                if (args.length == 3) {
                    java.nio.file.Files.write(new File(args[2]).toPath(), jxml.getBytes(StandardCharsets.UTF_8));
                } else {
                    System.out.print(jxml);
                }
                for (String warning : warnings) {
                    System.err.println("warning: " + warning);
                }
            } catch (JxmlException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
            return;
        }
        String src = "src/main/jxml";
        String out = "target/generated-sources/jxforge";
        String packageName = null;
        String suffix = "View";
        boolean hoist = true;
        for (int i = 0; i < args.length; i++) {
            if ("--src".equals(args[i])) {
                src = args[++i];
            } else if ("--out".equals(args[i])) {
                out = args[++i];
            } else if ("--package".equals(args[i])) {
                packageName = args[++i];
            } else if ("--suffix".equals(args[i])) {
                suffix = args[++i];
            } else if ("--no-hoist".equals(args[i])) {
                hoist = false;
            } else {
                System.err.println("unknown option " + args[i]);
                System.exit(2);
            }
        }
        if (packageName == null) {
            System.err.println("usage: JXForge --package <java.package> [--src dir] [--out dir] [--suffix View] [--no-hoist]");
            System.exit(2);
        }
        try {
            Result r = compileDirectory(new File(src), new File(out), packageName, suffix, hoist);
            for (String warning : r.warnings) {
                System.err.println("warning: " + warning);
            }
            System.out.printf("JXForge: %d written, %d up to date, %d removed, %.1f ms%n", r.written.size(), r.upToDate.size(),
                    r.removed.size(), r.nanos / 1e6);
        } catch (JxmlException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
