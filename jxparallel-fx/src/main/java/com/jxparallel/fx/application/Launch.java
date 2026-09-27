package com.jxparallel.fx.application;

/** Hands a JX application (and preloader) to JavaFX's launcher. */
public final class Launch {
    private Launch() {
    }

    public static void launch(Class<? extends Application> appClass, Class<? extends Preloader> preloaderClass, String[] args) {
        Application.pendingApplication = appClass;
        Application.pendingPreloader = preloaderClass;
        com.sun.javafx.application.LauncherImpl.launchApplication(Application.FxApplication.class,
                preloaderClass == null ? null : Preloader.FxPreloader.class, args);
    }
}
