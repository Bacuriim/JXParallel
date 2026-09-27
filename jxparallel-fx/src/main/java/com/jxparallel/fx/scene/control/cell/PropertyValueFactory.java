package com.jxparallel.fx.scene.control.cell;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jxparallel.fx.Fx;
import com.jxparallel.fx.beans.property.ReadOnlyObjectWrapper;
import com.jxparallel.fx.beans.value.ObservableValue;
import com.jxparallel.fx.scene.control.TableColumn.CellDataFeatures;
import com.jxparallel.fx.util.Callback;

/**
 * JXParallel counterpart of {@link javafx.scene.control.cell.PropertyValueFactory}. The row object's
 * {@code nameProperty()} is looked up here, not by JavaFX, because application models now return JX
 * properties that JavaFX's reflective lookup would try to cast to its own. Same rules as JavaFX: the
 * property method if there is one, else the getter's value wrapped in a read-only property.
 */
public class PropertyValueFactory<S, T> implements Callback<CellDataFeatures<S, T>, ObservableValue<T>> {
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final String property;
    private final Map<Class<?>, Method[]> methods = new ConcurrentHashMap<>();

    public PropertyValueFactory(@javafx.beans.NamedArg("property") String property) {
        this.property = property;
    }

    public final String getProperty() {
        return property;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ObservableValue<T> call(CellDataFeatures<S, T> param) {
        S row = param.getValue();
        if (row == null || property == null || property.isEmpty()) {
            return null;
        }
        Method[] found = methods.computeIfAbsent(row.getClass(), this::lookup);
        try {
            if (found[0] != NONE) {
                return (ObservableValue<T>) Fx.jx(found[0].invoke(row));
            }
            if (found[1] != NONE) {
                return new ReadOnlyObjectWrapper<>((T) found[1].invoke(row));
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /** {nameProperty(), getName() or isName()}, NONE where missing. */
    private Method[] lookup(Class<?> type) {
        String capital = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        return new Method[]{method(type, property + "Property"), getter(type, capital)};
    }

    private static Method getter(Class<?> type, String capital) {
        Method get = method(type, "get" + capital);
        return get != NONE ? get : method(type, "is" + capital);
    }

    private static Method method(Class<?> type, String name) {
        try {
            Method m = type.getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException | SecurityException e) {
            return NONE;
        }
    }
}
