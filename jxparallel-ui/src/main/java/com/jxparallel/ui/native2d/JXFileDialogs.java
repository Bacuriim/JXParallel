package com.jxparallel.ui.native2d;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The operating system's open, save and folder dialogs (tinyfiledialogs through LWJGL). Modal and
 * blocking, like JavaFX's FileChooser: call from the application thread; windows keep their last frame.
 */
public final class JXFileDialogs {
    private JXFileDialogs() {
    }

    /**
     * Files picked in an open dialog, or an empty list when cancelled. {@code patterns} are
     * globs such as {@code *.xml}; {@code description} names them.
     */
    public static List<File> open(String title, File initialDirectory, String initialName, List<String> patterns,
                                  String description, boolean multiple) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String result = TinyFileDialogs.tinyfd_openFileDialog(title == null ? "" : title,
                    defaultPath(initialDirectory, initialName), filters(stack, patterns), emptyToNull(description), multiple);
            if (result == null || result.isEmpty()) {
                return Collections.emptyList();
            }
            List<File> files = new ArrayList<File>();
            for (String path : result.split("\\|")) {
                if (!path.isEmpty()) {
                    files.add(new File(path));
                }
            }
            return files;
        }
    }

    /** The file chosen in a save dialog, or {@code null} when cancelled. */
    public static File save(String title, File initialDirectory, String initialName, List<String> patterns, String description) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String result = TinyFileDialogs.tinyfd_saveFileDialog(title == null ? "" : title,
                    defaultPath(initialDirectory, initialName), filters(stack, patterns), emptyToNull(description));
            return result == null || result.isEmpty() ? null : new File(result);
        }
    }

    /** The folder chosen, or {@code null} when cancelled. */
    public static File folder(String title, File initialDirectory) {
        String result = TinyFileDialogs.tinyfd_selectFolderDialog(title == null ? "" : title,
                initialDirectory == null ? "" : initialDirectory.getAbsolutePath() + File.separator);
        return result == null || result.isEmpty() ? null : new File(result);
    }

    static String defaultPath(File directory, String name) {
        String dir = directory == null ? "" : directory.getAbsolutePath() + File.separator;
        return dir + (name == null ? "" : name);
    }

    private static PointerBuffer filters(MemoryStack stack, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }
        PointerBuffer buffer = stack.mallocPointer(patterns.size());
        for (String pattern : patterns) {
            buffer.put(stack.UTF8(pattern));
        }
        buffer.flip();
        return buffer;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
