package com.jxparallel.fx.nativeimpl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

import com.jxparallel.fx.Fx;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Native-mode implementation of the JX node API. Generated methods of node classes call
 * {@link #call} when {@link Fx#NATIVE} is on. Most of the API is bean-shaped (getX, setX, isX,
 * xProperty, list getters, layouts' static setters) and is served generically from the node's
 * {@link NativeModel}; anything else goes to a registered implementation, or is recorded in
 * {@link #missing()} and answered with a default so a whole screen can be checked in one run.
 */
public final class Native {
    /** Something a generated method cannot express as a bean access. */
    public interface Impl {
        Object call(Object self, NativeModel model, Object[] args) throws Exception;
    }

    private static final Map<String, Impl> IMPLS = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = new ConcurrentSkipListSet<>();
    /** JavaFX defaults that differ from the Java default of the type. */
    private static final Map<String, Object> DEFAULTS = new HashMap<>();
    private static volatile Consumer<NativeModel> onChange = model -> { };

    static {
        DEFAULTS.put("visible", true);
        DEFAULTS.put("managed", true);
        DEFAULTS.put("opacity", 1.0);
        DEFAULTS.put("scaleX", 1.0);
        DEFAULTS.put("scaleY", 1.0);
        DEFAULTS.put("fillWidth", true);
        DEFAULTS.put("fillHeight", true);
        DEFAULTS.put("text", "");
        DEFAULTS.put("editable", true);
        DEFAULTS.put("wrapText", false);
        for (String size : new String[]{"minWidth", "prefWidth", "maxWidth", "minHeight", "prefHeight", "maxHeight"}) {
            DEFAULTS.put(size, -1.0);
        }
        NativeImpls.register();
    }

    private Native() {
    }

    public static void register(String key, Impl impl) {
        IMPLS.put(key, impl);
    }

    /** Parts of the API a native run needed and did not have, "Labeled.setGraphic(Node)". */
    public static Set<String> missing() {
        return MISSING;
    }

    public static void onChange(Consumer<NativeModel> listener) {
        onChange = listener;
    }

    static void changed(NativeModel model) {
        onChange.accept(model);
    }

    /** A native node for a JavaFX node class; named constructor arguments become property values. */
    public static NativeModel create(Class<?> fxType, String[] names, Object[] args) {
        NativeModel model = new NativeModel(fxType);
        for (int i = 0; i < args.length; i++) {
            if (names[i] == null) {
                // JavaFX 8 leaves the common constructors unannotated: Label(String, Node), VBox(double, Node...).
                Object a = args[i];
                names[i] = a instanceof String ? "text" : a instanceof Object[] ? "children"
                        : a instanceof Number ? "spacing" : a instanceof Fx.Backed ? "graphic" : null;
            }
            if (names[i] == null) {
                MISSING.add("new " + fxType.getSimpleName() + "(unnamed argument " + i + ")");
            } else if (args[i] instanceof Object[] && "children".equals(names[i])) {
                for (Object child : (Object[]) args[i]) {
                    model.children.add(Fx.fx(child));
                }
            } else {
                property(model, names[i], args[i] == null ? Object.class : unbox(args[i].getClass())).setValue(Fx.fx(args[i]));
            }
        }
        if (fxType == javafx.scene.control.Spinner.class && model.values.containsKey("min")) {
            // Like Spinner(min, max, initialValue): the constructor builds the value factory.
            Object min = value(model, "min");
            Object max = value(model, "max");
            Object initial = value(model, "initialValue");
            Object factory = min instanceof Integer
                    ? new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory((Integer) min, (Integer) max,
                    initial instanceof Integer ? (Integer) initial : (Integer) min)
                    : new javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory(((Number) min).doubleValue(),
                    ((Number) max).doubleValue(), initial instanceof Number ? ((Number) initial).doubleValue() : ((Number) min).doubleValue());
            property(model, "valueFactory", Object.class).setValue(factory);
        }
        return model;
    }

    /**
     * A JX node method in native mode. {@code self} is null for static methods; types are the JX
     * erasures of the declared parameters and result.
     */
    public static Object call(Object self, Class<?> declaring, String name, Class<?>[] params, Class<?> returns, Object... args) {
        String key = declaring.getSimpleName() + "." + name + signature(params);
        Impl impl = IMPLS.get(key);
        NativeModel model = self == null ? null : (NativeModel) ((Fx.Backed) self).fxPeer();
        try {
            if (impl != null) {
                return impl.call(self, model, args);
            }
            if (model == null) {
                return staticCall(declaring, name, params, returns, args, key);
            }
            if (name.equals("getChildren") && params.length == 0) {
                return Fx.jx(model.children);
            }
            if (name.startsWith("set") && name.length() > 3 && params.length == 1 && List.class.isAssignableFrom(params[0])) {
                // setItems(list): the node keeps that very list, as JavaFX does.
                model.values.put(bean(name, 3), Fx.fx(args[0]));
                changed(model);
                return null;
            }
            if (name.startsWith("set") && name.length() > 3 && params.length == 1) {
                property(model, bean(name, 3), params[0]).setValue(Fx.fx(args[0]));
                changed(model);
                return null;
            }
            if (params.length == 0 && name.endsWith("Property") && name.length() > 8) {
                return Fx.jx(property(model, name.substring(0, name.length() - 8), valueType(returns)));
            }
            String getter = name.startsWith("get") && name.length() > 3 ? bean(name, 3)
                    : name.startsWith("is") && name.length() > 2 ? bean(name, 2) : null;
            if (getter != null && params.length == 0) {
                if (Collection.class.isAssignableFrom(returns)) {
                    return Fx.jx(list(model, getter));
                }
                return read(model, getter, returns);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw Fx.sneaky(e);
        }
        MISSING.add(key);
        return zero(returns);
    }

    /** Layouts' static setters and getters: GridPane.setColumnIndex(child, 2) stores on the child. */
    private static Object staticCall(Class<?> declaring, String name, Class<?>[] params, Class<?> returns, Object[] args, String key) {
        Object first = args.length > 0 ? Fx.fx(args[0]) : null;
        if (first instanceof NativeModel) {
            NativeModel child = (NativeModel) first;
            if (name.startsWith("set") && params.length == 2) {
                child.constraints.put(declaring.getSimpleName() + "." + bean(name, 3), Fx.fx(args[1]));
                changed(child);
                return null;
            }
            if (name.startsWith("get") && params.length == 1) {
                return Fx.jx(child.constraints.get(declaring.getSimpleName() + "." + bean(name, 3)));
            }
        }
        MISSING.add(key);
        return zero(returns);
    }

    /** The JavaFX-side property holding a bean value, created on first use. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Property<Object> property(NativeModel model, String name, Class<?> type) {
        Object existing = model.values.get(name);
        if (existing instanceof Property) {
            return (Property<Object>) existing;
        }
        Object initial = DEFAULTS.get(name);
        Property created;
        if (type == String.class) {
            created = new SimpleStringProperty(model, name, (String) (initial instanceof String ? initial : null));
        } else if (type == boolean.class || type == Boolean.class) {
            created = new SimpleBooleanProperty(model, name, Boolean.TRUE.equals(initial));
        } else if (type == double.class || type == Double.class) {
            created = new SimpleDoubleProperty(model, name, initial instanceof Number ? ((Number) initial).doubleValue() : 0);
        } else if (type == int.class || type == Integer.class) {
            created = new SimpleIntegerProperty(model, name, initial instanceof Number ? ((Number) initial).intValue() : 0);
        } else if (type == long.class || type == Long.class) {
            created = new SimpleLongProperty(model, name);
        } else if (type == float.class || type == Float.class) {
            created = new SimpleFloatProperty(model, name);
        } else {
            created = new SimpleObjectProperty<>(model, name, initial);
        }
        created.addListener((obs, before, after) -> changed(model));
        model.values.put(name, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    static ObservableList<Object> list(NativeModel model, String name) {
        Object existing = model.values.get(name);
        if (existing instanceof ObservableList) {
            return (ObservableList<Object>) existing;
        }
        if (existing instanceof List) {
            ObservableList<Object> wrapped = FXCollections.observableList((List<Object>) existing);
            model.values.put(name, wrapped);
            return wrapped;
        }
        ObservableList<Object> created = FXCollections.observableArrayList();
        created.addListener((javafx.beans.InvalidationListener) o -> changed(model));
        model.values.put(name, created);
        return created;
    }

    /** A bean value for rendering (JavaFX-side), or the JavaFX default when never set. */
    public static Object value(NativeModel model, String name) {
        Object v = model.values.get(name);
        if (v instanceof ObservableValue) {
            return ((ObservableValue<?>) v).getValue();
        }
        return v != null ? v : DEFAULTS.get(name);
    }

    public static Object constraint(NativeModel model, String key) {
        return model.constraints.get(key);
    }

    private static Object read(NativeModel model, String name, Class<?> returns) {
        Object v = value(model, name);
        if (v == null) {
            return zero(returns);
        }
        v = Fx.jx(v);
        if (returns == double.class || returns == Double.class) {
            return ((Number) v).doubleValue();
        }
        if (returns == int.class || returns == Integer.class) {
            return ((Number) v).intValue();
        }
        return v;
    }

    private static Class<?> valueType(Class<?> propertyType) {
        String n = propertyType.getSimpleName();
        if (n.contains("String")) {
            return String.class;
        }
        if (n.contains("Boolean")) {
            return boolean.class;
        }
        if (n.contains("Double")) {
            return double.class;
        }
        if (n.contains("Integer")) {
            return int.class;
        }
        if (n.contains("Long")) {
            return long.class;
        }
        if (n.contains("Float")) {
            return float.class;
        }
        return Object.class;
    }

    private static Class<?> unbox(Class<?> c) {
        return c;
    }

    private static String bean(String method, int prefix) {
        return Character.toLowerCase(method.charAt(prefix)) + method.substring(prefix + 1);
    }

    private static String signature(Class<?>[] params) {
        StringBuilder s = new StringBuilder("(");
        for (int i = 0; i < params.length; i++) {
            s.append(i == 0 ? "" : ",").append(params[i].getSimpleName());
        }
        return s.append(')').toString();
    }

    static Object zero(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    /** For tests and tools: the models of a JX node list. */
    public static List<Object> childrenOf(NativeModel model) {
        return model.children;
    }
}
