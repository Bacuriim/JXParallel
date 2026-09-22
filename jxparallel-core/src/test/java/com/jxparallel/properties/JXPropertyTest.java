package com.jxparallel.properties;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXPropertyTest {
    @Test
    void shouldNotifyListenersOnValueChange() {
        JXProperty<String> property = new JXProperty<String>("initial");
        final AtomicReference<String> oldValue = new AtomicReference<String>();
        final AtomicReference<String> newValue = new AtomicReference<String>();

        property.addListener(new JXChangeListener<String>() {
            @Override
            public void changed(JXChangeEvent<String> event) {
                oldValue.set(event.getOldValue());
                newValue.set(event.getNewValue());
            }
        });

        property.set("updated");

        assertEquals("initial", oldValue.get());
        assertEquals("updated", newValue.get());
    }

    @Test
    void shouldUnbindSourceListener() {
        JXProperty<String> source = new JXProperty<String>("a");
        JXProperty<String> target = new JXProperty<String>();

        target.bind(source);
        source.set("b");
        target.unbind();
        source.set("c");

        assertEquals("b", target.get());
    }
}
