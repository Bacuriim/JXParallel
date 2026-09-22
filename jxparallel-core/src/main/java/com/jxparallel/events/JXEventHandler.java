package com.jxparallel.events;

public interface JXEventHandler<T extends JXEvent> {
    void handle(T event);
}
