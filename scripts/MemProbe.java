import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/**
 * Where the native mode's process memory goes: the private bytes (committed memory, the "peak
 * private" of the toolkit comparison) after each piece the native mode adds, in a fresh JVM.
 *
 *   fx    the JVM, then JavaFX's toolkit started as the native mode starts it (prism.order=sw):
 *         the event loop it keeps;
 *   gl    the JVM, then GLFW, a window with an OpenGL context as JXDisplay creates it (stencil 8),
 *         NanoVG's context with the fonts JX loads, the window shown at 1100x760 and 120 frames.
 *
 * Prints "JX_METRIC private_<stage>_bytes=n". On Windows the committed virtual memory size the JVM
 * reports is the process's private bytes (PagefileUsage).
 */
public class MemProbe {
    public static void main(String[] args) throws Exception {
        mark("jvm");
        if ("fx".equals(args[0])) {
            System.setProperty("prism.order", "sw");
            CountDownLatch started = new CountDownLatch(1);
            com.sun.javafx.application.PlatformImpl.startup(started::countDown);
            started.await();
            settle();
            mark("fx_toolkit");
            javafx.application.Platform.exit();
        } else {
            if (!GLFW.glfwInit()) {
                throw new IllegalStateException("glfwInit");
            }
            mark("glfw");
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            long window = GLFW.glfwCreateWindow(1100, 760, "probe", MemoryUtil.NULL, MemoryUtil.NULL);
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            settle();
            mark("gl_context");
            long vg = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
            String font = com.jxparallel.ui.text.JXTextEngine.get().getFontFile();
            if (font != null) {
                NanoVG.nvgCreateFont(vg, "sans", font);
            }
            String bold = com.jxparallel.ui.text.JXTextEngine.findBoldFontFile();
            if (bold != null) {
                NanoVG.nvgCreateFont(vg, "sans-bold", bold);
            }
            settle();
            mark("nanovg");
            GLFW.glfwShowWindow(window);
            for (int i = 0; i < 120; i++) {
                GLFW.glfwPollEvents();
                GL11.glClearColor(1, 1, 1, 1);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
                NanoVG.nvgBeginFrame(vg, 1100, 760, 1);
                NanoVG.nvgFontFace(vg, "sans");
                NanoVG.nvgFontSize(vg, 13);
                for (int r = 0; r < 40; r++) {
                    NanoVG.nvgText(vg, 10, 20 + r * 18, "Parâmetro " + r + " registro " + i);
                }
                NanoVG.nvgEndFrame(vg);
                GLFW.glfwSwapBuffers(window);
            }
            settle();
            mark("frames");
            NanoVGGL3.nvgDelete(vg);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
        System.exit(0);
    }

    /** Lets the driver's and the toolkit's threads finish their start-up. */
    private static void settle() throws InterruptedException {
        System.gc();
        Thread.sleep(1500);
    }

    private static void mark(String stage) {
        long committed = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getCommittedVirtualMemorySize();
        System.out.println("JX_METRIC private_" + stage + "_bytes=" + committed);
    }
}
