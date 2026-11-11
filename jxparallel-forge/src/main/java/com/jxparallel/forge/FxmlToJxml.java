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
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Converts an FXML file into the JXML the interpreter and the compiler read, so a JavaFX screen can run on the native
 * UI. The result is the JXML text and the tree {@code JxmlParser} would build from it (used directly at run time);
 * whatever cannot be converted is in the {@link FxmlReport}. See {@code docs/jxforge-fxml.md}.
 *
 * <pre>java -cp jxparallel-forge.jar;jxparallel-core.jar com.jxparallel.forge.FxmlToJxml
 *     --src src/main/resources --out target/generated-jxml [--strict] [--bundle messages.properties]
 *     [--item-type ordersTable=com.acme.Order] [--report report.txt]</pre>
 */
public final class FxmlToJxml {
    /** What the conversion may use; the {@link #report()} is filled by every conversion made with these options. */
    public static final class Options {
        private ResourceBundle bundle;
        private boolean strict;
        private final Map<String, String> itemTypes = new HashMap<String, String>();
        private final FxmlReport report = new FxmlReport();

        /** Resolves {@code %key} values; without one they are reported and kept as the key. */
        public Options bundle(ResourceBundle value) {
            this.bundle = value;
            return this;
        }

        public ResourceBundle bundle() {
            return bundle;
        }

        /** A conversion that reports anything not NOTE throws {@link FxmlConversionException}. */
        public Options strict(boolean value) {
            this.strict = value;
            return this;
        }

        public boolean strict() {
            return strict;
        }

        /** The class of the rows of the table whose fx:id is {@code id} ({@code "*"}: every table without another type). */
        public Options itemType(String id, String className) {
            itemTypes.put(id, className);
            return this;
        }

        String itemType(String id) {
            String type = id == null ? null : itemTypes.get(id);
            return type != null ? type : itemTypes.get("*");
        }

        /** Everything reported by the conversions made with these options. */
        public FxmlReport report() {
            return report;
        }
    }

    /** One converted file. */
    public static final class Result {
        private final JxmlParser.Node node;
        private final String name;
        private final FxmlReport report;

        Result(JxmlParser.Node node, String name, FxmlReport report) {
            this.node = node;
            this.name = name;
            this.report = report;
        }

        public FxmlReport report() {
            return report;
        }

        /** The JXML document. */
        public String jxml() {
            return write(node, name);
        }

        /** The root's {@code controller} class, or null. */
        public String controller() {
            return node.attrs.get("controller");
        }

        JxmlParser.Node node() {
            return node;
        }
    }

    private FxmlToJxml() {
    }

    /** Converts for files: an {@code fx:include} of {@code a.fxml} becomes {@code <Include src="a.jxml"/>}. */
    public static Result convert(InputStream input, String name, Options options) {
        return convert(input, name, options, false);
    }

    static Result convert(InputStream input, String name, Options options, boolean runtime) {
        FxmlReport report = new FxmlReport();
        FxmlParser.Document doc = FxmlParser.parse(input, name);
        JxmlParser.Node node = new FxmlConverter(doc, name, options, report, runtime).convert();
        options.report.addAll(report);
        if (node == null) {
            throw new FxmlConversionException(report);
        }
        if (options.strict && report.hasLosses()) {
            throw new FxmlConversionException(report);
        }
        return new Result(node, name, report);
    }

    /** A loader for {@code JxmlTemplate.read(...)}: the files it opens are found by {@code base}, and {@code .fxml} includes convert with {@code options}. */
    public static JxmlLoader loader(JxmlLoader base, Options options) {
        return new FxmlLoader(base, options);
    }

    static final class FxmlLoader implements JxmlLoader {
        final JxmlLoader base;
        final Options options;

        FxmlLoader(JxmlLoader base, Options options) {
            this.base = base;
            this.options = options;
        }

        @Override
        public InputStream open(String name) throws IOException {
            return base.open(name);
        }
    }

    // ---- directories -----------------------------------------------------------------------------

    /**
     * Converts every {@code .fxml} below {@code src} to a {@code .jxml} at the same relative path below {@code out}.
     * Returns the report of all files; in strict mode it throws after converting them all, so one run shows every problem.
     */
    public static FxmlReport convertDirectory(File src, File out, Options options) throws IOException {
        List<File> files = new ArrayList<File>();
        collect(src, files);
        FxmlReport all = new FxmlReport();
        Map<String, String> controllers = new HashMap<String, String>();
        Map<String, List<String[]>> includes = new HashMap<String, List<String[]>>();
        for (File file : files) {
            String name = relative(src, file);
            Result result;
            InputStream in = new FileInputStream(file);
            try {
                Options lenient = new Options().bundle(options.bundle);
                lenient.itemTypes.putAll(options.itemTypes);
                result = convert(in, name, lenient);
            } catch (FxmlConversionException e) {
                all.addAll(e.getReport());
                continue;
            } catch (JxmlException e) {
                all.add(FxmlReport.Level.UNSUPPORTED, name, e.getLine(), "(file)", null, "cannot be read: " + e.getMessage());
                continue;
            } finally {
                in.close();
            }
            all.addAll(result.report());
            controllers.put(name, result.controller());
            collectIncludes(result.node(), name, includes);
            File target = new File(out, name.substring(0, name.length() - ".fxml".length()) + ".jxml");
            if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) {
                throw new IOException("Cannot create " + target.getParentFile());
            }
            Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(target), StandardCharsets.UTF_8);
            try {
                writer.write(result.jxml());
            } finally {
                writer.close();
            }
        }
        // a part's bindings resolve against the controller of the file that includes it: a different fx:controller cannot work
        for (Map.Entry<String, List<String[]>> entry : includes.entrySet()) {
            String from = controllers.get(entry.getKey());
            for (String[] include : entry.getValue()) {
                String to = JxmlLoader.join(entry.getKey(), include[0].replace(".jxml", ".fxml"));
                String part = to == null ? null : controllers.get(to);
                if (part != null && from != null && !part.equals(from)) {
                    all.add(FxmlReport.Level.UNSUPPORTED, entry.getKey(), Integer.parseInt(include[1]), "fx:include", "source",
                            to + " declares fx:controller " + part + " but is included in a file whose controller is " + from
                            + "; JXML parts use the controller of the including file, so the two must be one class");
                }
            }
        }
        options.report.addAll(all);
        if (options.strict && all.hasLosses()) {
            throw new FxmlConversionException(all);
        }
        return all;
    }

    private static void collectIncludes(JxmlParser.Node node, String name, Map<String, List<String[]>> into) {
        if ("Include".equals(node.tag) && node.attrs.get("src") != null) {
            List<String[]> list = into.get(name);
            if (list == null) {
                list = new ArrayList<String[]>();
                into.put(name, list);
            }
            list.add(new String[] {node.attrs.get("src"), String.valueOf(node.line)});
        }
        for (JxmlParser.Node child : node.children) {
            collectIncludes(child, name, into);
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
            } else if (entry.getName().endsWith(".fxml")) {
                files.add(entry);
            }
        }
    }

    private static String relative(File root, File file) {
        String r = root.getAbsoluteFile().toURI().relativize(file.getAbsoluteFile().toURI()).getPath();
        return r.isEmpty() ? file.getName() : r;
    }

    // ---- writing JXML ----------------------------------------------------------------------------

    static String write(JxmlParser.Node root, String name) {
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<!-- converted from ").append(name.replace("--", "- -")).append(" by JXForge (FxmlToJxml); see docs/jxforge-fxml.md -->\n");
        element(root, 0, out);
        return out.toString();
    }

    private static void element(JxmlParser.Node node, int depth, StringBuilder out) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }
        out.append(indent).append('<').append(node.tag);
        int column = indent.length() + node.tag.length() + 1;
        for (Map.Entry<String, String> attr : node.attrs.entrySet()) {
            String text = attr.getKey() + "=\"" + escape(attr.getValue()) + "\"";
            if (column + text.length() > 110) {
                out.append('\n').append(indent).append("    ");
                column = indent.length() + 4;
            } else {
                out.append(' ');
                column++;
            }
            out.append(text);
            column += text.length();
        }
        if (node.children.isEmpty()) {
            out.append("/>\n");
            return;
        }
        out.append(">\n");
        for (JxmlParser.Node child : node.children) {
            element(child, depth + 1, out);
        }
        out.append(indent).append("</").append(node.tag).append(">\n");
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\n': out.append("&#10;"); break;
                case '\r': out.append("&#13;"); break;
                case '\t': out.append("&#9;"); break;
                default: out.append(c); break;
            }
        }
        return out.toString();
    }

    // ---- command line ----------------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        String src = "src/main/resources";
        String out = "target/generated-jxml";
        String reportFile = null;
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            if ("--src".equals(args[i])) {
                src = args[++i];
            } else if ("--out".equals(args[i])) {
                out = args[++i];
            } else if ("--strict".equals(args[i])) {
                options.strict(true);
            } else if ("--report".equals(args[i])) {
                reportFile = args[++i];
            } else if ("--bundle".equals(args[i])) {
                InputStream in = new FileInputStream(args[++i]);
                try {
                    options.bundle(new PropertyResourceBundle(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)));
                } finally {
                    in.close();
                }
            } else if ("--item-type".equals(args[i])) {
                String pair = args[++i];
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    System.err.println("--item-type needs fxId=fully.qualified.Class (or *=Class)");
                    System.exit(2);
                }
                options.itemType(pair.substring(0, eq), pair.substring(eq + 1));
            } else {
                System.err.println("unknown option " + args[i]);
                System.err.println("usage: FxmlToJxml [--src dir|file.fxml] [--out dir] [--strict] [--bundle file.properties] "
                        + "[--item-type id=Class] [--report file]");
                System.exit(2);
            }
        }
        FxmlReport report;
        int status = 0;
        try {
            File source = new File(src);
            if (source.isFile()) {
                Options single = options;
                InputStream in = new FileInputStream(source);
                try {
                    Result r = convert(in, source.getName(), single);
                    File target = new File(out, source.getName().replaceAll("\\.fxml$", "") + ".jxml");
                    target.getParentFile().mkdirs();
                    Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(target), StandardCharsets.UTF_8);
                    try {
                        writer.write(r.jxml());
                    } finally {
                        writer.close();
                    }
                } finally {
                    in.close();
                }
                report = options.report();
            } else {
                report = convertDirectory(source, new File(out), options);
            }
        } catch (FxmlConversionException e) {
            report = e.getReport();
            System.err.println(e.getMessage());
            status = 1;
        } catch (JxmlException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        if (reportFile != null) {
            File f = new File(reportFile);
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(f), StandardCharsets.UTF_8);
            try {
                writer.write(report.toText());
            } finally {
                writer.close();
            }
        } else {
            System.out.print(report.toText());
        }
        System.out.printf("FxmlToJxml: %d findings (%d not converted, %d approximate, %d notes)%n", report.entries().size(),
                report.entries(FxmlReport.Level.UNSUPPORTED).size(), report.entries(FxmlReport.Level.APPROXIMATE).size(),
                report.entries(FxmlReport.Level.NOTE).size());
        if (status != 0) {
            System.exit(status);
        }
    }
}
