package com.jxparallel.fx.application;

import java.util.List;
import java.util.Map;

import com.jxparallel.fx.Fx;
import com.jxparallel.fx.stage.Stage;

/**
 * JXParallel counterpart of {@link javafx.application.Application}. JavaFX's launcher starts
 * {@link FxApplication}, which creates the JX application and forwards the life cycle to it.
 */
public abstract class Application {
    public static final String STYLESHEET_CASPIAN = javafx.application.Application.STYLESHEET_CASPIAN;
    public static final String STYLESHEET_MODENA = javafx.application.Application.STYLESHEET_MODENA;

    /** Classes the JavaFX launcher should instantiate next (set by launch before handing over). */
    static volatile Class<? extends Application> pendingApplication;
    static volatile Class<? extends Preloader> pendingPreloader;

    javafx.application.Application fx;

    public void init() throws Exception {
    }

    public abstract void start(Stage primaryStage) throws Exception;

    public void stop() throws Exception {
    }

    public final Parameters getParameters() {
        javafx.application.Application.Parameters p = fx == null ? null : fx.getParameters();
        return p == null ? null : new Parameters(p);
    }

    public final javafx.application.HostServices getHostServices() {
        return fx.getHostServices();
    }

    public final void notifyPreloader(Preloader.PreloaderNotification info) {
        fx.notifyPreloader(Preloader.toFx(info));
    }

    public static void launch(Class<? extends Application> appClass, String... args) {
        pendingApplication = appClass;
        if (com.jxparallel.fx.Fx.NATIVE) {
            // loads Fx, whose static setup chooses JavaFX's pipeline before the toolkit starts
            com.jxparallel.fx.Fx.class.getName();
        }
        javafx.application.Application.launch(FxApplication.class, args);
    }

    public static void launch(String... args) {
        launch(callerApplication(), args);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Application> callerApplication() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            try {
                Class<?> c = Class.forName(frame.getClassName(), false, Thread.currentThread().getContextClassLoader());
                if (Application.class.isAssignableFrom(c) && c != Application.class) {
                    return (Class<? extends Application>) c;
                }
            } catch (ClassNotFoundException ignored) {
                // frames of other class loaders
            }
        }
        throw new RuntimeException("Unable to find the Application class that called launch()");
    }

    public static void setUserAgentStylesheet(String url) {
        javafx.application.Application.setUserAgentStylesheet(url);
    }

    public static String getUserAgentStylesheet() {
        return javafx.application.Application.getUserAgentStylesheet();
    }

    /** JXParallel counterpart of {@link javafx.application.Application.Parameters}. */
    public static final class Parameters {
        private final javafx.application.Application.Parameters fx;

        Parameters(javafx.application.Application.Parameters fx) {
            this.fx = fx;
        }

        public List<String> getRaw() {
            return fx.getRaw();
        }

        public List<String> getUnnamed() {
            return fx.getUnnamed();
        }

        public Map<String, String> getNamed() {
            return fx.getNamed();
        }
    }

    /** The JavaFX application the launcher creates; it hosts the pending JX application. */
    public static final class FxApplication extends javafx.application.Application {
        final Application jx;

        public FxApplication() throws ReflectiveOperationException {
            java.lang.reflect.Constructor<? extends Application> c = pendingApplication.getDeclaredConstructor();
            c.setAccessible(true);
            jx = c.newInstance();
            jx.fx = this;
        }

        @Override
        public void init() throws Exception {
            jx.init();
        }

        @Override
        public void start(javafx.stage.Stage primaryStage) throws Exception {
            // native mode: the primary stage is a native window; JavaFX's stays hidden and only runs the event loop
            jx.start(Fx.NATIVE ? new Stage() : (Stage) Fx.jx(primaryStage));
        }

        @Override
        public void stop() throws Exception {
            jx.stop();
        }
    }
}
