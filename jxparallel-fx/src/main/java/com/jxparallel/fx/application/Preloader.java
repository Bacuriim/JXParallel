package com.jxparallel.fx.application;

import com.jxparallel.fx.Fx;
import com.jxparallel.fx.stage.Stage;

/** JXParallel counterpart of {@link javafx.application.Preloader}, hosted by {@link FxPreloader}. */
public abstract class Preloader extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
    }

    public void handleProgressNotification(ProgressNotification info) {
    }

    public void handleStateChangeNotification(StateChangeNotification info) {
    }

    public void handleApplicationNotification(PreloaderNotification info) {
    }

    public boolean handleErrorNotification(ErrorNotification info) {
        return false;
    }

    public interface PreloaderNotification {
    }

    public static class ProgressNotification implements PreloaderNotification {
        private final double progress;

        public ProgressNotification(double progress) {
            this.progress = progress;
        }

        public double getProgress() {
            return progress;
        }
    }

    public static class StateChangeNotification implements PreloaderNotification {
        public enum Type {
            BEFORE_LOAD,
            BEFORE_INIT,
            BEFORE_START
        }

        private final Type type;
        private final Application application;

        public StateChangeNotification(Type type) {
            this(type, null);
        }

        public StateChangeNotification(Type type, Application application) {
            this.type = type;
            this.application = application;
        }

        public Type getType() {
            return type;
        }

        public Application getApplication() {
            return application;
        }
    }

    public static class ErrorNotification implements PreloaderNotification {
        private final String location;
        private final String details;
        private final Throwable cause;

        public ErrorNotification(String location, String details, Throwable cause) {
            this.location = location;
            this.details = details;
            this.cause = cause;
        }

        public String getLocation() {
            return location;
        }

        public String getDetails() {
            return details;
        }

        public Throwable getCause() {
            return cause;
        }

        @Override
        public String toString() {
            return "Preloader.ErrorNotification: " + details;
        }
    }

    /** Carries an application-defined JX notification through JavaFX. */
    private static final class Carrier implements javafx.application.Preloader.PreloaderNotification {
        final PreloaderNotification jx;

        Carrier(PreloaderNotification jx) {
            this.jx = jx;
        }
    }

    static javafx.application.Preloader.PreloaderNotification toFx(PreloaderNotification info) {
        if (info instanceof ProgressNotification) {
            return new javafx.application.Preloader.ProgressNotification(((ProgressNotification) info).getProgress());
        }
        return new Carrier(info);
    }

    static PreloaderNotification toJx(javafx.application.Preloader.PreloaderNotification info) {
        if (info instanceof Carrier) {
            return ((Carrier) info).jx;
        }
        if (info instanceof javafx.application.Preloader.ProgressNotification) {
            return new ProgressNotification(((javafx.application.Preloader.ProgressNotification) info).getProgress());
        }
        if (info instanceof javafx.application.Preloader.StateChangeNotification) {
            javafx.application.Preloader.StateChangeNotification s = (javafx.application.Preloader.StateChangeNotification) info;
            javafx.application.Application app = s.getApplication();
            return new StateChangeNotification(StateChangeNotification.Type.valueOf(s.getType().name()),
                    app instanceof FxApplication ? ((FxApplication) app).jx : null);
        }
        if (info instanceof javafx.application.Preloader.ErrorNotification) {
            javafx.application.Preloader.ErrorNotification e = (javafx.application.Preloader.ErrorNotification) info;
            return new ErrorNotification(e.getLocation(), e.getDetails(), e.getCause());
        }
        return null;
    }

    /** The JavaFX preloader the launcher creates; it hosts the pending JX preloader. */
    public static final class FxPreloader extends javafx.application.Preloader {
        final Preloader jx;

        public FxPreloader() throws ReflectiveOperationException {
            java.lang.reflect.Constructor<? extends Preloader> c = pendingPreloader.getDeclaredConstructor();
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
            jx.start((Stage) Fx.jx(primaryStage));
        }

        @Override
        public void stop() throws Exception {
            jx.stop();
        }

        @Override
        public void handleProgressNotification(javafx.application.Preloader.ProgressNotification info) {
            jx.handleProgressNotification((com.jxparallel.fx.application.Preloader.ProgressNotification) toJx(info));
        }

        @Override
        public void handleStateChangeNotification(javafx.application.Preloader.StateChangeNotification info) {
            jx.handleStateChangeNotification((com.jxparallel.fx.application.Preloader.StateChangeNotification) toJx(info));
        }

        @Override
        public void handleApplicationNotification(javafx.application.Preloader.PreloaderNotification info) {
            jx.handleApplicationNotification(toJx(info));
        }

        @Override
        public boolean handleErrorNotification(javafx.application.Preloader.ErrorNotification info) {
            return jx.handleErrorNotification((com.jxparallel.fx.application.Preloader.ErrorNotification) toJx(info));
        }
    }
}
