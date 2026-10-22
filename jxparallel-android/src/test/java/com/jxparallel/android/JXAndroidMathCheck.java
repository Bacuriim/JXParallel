package com.jxparallel.android;

/**
 * Checks the Android-free arithmetic of the backend on a plain JVM (no Robolectric, no Android classes):
 * {@code javac -d build/check src/main/java/.../JXAndroidMath.java src/test/java/.../JXAndroidMathCheck.java}
 * then {@code java -cp build/check com.jxparallel.android.JXAndroidMathCheck}. Exits non-zero on a failure.
 */
public final class JXAndroidMathCheck {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.err.println("FAIL " + what);
        }
    }

    public static void main(String[] args) {
        float[] line = JXAndroidMath.lineMetrics(10.4f, 2.6f, 0.5f);
        check(line[0] == 10.4f && line[1] == 11 + 3 + 0.5f, "line metrics round ascent and descent up: " + line[1]);
        check(JXAndroidMath.logical(540f, 2.625f) == 205, "logical 540px at 2.625");
        check(JXAndroidMath.logical(0f, 2f) == 0, "logical 0");
        check(JXAndroidMath.logicalSize(1080, 2.625f) == 411, "logical size " + JXAndroidMath.logicalSize(1080, 2.625f));
        check(JXAndroidMath.logicalSize(0, 3f) == 1, "logical size is at least 1");
        check(JXAndroidMath.nodeKey(67) == 259, "DEL is backspace");
        check(JXAndroidMath.nodeKey(66) == 257 && JXAndroidMath.nodeKey(160) == 257, "enter");
        check(JXAndroidMath.nodeKey(111) == 256, "escape");
        check(JXAndroidMath.nodeKey(29) == -1, "letters are characters");
        check(JXAndroidMath.editsText("input") && JXAndroidMath.editsText("password") && JXAndroidMath.editsText("textarea"), "text types");
        check(!JXAndroidMath.editsText("button") && !JXAndroidMath.editsText(null), "other types");
        if (failures > 0) {
            System.exit(1);
        }
        System.out.println("JXAndroidMathCheck: all passed");
    }
}
