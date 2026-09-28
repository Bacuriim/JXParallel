package com.jxparallel.fx.fxml;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ResourceBundle;

import com.jxparallel.fx.util.Callback;

/**
 * JXParallel counterpart of {@link javafx.fxml.FXMLLoader}. Unchanged FXML (Scene Builder output,
 * {@code <?import javafx...?>}, fx:include) builds com.jxparallel.fx objects; each file is parsed
 * once and cached as an {@link FxmlTemplate}, so later loads only run constructors and setters.
 */
public class FXMLLoader {
    public static final String DEFAULT_CHARSET_NAME = "UTF-8";

    private URL location;
    private ResourceBundle resources;
    private Object root;
    private Object controller;
    private Callback<Class<?>, Object> controllerFactory;
    private ClassLoader classLoader;

    public FXMLLoader() {
        this(null, null);
    }

    public FXMLLoader(URL location) {
        this(location, null);
    }

    public FXMLLoader(URL location, ResourceBundle resources) {
        this.location = location;
        this.resources = resources;
    }

    public FXMLLoader(Charset charset) {
        this();
    }

    public URL getLocation() {
        return location;
    }

    public void setLocation(URL location) {
        this.location = location;
    }

    public ResourceBundle getResources() {
        return resources;
    }

    public void setResources(ResourceBundle resources) {
        this.resources = resources;
    }

    @SuppressWarnings("unchecked")
    public <T> T getRoot() {
        return (T) root;
    }

    public void setRoot(Object root) {
        this.root = root;
    }

    @SuppressWarnings("unchecked")
    public <T> T getController() {
        return (T) controller;
    }

    public void setController(Object controller) {
        this.controller = controller;
    }

    public Callback<Class<?>, Object> getControllerFactory() {
        return controllerFactory;
    }

    public void setControllerFactory(Callback<Class<?>, Object> controllerFactory) {
        this.controllerFactory = controllerFactory;
    }

    public ClassLoader getClassLoader() {
        return classLoader != null ? classLoader : getDefaultClassLoader();
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public static ClassLoader getDefaultClassLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : FXMLLoader.class.getClassLoader();
    }

    @SuppressWarnings("unchecked")
    public <T> T load() throws IOException {
        if (location == null) {
            throw new IllegalStateException("Location is not set.");
        }
        FxmlTemplate template;
        try {
            template = FxmlTemplate.of(location, getClassLoader());
        } catch (FxmlTemplate.Unsupported e) {
            throw new IOException(location + ": " + e.getMessage(), e);
        }
        FxmlTemplate.ControllerFactory factory = controllerFactory == null ? null : controllerFactory::call;
        FxmlTemplate.Result result;
        try {
            result = template.build(controller, factory);
        } catch (IllegalStateException e) {
            throw new IOException(e.getMessage(), e.getCause());
        }
        root = result.root;
        controller = result.controller;
        return (T) root;
    }

    public static <T> T load(URL location) throws IOException {
        return new FXMLLoader(location).load();
    }

    public static <T> T load(URL location, ResourceBundle resources) throws IOException {
        return new FXMLLoader(location, resources).load();
    }
}
