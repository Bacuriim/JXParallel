package com.jxparallel.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JXEventDispatcher {
    private final Map<JXEventType<?>, List<JXEventHandler<? extends JXEvent>>> handlers = new ConcurrentHashMap<JXEventType<?>, List<JXEventHandler<? extends JXEvent>>>();

    public <T extends JXEvent> void addListener(JXEventType<T> type, JXEventHandler<? super T> handler) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        synchronized (handlers) {
            List<JXEventHandler<? extends JXEvent>> list = handlers.get(type);
            if (list == null) {
                list = Collections.synchronizedList(new ArrayList<JXEventHandler<? extends JXEvent>>());
                handlers.put(type, list);
            }
            list.add(handler);
        }
    }

    public <T extends JXEvent> void removeListener(JXEventType<T> type, JXEventHandler<? super T> handler) {
        if (type == null || handler == null) {
            return;
        }
        synchronized (handlers) {
            List<JXEventHandler<? extends JXEvent>> list = handlers.get(type);
            if (list != null) {
                list.remove(handler);
            }
        }
    }

    public void dispatch(JXEvent event) {
        if (event == null) {
            return;
        }
        List<JXEventHandler<? extends JXEvent>> listeners = handlers.get(new JXEventType<JXEvent>(event.getClass().getSimpleName()));
        if (listeners == null) {
            return;
        }
        List<JXEventHandler<? extends JXEvent>> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<JXEventHandler<? extends JXEvent>>(listeners);
        }
        for (JXEventHandler<? extends JXEvent> listener : snapshot) {
            @SuppressWarnings("unchecked")
            JXEventHandler<JXEvent> cast = (JXEventHandler<JXEvent>) listener;
            cast.handle(event);
        }
    }

    public void dispatch(JXEventType<?> type, JXEvent event) {
        if (type == null || event == null) {
            return;
        }
        List<JXEventHandler<? extends JXEvent>> listeners = handlers.get(type);
        if (listeners == null) {
            return;
        }
        List<JXEventHandler<? extends JXEvent>> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<JXEventHandler<? extends JXEvent>>(listeners);
        }
        for (JXEventHandler<? extends JXEvent> listener : snapshot) {
            @SuppressWarnings("unchecked")
            JXEventHandler<JXEvent> cast = (JXEventHandler<JXEvent>) listener;
            cast.handle(event);
        }
    }
}
