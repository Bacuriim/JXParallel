package com.jxparallel.properties;

public class JXBooleanProperty extends JXProperty<Boolean> {
    public JXBooleanProperty() {
        super(Boolean.FALSE);
    }

    public JXBooleanProperty(boolean initialValue) {
        super(Boolean.valueOf(initialValue));
    }

    public void set(boolean value) {
        super.set(Boolean.valueOf(value));
    }

    public boolean getBoolean() {
        return Boolean.TRUE.equals(super.get());
    }
}
