package com.jxparallel.forge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the files an {@code <Include src="..."/>} names. A name is a path with {@code /}; {@code src} is
 * resolved against the folder of the file that includes it ({@link #join}), so the same {@code src} means the
 * same thing for the interpreter and the compiler. Three loaders: a folder (the compiler's source directory),
 * the class path (resources) and the file system (a file read with {@link JxmlTemplate#read(File)}).
 */
public interface JxmlLoader {
    /** Opens the file of this name, as {@link #join} made it; throws {@link FileNotFoundException} when there is none. */
    InputStream open(String name) throws IOException;

    /** The folder {@code root}: names are paths below it, so an include cannot leave it. */
    static JxmlLoader directory(final File root) {
        return new JxmlLoader() {
            @Override
            public InputStream open(String name) throws IOException {
                return new FileInputStream(new File(root, name));
            }
        };
    }

    /** Resources of a class loader: names are resource paths such as {@code screens/parts/header.jxml}. */
    static JxmlLoader classpath(final ClassLoader loader) {
        return new JxmlLoader() {
            @Override
            public InputStream open(String name) throws IOException {
                InputStream in = loader.getResourceAsStream(name);
                if (in == null) {
                    throw new FileNotFoundException(name);
                }
                return in;
            }
        };
    }

    /** The file system: names are the paths of the files themselves. */
    static JxmlLoader files() {
        return new JxmlLoader() {
            @Override
            public InputStream open(String name) throws IOException {
                return new FileInputStream(name);
            }
        };
    }

    /**
     * {@code src} resolved against the folder of {@code from}, with {@code .} and {@code ..} applied and only
     * {@code /} as separator; null when {@code src} climbs out of the top of a relative path.
     */
    static String join(String from, String src) {
        String base = from.replace('\\', '/');
        String folder = base.contains("/") ? base.substring(0, base.lastIndexOf('/')) : "";
        String path = (src.startsWith("/") || folder.isEmpty() ? src : folder + "/" + src).replace('\\', '/');
        boolean absolute = path.startsWith("/");
        List<String> parts = new ArrayList<String>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (parts.isEmpty() || parts.get(parts.size() - 1).endsWith(":")) {
                    return null;
                }
                parts.remove(parts.size() - 1);
            } else {
                parts.add(part);
            }
        }
        return (absolute ? "/" : "") + String.join("/", parts);
    }
}
