package com.jxparallel.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

class FxWrapperIdentityTest {

    /**
     * The first wrapping of a JavaFX constant may load the JX class, whose static initializer wraps
     * that same constant: both must be one object, as applications compare with ==
     * (an application's {@code result.get() == ButtonType.OK}). Needs a JVM where the JX class was
     * never loaded, so it runs in a new one.
     */
    @Test
    void wrappingAConstantBeforeItsClassLoadsGivesTheConstant() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), FirstWrap.class.getName())
                .redirectErrorStream(true).start();
        String out;
        try (Scanner s = new Scanner(p.getInputStream(), "UTF-8").useDelimiter("\\A")) {
            out = s.hasNext() ? s.next().trim() : "";
        }
        p.waitFor();
        assertEquals("same", out);
    }

    /** Wraps javafx's ButtonType.OK before anything touched the JX ButtonType class. */
    public static final class FirstWrap {
        public static void main(String[] args) {
            Object wrapped = Fx.jx(javafx.scene.control.ButtonType.OK);
            System.out.print(wrapped == com.jxparallel.fx.scene.control.ButtonType.OK ? "same" : "different");
        }
    }
}
