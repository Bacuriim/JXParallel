package com.jxparallel.ui.native2d;

/**
 * The {@code translateX}, {@code translateY}, {@code scaleX}, {@code scaleY} and {@code rotate} (degrees) props of a
 * node, as JavaFX has them: the node is scaled, then rotated clockwise about the centre of its layout bounds, then moved.
 * They change where the node and its subtree are drawn and picked, never the layout bounds.
 *
 * <p>Affine maps are {@code double[6]} {@code {a, b, c, d, e, f}}: {@code x' = a x + c y + e}, {@code y' = b x + d y + f}.
 */
final class JXTransforms {
    private JXTransforms() {
    }

    /** True when the node has a transform that is not the identity. */
    static boolean has(JXNativeNode node) {
        return JXPaint.number(node, "translateX", 0) != 0 || JXPaint.number(node, "translateY", 0) != 0
                || JXPaint.number(node, "scaleX", 1) != 1 || JXPaint.number(node, "scaleY", 1) != 1
                || JXPaint.number(node, "rotate", 0) != 0;
    }

    /** Pushes the node's transform on the painter; matched by {@link JXPainter#restore}. */
    static void push(JXPainter p, JXNativeNode node) {
        p.transform((float) JXPaint.number(node, "translateX", 0), (float) JXPaint.number(node, "translateY", 0),
                (float) JXPaint.number(node, "scaleX", 1), (float) JXPaint.number(node, "scaleY", 1),
                (float) JXPaint.number(node, "rotate", 0), node.getX() + node.getWidth() / 2f, node.getY() + node.getHeight() / 2f);
    }

    /** The inverse of the node's transform (parent space to the node's own), or null when it is the identity or cannot be inverted. */
    static double[] inverse(JXNativeNode node) {
        double tx = JXPaint.number(node, "translateX", 0);
        double ty = JXPaint.number(node, "translateY", 0);
        double sx = JXPaint.number(node, "scaleX", 1);
        double sy = JXPaint.number(node, "scaleY", 1);
        double rotate = JXPaint.number(node, "rotate", 0);
        double cx = node.getX() + node.getWidth() / 2.0;
        double cy = node.getY() + node.getHeight() / 2.0;
        double radians = Math.toRadians(rotate);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        // forward: translate(c + t) * rotate * scale * translate(-c)
        double a = cos * sx;
        double b = sin * sx;
        double c = -sin * sy;
        double d = cos * sy;
        double e = cx + tx - (a * cx + c * cy);
        double f = cy + ty - (b * cx + d * cy);
        double det = a * d - b * c;
        if (Math.abs(det) < 1e-12) {
            return SINGULAR;
        }
        double ia = d / det;
        double ib = -b / det;
        double ic = -c / det;
        double id = a / det;
        return new double[] {ia, ib, ic, id, -(ia * e + ic * f), -(ib * e + id * f)};
    }

    /** A transform that collapses the plane: nothing under it can be picked. */
    static final double[] SINGULAR = new double[6];

    /** {@code outer} after {@code inner}: the point goes through {@code inner} first. {@code inner} may be null (identity). */
    static double[] then(double[] inner, double[] outer) {
        if (inner == null) {
            return outer;
        }
        return new double[] {
            outer[0] * inner[0] + outer[2] * inner[1], outer[1] * inner[0] + outer[3] * inner[1],
            outer[0] * inner[2] + outer[2] * inner[3], outer[1] * inner[2] + outer[3] * inner[3],
            outer[0] * inner[4] + outer[2] * inner[5] + outer[4], outer[1] * inner[4] + outer[3] * inner[5] + outer[5]};
    }

    static double x(double[] m, double x, double y) {
        return m == null ? x : m[0] * x + m[2] * y + m[4];
    }

    static double y(double[] m, double x, double y) {
        return m == null ? y : m[1] * x + m[3] * y + m[5];
    }

    /**
     * What the painters that cannot rotate or stretch (the software one and the damage finder that mirrors it) do with
     * a transform: scale uniformly about the centre (when scaleX and scaleY differ the scale is left out) and move; the
     * rotation is left out. Returns {scale factor, x offset, y offset} to apply as {@code q' = factor * q + offset}.
     */
    static float[] withoutRotation(float tx, float ty, float sx, float sy, float cx, float cy) {
        float s = sx == sy ? sx : 1f;
        return new float[] {s, cx * (1 - s) + tx, cy * (1 - s) + ty};
    }
}
