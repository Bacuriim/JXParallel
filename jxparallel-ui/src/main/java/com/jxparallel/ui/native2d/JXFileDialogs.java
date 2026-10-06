package com.jxparallel.ui.native2d;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The operating system's open, save and folder dialogs. Modal and blocking, like JavaFX's
 * FileChooser: call from the application thread; windows keep their last frame.
 *
 * <p>On Windows, open and save call the common dialog ({@code GetOpenFileNameW},
 * {@code GetSaveFileNameW} in comdlg32) through libffi ({@link JXNativeCall}): tinyfiledialogs
 * returned at once without showing anything there (the save, open and message box calls all return
 * null/false on Windows 11, both 32 and 64 bit, LWJGL 3.3.3). Elsewhere, and for folders,
 * tinyfiledialogs through LWJGL.
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
        if (WINDOWS) {
            return common(false, title, initialDirectory, initialName, patterns, description, multiple);
        }
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
        if (WINDOWS) {
            List<File> chosen = common(true, title, initialDirectory, initialName, patterns, description, false);
            return chosen.isEmpty() ? null : chosen.get(0);
        }
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

    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private static final int OFN_OVERWRITEPROMPT = 0x2;
    private static final int OFN_NOCHANGEDIR = 0x8;
    private static final int OFN_ALLOWMULTISELECT = 0x200;
    private static final int OFN_PATHMUSTEXIST = 0x800;
    private static final int OFN_FILEMUSTEXIST = 0x1000;
    private static final int OFN_EXPLORER = 0x80000;
    /** UTF-16 units of the file name buffer: room for many files picked at once. */
    private static final int FILE_BUFFER = 32768;

    private static JXNativeCall getOpenFileName;
    private static JXNativeCall getSaveFileName;

    private static synchronized void bindComdlg32() {
        if (getOpenFileName == null) {
            org.lwjgl.system.SharedLibrary comdlg32 = org.lwjgl.system.APIUtil.apiCreateLibrary("comdlg32");
            getOpenFileName = new JXNativeCall(comdlg32, "GetOpenFileNameW", JXNativeCall.INT, JXNativeCall.POINTER);
            getSaveFileName = new JXNativeCall(comdlg32, "GetSaveFileNameW", JXNativeCall.INT, JXNativeCall.POINTER);
        }
    }

    /**
     * The Windows common dialog, without an owner window (the windows belong to the display thread),
     * as AWT's FileDialog without a frame. The patterns become one filter entry named by the description.
     */
    private static List<File> common(boolean save, String title, File initialDirectory, String initialName,
                                     List<String> patterns, String description, boolean multiple) {
        bindComdlg32();
        // OPENFILENAMEW, laid out for this pointer size: DWORDs, a WORD pair and pointers in order
        int ptr = org.lwjgl.system.Pointer.POINTER_SIZE;
        int[] offsets = new int[23];
        int[] sizes = {4, ptr, ptr, ptr, ptr, 4, 4, ptr, 4, ptr, 4, ptr, ptr, 4, 2, 2, ptr, ptr, ptr, ptr, ptr, 4, 4};
        int at = 0;
        for (int f = 0; f < sizes.length; f++) {
            at = (at + sizes[f] - 1) / sizes[f] * sizes[f];
            offsets[f] = at;
            at += sizes[f];
        }
        int structSize = (at + ptr - 1) / ptr * ptr;
        java.nio.ByteBuffer ofn = org.lwjgl.system.MemoryUtil.memCalloc(structSize);
        java.nio.ByteBuffer file = org.lwjgl.system.MemoryUtil.memCalloc(2 * FILE_BUFFER);
        java.nio.ByteBuffer filter = null;
        java.nio.ByteBuffer dir = null;
        java.nio.ByteBuffer caption = null;
        try {
            if (initialName != null && !initialName.isEmpty()) {
                for (int i = 0; i < Math.min(initialName.length(), FILE_BUFFER - 1); i++) {
                    file.putChar(2 * i, initialName.charAt(i));
                }
            }
            if (patterns != null && !patterns.isEmpty()) {
                String joined = String.join(";", patterns);
                String name = description == null || description.isEmpty() ? joined : description + " (" + joined + ")";
                filter = org.lwjgl.system.MemoryUtil.memUTF16(name + '\0' + joined + '\0'); // and the list's final NUL
            }
            if (initialDirectory != null) {
                dir = org.lwjgl.system.MemoryUtil.memUTF16(initialDirectory.getAbsolutePath());
            }
            if (title != null && !title.isEmpty()) {
                caption = org.lwjgl.system.MemoryUtil.memUTF16(title);
            }
            long base = org.lwjgl.system.MemoryUtil.memAddress(ofn);
            ofn.putInt(offsets[0], structSize);
            org.lwjgl.system.MemoryUtil.memPutAddress(base + offsets[3], address(filter));
            ofn.putInt(offsets[6], filter == null ? 0 : 1);
            org.lwjgl.system.MemoryUtil.memPutAddress(base + offsets[7], org.lwjgl.system.MemoryUtil.memAddress(file));
            ofn.putInt(offsets[8], FILE_BUFFER);
            org.lwjgl.system.MemoryUtil.memPutAddress(base + offsets[11], address(dir));
            org.lwjgl.system.MemoryUtil.memPutAddress(base + offsets[12], address(caption));
            int flags = OFN_EXPLORER | OFN_NOCHANGEDIR | OFN_PATHMUSTEXIST
                    | (save ? OFN_OVERWRITEPROMPT : OFN_FILEMUSTEXIST) | (multiple ? OFN_ALLOWMULTISELECT : 0);
            ofn.putInt(offsets[13], flags);
            long ok = (save ? getSaveFileName : getOpenFileName).call(base);
            List<File> files = new ArrayList<File>();
            if (ok == 0) {
                return files; // cancelled (or failed: CommDlgExtendedError)
            }
            // one full path, or with several files the folder then each name, NUL separated, NUL NUL ended
            List<String> parts = new ArrayList<String>();
            StringBuilder part = new StringBuilder();
            for (int i = 0; i < FILE_BUFFER; i++) {
                char c = file.getChar(2 * i);
                if (c != 0) {
                    part.append(c);
                } else if (part.length() == 0) {
                    break;
                } else {
                    parts.add(part.toString());
                    part.setLength(0);
                }
            }
            if (parts.size() == 1) {
                files.add(new File(parts.get(0)));
            } else {
                for (int i = 1; i < parts.size(); i++) {
                    files.add(new File(parts.get(0), parts.get(i)));
                }
            }
            return files;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(ofn);
            org.lwjgl.system.MemoryUtil.memFree(file);
            org.lwjgl.system.MemoryUtil.memFree(filter);
            org.lwjgl.system.MemoryUtil.memFree(dir);
            org.lwjgl.system.MemoryUtil.memFree(caption);
        }
    }

    private static long address(java.nio.ByteBuffer buffer) {
        return buffer == null ? org.lwjgl.system.MemoryUtil.NULL : org.lwjgl.system.MemoryUtil.memAddress(buffer);
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
