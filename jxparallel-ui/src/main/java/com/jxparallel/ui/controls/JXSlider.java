package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXDoubleProperty;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXSlider implements JXComponent {
    private final JXDoubleProperty min = new JXDoubleProperty(0.0);
    private final JXDoubleProperty max = new JXDoubleProperty(100.0);
    private final JXDoubleProperty value = new JXDoubleProperty(0.0);

    public double getMin() {
        return min.getDouble();
    }

    public void setMin(double value) {
        min.set(value);
        setValue(getValue());
    }

    public double getMax() {
        return max.getDouble();
    }

    public void setMax(double value) {
        max.set(Math.max(getMin(), value));
        setValue(getValue());
    }

    public double getValue() {
        return value.getDouble();
    }

    public void setValue(double next) {
        value.set(Math.max(getMin(), Math.min(getMax(), next)));
    }

    public JXDoubleProperty valueProperty() {
        return value;
    }

    @Override
    public JXElement render() {
        return JXElement.of("slider", JXProps.builder()
                .set("min", getMin())
                .set("max", getMax())
                .set("value", getValue())
                .build());
    }
}
