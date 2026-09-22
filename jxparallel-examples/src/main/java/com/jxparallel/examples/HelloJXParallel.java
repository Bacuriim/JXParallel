package com.jxparallel.examples;

import com.jxparallel.core.JXParallel;

import java.util.concurrent.CompletableFuture;

public final class HelloJXParallel {
    private HelloJXParallel() {
    }

    public static void main(String[] args) {
        JXParallel.start();
        CompletableFuture<String> future = JXParallel.background(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return "JXParallel ready";
        });

        future.thenAccept(System.out::println);
        future.join();
        JXParallel.shutdown();
    }
}
