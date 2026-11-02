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
 * output is newer than the JXML are left alone. Used by the Maven plugin and from the command line:
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
        public long nanos;
    }

    private JXForge() {
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
            if (target.isFile() && target.lastModified() >= stamp) {
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
