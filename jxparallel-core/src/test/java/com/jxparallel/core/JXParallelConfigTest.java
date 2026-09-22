package com.jxparallel.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXParallelConfigTest {
    @Test
    void shouldCreateDefaultConfig() {
        JXParallelConfig config = JXParallelConfig.defaults();
        assertEquals(2, config.getMinThreads());
        assertEquals(8, config.getMaxThreads());
        assertTrue(config.isAutoScale());
    }

    @Test
    void shouldLoadConfigFromProperties() {
        Properties properties = new Properties();
        properties.setProperty("threads.min", "3");
        properties.setProperty("threads.max", "12");
        properties.setProperty("jxparallel.mode", "LIGHT");
        properties.setProperty("testing.parallel", "false");

        JXParallelConfig config = JXParallelConfig.fromProperties(properties);
        assertEquals(3, config.getMinThreads());
        assertEquals(12, config.getMaxThreads());
        assertEquals(JXParallelMode.LIGHT, config.getMode());
        assertEquals(false, config.isTestingParallel());
    }

    @Test
    void shouldLoadConfigFromFile() throws IOException {
        Path file = Files.createTempFile("jx-parallel", ".config");
        Files.write(file, "threads.min=4\nthreads.max=16\n".getBytes(StandardCharsets.UTF_8));

        JXParallelConfig config = JXParallelConfig.loadFromFile(file);
        assertEquals(4, config.getMinThreads());
        assertEquals(16, config.getMaxThreads());
    }

    @Test
    void shouldHonorSystemPropertyOverride() {
        String previous = System.getProperty("jxparallel.threads.min");
        try {
            System.setProperty("jxparallel.threads.min", "5");
            JXParallelConfig config = JXParallelConfig.loadEffectiveConfiguration(null);
            assertEquals(5, config.getMinThreads());
        } finally {
            if (previous == null) {
                System.clearProperty("jxparallel.threads.min");
            } else {
                System.setProperty("jxparallel.threads.min", previous);
            }
        }
    }
}
