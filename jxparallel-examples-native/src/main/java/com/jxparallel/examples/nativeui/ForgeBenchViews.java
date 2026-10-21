package com.jxparallel.examples.nativeui;

import com.jxparallel.forge.JxmlView;

/** The 20 generated views and their controllers, referenced directly as an application would. */
final class ForgeBenchViews {
    private ForgeBenchViews() {
    }

    @SuppressWarnings("rawtypes")
    static JxmlView view(int screen, boolean plain) {
        switch (screen) {
            case 1: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen01View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen01View();
            case 2: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen02View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen02View();
            case 3: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen03View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen03View();
            case 4: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen04View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen04View();
            case 5: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen05View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen05View();
            case 6: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen06View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen06View();
            case 7: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen07View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen07View();
            case 8: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen08View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen08View();
            case 9: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen09View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen09View();
            case 10: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen10View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen10View();
            case 11: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen11View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen11View();
            case 12: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen12View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen12View();
            case 13: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen13View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen13View();
            case 14: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen14View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen14View();
            case 15: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen15View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen15View();
            case 16: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen16View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen16View();
            case 17: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen17View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen17View();
            case 18: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen18View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen18View();
            case 19: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen19View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen19View();
            case 20: return plain ? new com.jxparallel.examples.nativeui.forgebench.plain.Screen20View() : new com.jxparallel.examples.nativeui.forgebench.gen.Screen20View();
            default: throw new IllegalArgumentException("screen " + screen);
        }
    }

    static Object controller(int screen) {
        switch (screen) {
            case 1: return new com.jxparallel.examples.nativeui.forgebench.Screen01Controller();
            case 2: return new com.jxparallel.examples.nativeui.forgebench.Screen02Controller();
            case 3: return new com.jxparallel.examples.nativeui.forgebench.Screen03Controller();
            case 4: return new com.jxparallel.examples.nativeui.forgebench.Screen04Controller();
            case 5: return new com.jxparallel.examples.nativeui.forgebench.Screen05Controller();
            case 6: return new com.jxparallel.examples.nativeui.forgebench.Screen06Controller();
            case 7: return new com.jxparallel.examples.nativeui.forgebench.Screen07Controller();
            case 8: return new com.jxparallel.examples.nativeui.forgebench.Screen08Controller();
            case 9: return new com.jxparallel.examples.nativeui.forgebench.Screen09Controller();
            case 10: return new com.jxparallel.examples.nativeui.forgebench.Screen10Controller();
            case 11: return new com.jxparallel.examples.nativeui.forgebench.Screen11Controller();
            case 12: return new com.jxparallel.examples.nativeui.forgebench.Screen12Controller();
            case 13: return new com.jxparallel.examples.nativeui.forgebench.Screen13Controller();
            case 14: return new com.jxparallel.examples.nativeui.forgebench.Screen14Controller();
            case 15: return new com.jxparallel.examples.nativeui.forgebench.Screen15Controller();
            case 16: return new com.jxparallel.examples.nativeui.forgebench.Screen16Controller();
            case 17: return new com.jxparallel.examples.nativeui.forgebench.Screen17Controller();
            case 18: return new com.jxparallel.examples.nativeui.forgebench.Screen18Controller();
            case 19: return new com.jxparallel.examples.nativeui.forgebench.Screen19Controller();
            case 20: return new com.jxparallel.examples.nativeui.forgebench.Screen20Controller();
            default: throw new IllegalArgumentException("screen " + screen);
        }
    }
}