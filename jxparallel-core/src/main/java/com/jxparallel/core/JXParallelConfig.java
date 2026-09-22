package com.jxparallel.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class JXParallelConfig {
    public static final String DEFAULT_CONFIG_FILE = "jx-parallel.config";

    private final int minThreads;
    private final int maxThreads;
    private final boolean autoScale;
    private final int queueCapacity;
    private final QueuePolicy queuePolicy;
    private final long keepAliveMillis;
    private final boolean metricsEnabled;
    private final boolean debug;
    private final JXParallelMode mode;
    private final boolean testingParallel;
    private final int testingMaxThreads;
    private final boolean fxmlCacheEnabled;
    private final int fxmlCacheMaxSize;
    private final String fxmlCacheTtl;
    private final String fxmlCacheStrategy;
    private final boolean daemonThreads;
    private final int threadPriority;

    public JXParallelConfig(
            int minThreads,
            int maxThreads,
            boolean autoScale,
            int queueCapacity,
            QueuePolicy queuePolicy,
            long keepAliveMillis,
            boolean metricsEnabled,
            boolean debug,
            JXParallelMode mode,
            boolean testingParallel,
            int testingMaxThreads,
            boolean fxmlCacheEnabled,
            int fxmlCacheMaxSize,
            String fxmlCacheTtl,
            String fxmlCacheStrategy,
            boolean daemonThreads,
            int threadPriority) {
        this.minThreads = Math.max(1, minThreads);
        this.maxThreads = Math.max(this.minThreads, maxThreads);
        this.autoScale = autoScale;
        this.queueCapacity = Math.max(1, queueCapacity);
        this.queuePolicy = queuePolicy == null ? QueuePolicy.BLOCK : queuePolicy;
        this.keepAliveMillis = Math.max(1L, keepAliveMillis);
        this.metricsEnabled = metricsEnabled;
        this.debug = debug;
        this.mode = mode == null ? JXParallelMode.BALANCED : mode;
        this.testingParallel = testingParallel;
        this.testingMaxThreads = Math.max(1, testingMaxThreads);
        this.fxmlCacheEnabled = fxmlCacheEnabled;
        this.fxmlCacheMaxSize = Math.max(1, fxmlCacheMaxSize);
        this.fxmlCacheTtl = fxmlCacheTtl == null ? "30m" : fxmlCacheTtl;
        this.fxmlCacheStrategy = fxmlCacheStrategy == null ? "LRU" : fxmlCacheStrategy;
        this.daemonThreads = daemonThreads;
        this.threadPriority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, threadPriority));
    }

    public int getMinThreads() {
        return minThreads;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public boolean isAutoScale() {
        return autoScale;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public QueuePolicy getQueuePolicy() {
        return queuePolicy;
    }

    public long getKeepAliveMillis() {
        return keepAliveMillis;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public boolean isDebug() {
        return debug;
    }

    public JXParallelMode getMode() {
        return mode;
    }

    public boolean isTestingParallel() {
        return testingParallel;
    }

    public int getTestingMaxThreads() {
        return testingMaxThreads;
    }

    public boolean isFxmlCacheEnabled() {
        return fxmlCacheEnabled;
    }

    public int getFxmlCacheMaxSize() {
        return fxmlCacheMaxSize;
    }

    public String getFxmlCacheTtl() {
        return fxmlCacheTtl;
    }

    public String getFxmlCacheStrategy() {
        return fxmlCacheStrategy;
    }

    public boolean isDaemonThreads() {
        return daemonThreads;
    }

    public int getThreadPriority() {
        return threadPriority;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JXParallelConfig defaults() {
        return builder().build();
    }

    public static JXParallelConfig fromProperties(Properties properties) {
        Builder builder = builder();
        if (properties == null) {
            return builder.build();
        }
        builder.minThreads(getInt(properties, "threads.min", 2));
        builder.maxThreads(getInt(properties, "threads.max", 8));
        builder.autoScale(getBoolean(properties, "threads.auto-scale", true));
        builder.queueCapacity(getInt(properties, "queue.capacity", 500));
        builder.queuePolicy(enumValue(properties.getProperty("queue.policy"), QueuePolicy.BLOCK));
        builder.keepAliveMillis(getLong(properties, "threads.keep-alive-ms", 30000L));
        builder.metricsEnabled(getBoolean(properties, "metrics.enabled", false));
        builder.debug(getBoolean(properties, "debug", false));
        builder.mode(enumValue(properties.getProperty("jxparallel.mode"), JXParallelMode.BALANCED));
        builder.testingParallel(getBoolean(properties, "testing.parallel", true));
        builder.testingMaxThreads(getInt(properties, "testing.max-threads", 8));
        builder.fxmlCacheEnabled(getBoolean(properties, "fxml.cache.enabled", true));
        builder.fxmlCacheMaxSize(getInt(properties, "fxml.cache.max-size", 100));
        builder.fxmlCacheTtl(properties.getProperty("fxml.cache.ttl", "30m"));
        builder.fxmlCacheStrategy(properties.getProperty("fxml.cache.strategy", "LRU"));
        builder.daemonThreads(getBoolean(properties, "threads.daemon", true));
        builder.threadPriority(getInt(properties, "threads.priority", Thread.NORM_PRIORITY));
        return builder.build();
    }

    public static JXParallelConfig loadEffectiveConfiguration(Path path) {
        Properties properties = new Properties();
        populateDefaults(properties);
        if (path != null && Files.exists(path)) {
            try (InputStream stream = Files.newInputStream(path)) {
                properties.load(stream);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read config file: " + path, e);
            }
        }
        applyEnvironmentOverrides(properties);
        applySystemPropertyOverrides(properties);
        return fromProperties(properties);
    }

    public static JXParallelConfig loadFromFile(Path path) throws IOException {
        Properties properties = new Properties();
        if (path == null || !Files.exists(path)) {
            return defaults();
        }
        try (InputStream stream = Files.newInputStream(path)) {
            properties.load(stream);
        }
        return fromProperties(properties);
    }

    public static JXParallelConfig loadFromFile(String fileName) throws IOException {
        return loadFromFile(Paths.get(fileName));
    }

    private static void populateDefaults(Properties properties) {
        Map<String, String> defaults = new LinkedHashMap<String, String>();
        defaults.put("threads.min", "2");
        defaults.put("threads.max", "8");
        defaults.put("threads.auto-scale", "true");
        defaults.put("queue.capacity", "500");
        defaults.put("queue.policy", "BLOCK");
        defaults.put("threads.keep-alive-ms", "30000");
        defaults.put("metrics.enabled", "false");
        defaults.put("debug", "false");
        defaults.put("jxparallel.mode", "BALANCED");
        defaults.put("testing.parallel", "true");
        defaults.put("testing.max-threads", "8");
        defaults.put("fxml.cache.enabled", "true");
        defaults.put("fxml.cache.max-size", "100");
        defaults.put("fxml.cache.ttl", "30m");
        defaults.put("fxml.cache.strategy", "LRU");
        defaults.put("threads.daemon", "true");
        defaults.put("threads.priority", String.valueOf(Thread.NORM_PRIORITY));
        defaults.forEach(properties::setProperty);
    }

    private static void applyEnvironmentOverrides(Properties properties) {
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = normalizePropertyKey(entry.getKey());
            if (key != null) {
                properties.setProperty(key, entry.getValue());
            }
        }
    }

    private static void applySystemPropertyOverrides(Properties properties) {
        Properties systemProperties = System.getProperties();
        for (String key : systemProperties.stringPropertyNames()) {
            String normalized = normalizePropertyKey(key);
            boolean recognized = normalized != null && (
                    normalized.startsWith("threads.")
                            || normalized.startsWith("queue.")
                            || normalized.startsWith("metrics.")
                            || normalized.equals("debug")
                            || normalized.startsWith("jxparallel.")
                            || normalized.startsWith("testing.")
                            || normalized.startsWith("fxml."));
            if (recognized) {
                properties.setProperty(normalized, systemProperties.getProperty(key));
            }
        }
    }

    private static String normalizePropertyKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        String normalized = key.trim().toLowerCase();
        for (String prefix : new String[]{"jxparallel.", "jxparallel_", "jx_parallel.", "jx_parallel_"}) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length());
                break;
            }
        }
        normalized = normalized.replace('-', '.').replace('_', '.');
        return normalized;
    }

    private static boolean getBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private static int getInt(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static long getLong(Properties properties, String key, long defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : Long.parseLong(value);
    }

    private static QueuePolicy enumValue(String value, QueuePolicy defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return QueuePolicy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private static JXParallelMode enumValue(String value, JXParallelMode defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return JXParallelMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    public static final class Builder {
        private int minThreads = 2;
        private int maxThreads = 8;
        private boolean autoScale = true;
        private int queueCapacity = 500;
        private QueuePolicy queuePolicy = QueuePolicy.BLOCK;
        private long keepAliveMillis = 30_000L;
        private boolean metricsEnabled = false;
        private boolean debug = false;
        private JXParallelMode mode = JXParallelMode.BALANCED;
        private boolean testingParallel = true;
        private int testingMaxThreads = 8;
        private boolean fxmlCacheEnabled = true;
        private int fxmlCacheMaxSize = 100;
        private String fxmlCacheTtl = "30m";
        private String fxmlCacheStrategy = "LRU";
        private boolean daemonThreads = true;
        private int threadPriority = Thread.NORM_PRIORITY;

        public Builder minThreads(int minThreads) {
            this.minThreads = minThreads;
            return this;
        }

        public Builder maxThreads(int maxThreads) {
            this.maxThreads = maxThreads;
            return this;
        }

        public Builder autoScale(boolean autoScale) {
            this.autoScale = autoScale;
            return this;
        }

        public Builder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        public Builder queuePolicy(QueuePolicy queuePolicy) {
            this.queuePolicy = queuePolicy;
            return this;
        }

        public Builder keepAliveMillis(long keepAliveMillis) {
            this.keepAliveMillis = keepAliveMillis;
            return this;
        }

        public Builder metricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder mode(JXParallelMode mode) {
            this.mode = mode == null ? JXParallelMode.BALANCED : mode;
            return this;
        }

        public Builder testingParallel(boolean testingParallel) {
            this.testingParallel = testingParallel;
            return this;
        }

        public Builder testingMaxThreads(int testingMaxThreads) {
            this.testingMaxThreads = testingMaxThreads;
            return this;
        }

        public Builder fxmlCacheEnabled(boolean fxmlCacheEnabled) {
            this.fxmlCacheEnabled = fxmlCacheEnabled;
            return this;
        }

        public Builder fxmlCacheMaxSize(int fxmlCacheMaxSize) {
            this.fxmlCacheMaxSize = fxmlCacheMaxSize;
            return this;
        }

        public Builder fxmlCacheTtl(String fxmlCacheTtl) {
            this.fxmlCacheTtl = fxmlCacheTtl;
            return this;
        }

        public Builder fxmlCacheStrategy(String fxmlCacheStrategy) {
            this.fxmlCacheStrategy = fxmlCacheStrategy;
            return this;
        }

        public Builder daemonThreads(boolean daemonThreads) {
            this.daemonThreads = daemonThreads;
            return this;
        }

        public Builder threadPriority(int threadPriority) {
            this.threadPriority = threadPriority;
            return this;
        }

        public JXParallelConfig build() {
            return new JXParallelConfig(
                    minThreads,
                    maxThreads,
                    autoScale,
                    queueCapacity,
                    queuePolicy,
                    keepAliveMillis,
                    metricsEnabled,
                    debug,
                    mode,
                    testingParallel,
                    testingMaxThreads,
                    fxmlCacheEnabled,
                    fxmlCacheMaxSize,
                    fxmlCacheTtl,
                    fxmlCacheStrategy,
                    daemonThreads,
                    threadPriority
            );
        }

        public void start() {
            JXParallel.applyConfiguration(build());
        }
    }
}
