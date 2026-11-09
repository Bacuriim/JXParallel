package com.jxparallel.ui.resource;

/**
 * A decoded picture: {@code width * height} ARGB pixels (not premultiplied), row by row, which is what the
 * {@code pixels} prop of an {@code Image} takes together with {@code imageWidth} and {@code imageHeight}.
 *
 * <p>The renderers tell one picture from another by the identity of the pixel array, so a
 * {@link JXImages} cache hands out the same {@code JXImage} (and the same array) for the same file while it
 * is cached. Do not change the array.
 */
public final class JXImage {
    private final int width;
    private final int height;
    private final int[] pixels;

    JXImage(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** The ARGB pixels themselves, not a copy; read only. */
    public int[] getPixels() {
        return pixels;
    }

    /** What the pixels take in memory, in bytes. */
    long bytes() {
        return 4L * pixels.length;
    }

    @Override
    public String toString() {
        return "JXImage[" + width + "x" + height + "]";
    }
}
