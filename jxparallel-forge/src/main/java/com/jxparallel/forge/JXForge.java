package com.jxparallel.forge;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles a directory of JXML files into Java sources, one class per file, like {@code protoc}:
 * {@code src/main/jxml/orders/order-list.jxml} becomes {@code <package>.OrderListView}. Files whose
 * output is newer than the JXML are left alone, unless the compiler or its options changed since: a stamp file in the
 * output folder holds their fingerprint, and a missing or different one regenerates everything. Used by the Maven plugin and from the command line:
 *
 * <pre>java -cp jxparallel-forge.jar;jxparallel-core.jar com.jxparallel.forge.JXForge
 *     --src src/main/jxml --out target/generated-sources/jxforge --package com.acme.views [--no-hoist]</pre>
 */
public final class JXForge {
    /** What one run did. */
    public static final class Result {
        public final List<File> written = new ArrayList<File>();
        public final List<File> upToDate = new ArrayList<File>();
        /** Files that are only parts of others (included, with no root controller): they get no class. */
        public final List<File> parts = new ArrayList<File>();
        /** True when the stamp of the output folder was missing or differed, so every class was generated again. */
        public boolean regenerated;
        public long nanos;
    }

    private JXForge() {
    }

    private static volatile String compilerFingerprint;

    /**
     * Identifies the compiler: the JXForge version and a hash of its classes (the schema, the compiler and what they
     * use), so it changes whenever the generated code could change. {@link #compileDirectory} keeps it, with the
     * options, in a stamp file in the output folder and regenerates everything when it differs.
     */
    public static String compilerFingerprint() {
        String cached = compilerFingerprint;
        if (cached == null) {
            cached = version() + ":" + hashClasses();
            compilerFingerprint = cached;
        }
        return cached;
    }

    /** The fingerprint of the compiler together with the options that shape the output. */
    public static String fingerprint(String packageName, String suffix, boolean hoist) {
        return compilerFingerprint() + "|package=" + packageName + "|suffix=" + suffix + "|hoist=" + hoist;
    }

    private static String version() {
        InputStream in = JXForge.class.getResourceAsStream("/META-INF/maven/io.github.bacuriim/jxparallel-forge/pom.properties");
        if (in != null) {
            try {
                java.util.Properties properties = new java.util.Properties();
                properties.load(in);
                String v = properties.getProperty("version");
                if (v != null) {
                    return v;
                }
            } catch (IOException ignored) {
                // falls back below
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        }
        String v = JXForge.class.getPackage() == null ? null : JXForge.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    /**
     * The jar or classes folder this class was loaded from. Found through the class's own resource URL, which every class
     * loader gives (Maven's plugin class loader does not always give a code source), with the code source as the fallback.
     */
    static File classesRoot() throws Exception {
        java.net.URL self = JXForge.class.getResource("JXForge.class");
        if (self != null) {
            String url = self.toString();
            if (url.startsWith("jar:")) {
                String jar = url.substring(4, url.indexOf("!/"));
                return new File(new java.net.URI(jar));
            }
            if (url.startsWith("file:")) {
                File file = new File(self.toURI()); // .../com/jxparallel/forge/JXForge.class
                return file.getParentFile().getParentFile().getParentFile().getParentFile();
            }
        }
        return new File(JXForge.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** SHA-256 over the name and bytes of every class of this package, in a jar or a classes folder. */
    private static String hashClasses() {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            File root = classesRoot();
            Map<String, byte[]> contents = new java.util.TreeMap<String, byte[]>();
            if (root.isFile()) {
                java.util.zip.ZipFile zip = new java.util.zip.ZipFile(root);
                try {
                    java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        java.util.zip.ZipEntry entry = entries.nextElement();
                        if (entry.getName().startsWith("com/jxparallel/forge/") && entry.getName().endsWith(".class")) {
                            contents.put(entry.getName(), readAll(zip.getInputStream(entry)));
                        }
                    }
                } finally {
                    zip.close();
                }
            } else {
                collectClasses(new File(root, "com/jxparallel/forge"), "com/jxparallel/forge/", contents);
            }
            for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(entry.getValue());
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            // the classes cannot be read: a fingerprint that never matches keeps the output right, at the cost of the skip
            return "unknown-" + System.nanoTime();
        }
    }

    private static void collectClasses(File dir, String prefix, Map<String, byte[]> contents) throws IOException {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectClasses(entry, prefix + entry.getName() + "/", contents);
            } else if (entry.getName().endsWith(".class")) {
                contents.put(prefix + entry.getName(), readAll(new FileInputStream(entry)));
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The stamp file of one source folder: several folders (JXML and converted FXML) can share an output folder. */
    private static File stampFile(File src, File out) {
        String key = Integer.toHexString(src.getAbsoluteFile().toURI().toString().hashCode());
        return new File(out, ".jxforge-stamp-" + key);
    }

    private static String readStamp(File stamp) {
        if (!stamp.isFile()) {
            return null;
        }
        try {
            return new String(readAll(new FileInputStream(stamp)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static Result compileDirectory(File src, File out, String packageName, String suffix, boolean hoist)
            throws IOException {
        long start = System.nanoTime();
        Result result = new Result();
        List<File> files = new ArrayList<File>();
        collect(src, files);
        java.util.Set<String> included = new java.util.HashSet<String>();
        java.util.Set<String> withControllers = new java.util.HashSet<String>();
        java.util.Set<String> withIncludes = new java.util.HashSet<String>();
        long newest = 0;
        for (File file : files) {
            newest = Math.max(newest, file.lastModified());
            scan(file, relative(src, file), included, withControllers, withIncludes);
        }
        // generated by another compiler, or with other options: nothing in the folder can be trusted
        String fingerprint = fingerprint(packageName, suffix, hoist);
        File stampFile = stampFile(src, out);
        boolean current = fingerprint.equals(readStamp(stampFile));
        result.regenerated = !current;
        JxmlLoader loader = JxmlLoader.directory(src);
        Map<String, File> classes = new HashMap<String, File>();
        File packageDir = new File(out, packageName.replace('.', File.separatorChar));
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
            // a file with includes depends on the files it includes (and they on theirs): any newer file regenerates it
            long stamp = withIncludes.contains(name) ? newest : file.lastModified();
            if (current && target.isFile() && target.lastModified() >= stamp) {
                result.upToDate.add(target);
                continue;
            }
            JxmlTemplate template;
            InputStream input = new FileInputStream(file);
            try {
                template = JxmlTemplate.read(input, name, loader);
            } finally {
                input.close();
            }
            String java = JxmlCompiler.compile(template, new JxmlCompiler.Options(packageName, className, hoist));
            if (!packageDir.isDirectory() && !packageDir.mkdirs()) {
                throw new IOException("Cannot create " + packageDir);
            }
            Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(target), StandardCharsets.UTF_8);
            try {
                writer.write(java);
            } finally {
                writer.close();
            }
            result.written.add(target);
        }
        if (!current) {
            if (!out.isDirectory() && !out.mkdirs()) {
                throw new IOException("Cannot create " + out);
            }
            Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(stampFile), StandardCharsets.UTF_8);
            try {
                writer.write(fingerprint);
            } finally {
                writer.close();
            }
        }
        result.nanos = System.nanoTime() - start;
        return result;
    }

    /** Notes, from the parsed root of a file, which files it includes, whether it has a controller and whether it includes. */
    private static void scan(File file, String name, java.util.Set<String> included, java.util.Set<String> withControllers,
                             java.util.Set<String> withIncludes) {
        try {
            InputStream input = new FileInputStream(file);
            try {
                JxmlParser.Node root = JxmlParser.parse(input, name);
                if (root.attrs.containsKey("controller")) {
                    withControllers.add(name);
                }
                includes(root, name, included, withIncludes);
            } finally {
                input.close();
            }
        } catch (JxmlException | IOException e) {
            // reported when the file itself is compiled
        }
    }

    private static void includes(JxmlParser.Node node, String name, java.util.Set<String> included, java.util.Set<String> withIncludes) {
        if ("Include".equals(node.tag) && node.attrs.get("src") != null) {
            String target = JxmlLoader.join(name, node.attrs.get("src").trim());
            if (target != null) {
                included.add(target);
                withIncludes.add(name);
            }
        }
        for (JxmlParser.Node child : node.children) {
            includes(child, name, included, withIncludes);
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
            } else if (entry.getName().endsWith(".jxml")) {
                files.add(entry);
            }
        }
    }

    private static String relative(File root, File file) {
        String r = root.getAbsoluteFile().toURI().relativize(file.getAbsoluteFile().toURI()).getPath();
        return r.isEmpty() ? file.getName() : r;
    }

    public static void main(String[] args) throws IOException {
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
            System.out.printf("JXForge: %d written, %d up to date, %.1f ms%n", r.written.size(), r.upToDate.size(), r.nanos / 1e6);
        } catch (JxmlException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
