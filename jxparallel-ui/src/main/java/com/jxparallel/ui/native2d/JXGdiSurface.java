package com.jxparallel.ui.native2d;

import java.nio.ByteBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.libffi.FFIType;
import org.lwjgl.system.windows.User32;

/**
 * Shows a software-painted frame in a Windows window without OpenGL: a 32-bit top-down DIB section
 * the painter's ARGB pixels are copied into, blitted to the window's device context with GDI.
 *
 * <p>GDI is called through LWJGL's libffi ({@link JXNativeCall}): LWJGL binds no BitBlt or
 * CreateDIBSection. No AWT, no extra native library. The DIB's byte order (B, G, R, A) is the ARGB int in little-endian memory, so a frame is
 * one copy. Display thread only.
 */
final class JXGdiSurface implements AutoCloseable {
    private static final int SRCCOPY = 0x00CC0020;
    private static final int DIB_RGB_COLORS = 0;

    private static SharedLibrary gdi32;
    private static JXNativeCall createCompatibleDC;
    private static JXNativeCall createDIBSection;
    private static JXNativeCall selectObject;
    private static JXNativeCall bitBlt;
    private static JXNativeCall deleteObject;
    private static JXNativeCall deleteDC;
    private static JXNativeCall gdiFlush;

    private final long hwnd;
    private long memoryDC;
    private long bitmap;
    private long previous;
    private long bits;
    private java.nio.IntBuffer dib;
    private int width;
    private int height;

    JXGdiSurface(long hwnd) {
        if (hwnd == MemoryUtil.NULL) {
            throw new IllegalStateException("No Win32 window");
        }
        this.hwnd = hwnd;
        bind();
    }

    /** True where a frame can be shown this way (Windows). */
    static boolean supported() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    /** Copies the whole frame into the DIB and blits it to the window. */
    void present(int[] argb, int frameWidth, int frameHeight) {
        present(argb, frameWidth, frameHeight, new int[] {0, 0, frameWidth, frameHeight}, 1);
    }

    /**
     * Copies the rectangles ({@code x0, y0, x1, y1} each) of the frame into the DIB and blits them to
     * the window; a new size copies the whole frame, as the DIB is new.
     */
    void present(int[] argb, int frameWidth, int frameHeight, int[] rects, int count) {
        if (frameWidth != width || frameHeight != height || bits == MemoryUtil.NULL) {
            recreate(frameWidth, frameHeight);
            rects = new int[] {0, 0, frameWidth, frameHeight};
            count = 1;
        }
        gdiFlush.call(); // GDI may still read the DIB from the last blit
        for (int r = 0; r < count; r++) {
            int x0 = Math.max(0, rects[4 * r]);
            int x1 = Math.min(frameWidth, rects[4 * r + 2]);
            int y1 = Math.min(frameHeight, rects[4 * r + 3]);
            for (int y = Math.max(0, rects[4 * r + 1]); y < y1 && x0 < x1; y++) {
                int at = y * frameWidth + x0;
                dib.position(at);
                dib.put(argb, at, x1 - x0);
            }
        }
        long dc = User32.GetDC(hwnd);
        try {
            for (int r = 0; r < count; r++) {
                int x0 = rects[4 * r];
                int y0 = rects[4 * r + 1];
                bitBlt.call(dc, x0, y0, rects[4 * r + 2] - x0, rects[4 * r + 3] - y0, memoryDC, x0, y0, SRCCOPY);
            }
        } finally {
            User32.ReleaseDC(hwnd, dc);
        }
    }

    private void recreate(int newWidth, int newHeight) {
        release();
        width = newWidth;
        height = newHeight;
        long dc = User32.GetDC(hwnd);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            memoryDC = createCompatibleDC.call(dc);
            ByteBuffer info = stack.calloc(44); // BITMAPINFOHEADER and one RGBQUAD
            info.putInt(0, 40);              // biSize
            info.putInt(4, newWidth);        // biWidth
            info.putInt(8, -newHeight);      // biHeight: negative is top-down, as the painter's rows
            info.putShort(12, (short) 1);    // biPlanes
            info.putShort(14, (short) 32);   // biBitCount
            PointerBuffer bitsOut = stack.callocPointer(1);
            bitmap = createDIBSection.call(dc, MemoryUtil.memAddress(info), DIB_RGB_COLORS, MemoryUtil.memAddress(bitsOut), 0L, 0);
            bits = bitsOut.get(0);
            if (bitmap == MemoryUtil.NULL || bits == MemoryUtil.NULL) {
                throw new IllegalStateException("CreateDIBSection failed");
            }
            dib = MemoryUtil.memIntBuffer(bits, newWidth * newHeight);
            previous = selectObject.call(memoryDC, bitmap);
        } finally {
            User32.ReleaseDC(hwnd, dc);
        }
    }

    private void release() {
        if (memoryDC != MemoryUtil.NULL) {
            selectObject.call(memoryDC, previous);
            deleteDC.call(memoryDC);
            memoryDC = MemoryUtil.NULL;
        }
        if (bitmap != MemoryUtil.NULL) {
            deleteObject.call(bitmap);
            bitmap = MemoryUtil.NULL;
            bits = MemoryUtil.NULL;
            dib = null;
        }
    }

    @Override
    public void close() {
        release();
    }

    private static synchronized void bind() {
        if (gdi32 != null) {
            return;
        }
        gdi32 = org.lwjgl.system.APIUtil.apiCreateLibrary("gdi32");
        FFIType p = JXNativeCall.POINTER;
        FFIType i = JXNativeCall.INT;
        createCompatibleDC = new JXNativeCall(gdi32, "CreateCompatibleDC", p, p);
        createDIBSection = new JXNativeCall(gdi32, "CreateDIBSection", p, p, p, i, p, p, i);
        selectObject = new JXNativeCall(gdi32, "SelectObject", p, p, p);
        bitBlt = new JXNativeCall(gdi32, "BitBlt", i, p, i, i, i, i, p, i, i, i);
        deleteObject = new JXNativeCall(gdi32, "DeleteObject", i, p);
        deleteDC = new JXNativeCall(gdi32, "DeleteDC", i, p);
        gdiFlush = new JXNativeCall(gdi32, "GdiFlush", i);
    }
}
