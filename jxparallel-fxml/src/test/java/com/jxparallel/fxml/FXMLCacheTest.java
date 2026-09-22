package com.jxparallel.fxml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FXMLCacheTest {
    @Test
    void shouldEvictLeastRecentlyUsedEntry() {
        FXMLCache cache = new FXMLCache(1, 0L);
        Object first = new byte[]{1};
        Object second = new byte[]{2};

        cache.put("first", first);
        cache.put("second", second);

        assertEquals(1, cache.size());
        assertSame(second, cache.get("second"));
    }
}
