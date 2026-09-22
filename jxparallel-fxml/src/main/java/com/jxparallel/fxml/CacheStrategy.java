package com.jxparallel.fxml;

public enum CacheStrategy {
    NONE,
    LRU,
    TTL,
    LRU_TTL;

    public static CacheStrategy parse(String value) {
        if (value == null) {
            return LRU;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_').replace('+', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return LRU;
        }
    }
}
