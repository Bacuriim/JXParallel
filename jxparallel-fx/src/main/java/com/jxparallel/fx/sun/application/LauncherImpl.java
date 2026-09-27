package com.jxparallel.fx.sun.application;

import com.jxparallel.fx.application.Application;
import com.jxparallel.fx.application.Launch;
import com.jxparallel.fx.application.Preloader;

/** JXParallel counterpart of JavaFX's internal {@code com.sun.javafx.application.LauncherImpl}. */
public final class LauncherImpl {
    private LauncherImpl() {
    }

    public static void launchApplication(Class<? extends Application> appClass,
                                         Class<? extends Preloader> preloaderClass, String[] args) {
        Launch.launch(appClass, preloaderClass, args);
    }

    public static void launchApplication(Class<? extends Application> appClass, String[] args) {
        Launch.launch(appClass, null, args);
    }
}
