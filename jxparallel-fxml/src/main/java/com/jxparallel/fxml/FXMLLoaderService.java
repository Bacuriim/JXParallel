package com.jxparallel.fxml;

import com.jxparallel.core.JXParallel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class FXMLLoaderService {
    private final FXMLCache cache;

    public FXMLLoaderService() {
        this(null);
    }

    public FXMLLoaderService(FXMLCache cache) {
        if (cache != null) {
            this.cache = cache;
        } else {
            this.cache = createConfiguredCache();
        }
    }

    public CompletableFuture<Parent> loadAsync(String resourcePath) {
        return JXParallel.background(() -> load(resourcePath));
    }

    public Parent load(String resourcePath) {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource path must not be empty");
        }
        try {
            byte[] source = source(resourcePath);
            FXMLLoader loader = new FXMLLoader(getClass().getResource(resourcePath));
            return loader.load(new ByteArrayInputStream(source));
        } catch (IOException | IllegalStateException e) {
            throw new RuntimeException("Unable to load FXML resource: " + resourcePath, e);
        }
    }

    private byte[] source(String resourcePath) throws IOException {
        Object cached = cache.get(resourcePath);
        if (cached instanceof byte[]) {
            return (byte[]) cached;
        }
        URL resource = getClass().getResource(resourcePath);
        if (resource == null) {
            throw new IOException("FXML resource not found: " + resourcePath);
        }
        try (InputStream input = resource.openStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            byte[] source = output.toByteArray();
            cache.put(resourcePath, source);
            return source;
        }
    }

    public void clearCache() {
        cache.clear();
    }

    public FXMLCache cache() {
        return cache;
    }

    private static FXMLCache createConfiguredCache() {
        com.jxparallel.core.JXParallelConfig config = JXParallel.getConfig();
        return new FXMLCache(
                config.getFxmlCacheMaxSize(),
                parseDurationMillis(config.getFxmlCacheTtl()),
                config.isFxmlCacheEnabled()
                        ? CacheStrategy.parse(config.getFxmlCacheStrategy())
                        : CacheStrategy.NONE);
    }

    private static long parseDurationMillis(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        String normalized = value.trim().toLowerCase();
        try {
            if (normalized.endsWith("ms")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 2));
            }
            if (normalized.endsWith("s")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 1000L;
            }
            if (normalized.endsWith("m")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 60_000L;
            }
            if (normalized.endsWith("h")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 3_600_000L;
            }
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
