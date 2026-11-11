package com.jxparallel.ui.native2d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jxparallel.ui.resource.JXImage;
import com.jxparallel.ui.resource.JXImages;

/**
 * One size of a window's icon: ARGB pixels, row by row from the top. A window takes several sizes
 * ({@link JXWindow#setIcons}) and the system picks the closest for the title bar, the task bar and Alt+Tab. Free of GLFW.
 */
public final class JXWindowIcon {
    private final int width;
    private final int height;
    private final int[] argb;

    public JXWindowIcon(int width, int height, int[] argb) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("icon size must be positive: " + width + "x" + height);
        }
        if (argb == null || argb.length < (long) width * height) {
            throw new IllegalArgumentException("icon needs " + (long) width * height + " pixels");
        }
        this.width = width;
        this.height = height;
        this.argb = java.util.Arrays.copyOf(argb, width * height);
    }

    /** Decodes a picture file or resource ({@link JXImages#load(String)}: PNG, JPEG, GIF, BMP). */
    public static JXWindowIcon load(String source) throws IOException {
        JXImage image = JXImages.load(source);
        return new JXWindowIcon(image.getWidth(), image.getHeight(), image.getPixels());
    }

    /** One icon per source, the sizes the files have. */
    public static List<JXWindowIcon> load(String... sources) throws IOException {
        List<JXWindowIcon> out = new ArrayList<JXWindowIcon>();
        for (String source : sources) {
            out.add(load(source));
        }
        return out;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** A copy of the ARGB pixels. */
    public int[] getPixels() {
        return argb.clone();
    }

    /** The pixels as GLFW wants them: R, G, B, A bytes per pixel, row by row from the top. */
    byte[] rgba() {
        byte[] out = new byte[argb.length * 4];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            out[4 * i] = (byte) (p >> 16);
            out[4 * i + 1] = (byte) (p >> 8);
            out[4 * i + 2] = (byte) p;
            out[4 * i + 3] = (byte) (p >>> 24);
        }
        return out;
    }
}
