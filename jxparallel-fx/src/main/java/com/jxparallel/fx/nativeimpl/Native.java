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
        DEFAULTS.put("scaleZ", 1.0);
        // transitions: an unset from/to is NaN (start from the node's value, end at from + by)
        for (String end : new String[]{"fromValue", "toValue", "fromX", "fromY", "fromZ", "toX", "toY", "toZ", "fromAngle", "toAngle"}) {
            DEFAULTS.put(end, Double.NaN);
        }
        DEFAULTS.put("duration", javafx.util.Duration.millis(400));
        DEFAULTS.put("fillWidth", true);
        DEFAULTS.put("fillHeight", true);
        DEFAULTS.put("text", "");
        DEFAULTS.put("editable", true);
        DEFAULTS.put("wrapText", false);
        for (String size : new String[]{"minWidth", "prefWidth", "maxWidth", "minHeight", "prefHeight", "maxHeight"}) {
            DEFAULTS.put(size, -1.0);
        }
        NativeImpls.register();
        NativeRuntime.register();
        // other modules (jxparallel-fx-controlsfx) add their native implementations
        for (Runnable extension : java.util.ServiceLoader.load(Runnable.class, Native.class.getClassLoader())) {
            if (extension.getClass().getName().startsWith("com.jxparallel.")) {
                extension.run();
            }
        }
    }

    private Native() {
    }

    public static void register(String key, Impl impl) {
        IMPLS.put(key, impl);
    }

    /** Keys of the registered implementations ("Parent.lookup(String)"), for checks. */
    static java.util.Set<String> registeredKeys() {
        return java.util.Collections.unmodifiableSet(IMPLS.keySet());
    }

    /** Parts of the API a native run needed and did not have, "Labeled.setGraphic(Node)". */
    public static Set<String> missing() {
        return MISSING;
    }

    public static void onChange(Consumer<NativeModel> listener) {
        onChange = listener;
    }

    static void changed(NativeModel model) {
        if (model.is(javafx.scene.Scene.class)) {
            NativeElements.invalidateAll(); // its stylesheets apply to every node
        }
        NativeElements.invalidate(model);
        onChange.accept(model);
    }

    /**
     * Properties CSS matching reads (classes, id, inline style, pseudo-class states) or descendants
     * inherit (disabled): a descendant selector can make a change here restyle any node below.
     */
    private static final java.util.Set<String> SUBTREE_KEYS = new java.util.HashSet<>(java.util.Arrays.asList(
            "disable", "styleClass", "id", "style", "stylesheets", "selected", "index", "empty", "expanded",
            "showing", "defaultButton", "cancelButton", "visible", "managed", "focused"));

    /**
     * Properties that refer to another node without showing it (a cell's table, a toggle's group,
     * a stage's owner): that node keeps its parent, so its subtree (a table with all its cells) stays.
     */
    private static final java.util.Set<String> REFERENCE_KEYS = new java.util.HashSet<>(java.util.Arrays.asList(
            "tableView", "tableRow", "tableColumn", "listView", "treeView", "treeTableView", "toggleGroup",
            "selectedToggle", "owner", "labelFor", "expandedPane", "item", "selectedItem", "result", "skinnable"));

    /** A property or list of the node changed; {@code value} is its new value (a node set as content...). */
    static void changed(NativeModel model, String name, Object value) {
        // A Tab is not its content's CSS parent in JavaFX (the tab pane's content region is): a tab
        // being selected or restyled changes its header, not the whole form it holds.
        if (SUBTREE_KEYS.contains(name) && (!model.is(javafx.scene.control.Tab.class) || "disable".equals(name))) {
            NativeElements.invalidateSubtree(model);
        }
        Object node = REFERENCE_KEYS.contains(name) ? null : Fx.fx(value);
        if (node instanceof NativeModel) {
            NativeElements.invalidateSubtree((NativeModel) node); // under a new parent now
        }
        changed(model);
    }

    /**
     * Parameter names of JavaFX 8 constructors without @NamedArg, by class and argument count
     * ("ScrollPane(Node content)"); a list property name takes array arguments as its elements.
     */
    private static final Map<String, String[]> CONSTRUCTOR_NAMES = new HashMap<>();
    private static final java.util.Set<String> LIST_PROPERTIES = new java.util.HashSet<>(java.util.Arrays.asList(
            "tabs", "panes", "items", "children", "buttonTypes"));

    static {
        String[][] table = {
            {"ScrollPane/1", "content"}, {"TitledPane/2", "text", "content"}, {"Tab/1", "text"}, {"Tab/2", "text", "content"},
            {"TabPane/1", "tabs"}, {"Accordion/1", "panes"}, {"DatePicker/1", "value"}, {"ListView/1", "items"},
            {"TableView/1", "items"}, {"ComboBox/1", "items"}, {"ChoiceBox/1", "items"}, {"ProgressBar/1", "progress"},
            {"ProgressIndicator/1", "progress"}, {"Slider/3", "min", "max", "value"}, {"Separator/1", "orientation"},
            {"BorderPane/1", "center"}, {"BorderPane/5", "center", "top", "right", "bottom", "left"},
            {"Text/3", "x", "y", "text"}, {"ContextMenu/1", "items"}, {"Pagination/1", "pageCount"},
            {"Pagination/2", "pageCount", "currentPageIndex"}, {"ButtonBar/1", "buttonOrder"}, {"Stage/1", "style"},
            {"ImageView/1", "image"}, {"FlowPane/1", "orientation"}, {"FlowPane/2", "hgap", "vgap"},
            {"TilePane/1", "orientation"}, {"TilePane/2", "hgap", "vgap"}, {"Menu/1", "text"}, {"MenuButton/1", "text"},
            {"SplitMenuButton/1", "items"}, {"ToolBar/1", "items"},
            {"CellDataFeatures/3", "tableView", "tableColumn", "value"},
            {"TreeItem/1", "value"}, {"TreeItem/2", "value", "graphic"},
            {"FadeTransition/1", "duration"}, {"FadeTransition/2", "duration", "node"},
            {"TranslateTransition/1", "duration"}, {"TranslateTransition/2", "duration", "node"},
            {"ScaleTransition/1", "duration"}, {"ScaleTransition/2", "duration", "node"},
            {"RotateTransition/1", "duration"}, {"RotateTransition/2", "duration", "node"},
            {"ParallelTransition/1", "children"}, {"ParallelTransition/2", "node", "children"},
            {"SequentialTransition/1", "children"}, {"SequentialTransition/2", "node", "children"},
        };
        for (String[] row : table) {
            CONSTRUCTOR_NAMES.put(row[0], java.util.Arrays.copyOfRange(row, 1, row.length));
        }
    }

    /**
     * The JavaFX object a generated method of a plain superclass needs for a native model: the
     * animation behind a native FadeTransition for Animation.play() and the like.
     */
    public static Object adapter(Object peer, Class<?> type) {
        if (peer instanceof NativeModel && type.isAssignableFrom(javafx.animation.Transition.class)) {
            return NativeAnimations.of((NativeModel) peer);
        }
        throw new UnsupportedOperationException("JX native: " + peer + " as " + type.getName());
    }

    /** A native node for a JavaFX node class; named constructor arguments become property values. */
    public static NativeModel create(Class<?> fxType, String[] names, Object[] args) {
        String[] known = CONSTRUCTOR_NAMES.get(simpleName(fxType) + "/" + args.length);
        if (known != null) {
            names = names.clone();
            for (int i = 0; i < names.length; i++) {
                if (names[i] == null) {
                    names[i] = known[i];
                }
            }
        }
        NativeModel model = new NativeModel(fxType);
        if (fxType == javafx.scene.control.ChoiceDialog.class && args.length == 2) {
            // ChoiceDialog(defaultChoice, choices...) is unannotated in JavaFX 8
            property(model, "selectedItem", Object.class).setValue(Fx.fx(args[0]));
            Iterable<?> choices = args[1] instanceof Object[] ? java.util.Arrays.asList((Object[]) args[1]) : (Iterable<?>) args[1];
            if (choices != null) {
                for (Object choice : choices) {
                    list(model, "items").add(Fx.fx(choice));
                }
            }
            return model;
        }
        for (int i = 0; i < args.length; i++) {
            if (names[i] == null) {
                // JavaFX 8 leaves the common constructors unannotated: Label(String, Node), VBox(double, Node...).
                Object a = args[i];
                boolean buttons = a instanceof Object[] && javafx.scene.control.Dialog.class.isAssignableFrom(fxType);
                names[i] = buttons ? "buttonTypes" : a instanceof String ? "text" : a instanceof Object[] ? "children"
                        : a instanceof Number ? "spacing" : a instanceof Fx.Backed ? "graphic" : null;
            }
            if ("buttonTypes".equals(names[i]) && args[i] instanceof Object[]) {
                for (Object button : (Object[]) args[i]) {
                    list(model, "buttonTypes").add(Fx.fx(button));
                }
                continue;
            }
            if (names[i] == null) {
                MISSING.add("new " + fxType.getSimpleName() + "(unnamed argument " + i + ")");
            } else if (args[i] instanceof Object[] && "children".equals(names[i])) {
                for (Object child : (Object[]) args[i]) {
                    model.children.add(Fx.fx(child));
                }
            } else if (LIST_PROPERTIES.contains(names[i]) && (args[i] instanceof Object[] || args[i] instanceof java.util.Collection)) {
                Iterable<?> elements = args[i] instanceof Object[] ? java.util.Arrays.asList((Object[]) args[i]) : (Iterable<?>) args[i];
                if (args[i] instanceof javafx.collections.ObservableList || args[i] instanceof com.jxparallel.fx.collections.ObservableList) {
                    model.values.put(names[i], Fx.fx(args[i])); // ListView(items) keeps that very list, like JavaFX
                } else {
                    for (Object element : elements) {
                        list(model, names[i]).add(Fx.fx(element));
                    }
                }
            } else if ("image".equals(names[i]) && args[i] instanceof String) {
                property(model, "image", Object.class).setValue(new javafx.scene.image.Image((String) args[i]));
            } else {
                property(model, names[i], args[i] == null ? Object.class : unbox(args[i].getClass())).setValue(Fx.fx(args[i]));
            }
        }
        if (fxType == javafx.scene.Scene.class) {
            NativeRuntime.sceneCreated(model);
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

    private static final ClassValue<String> SIMPLE_NAMES = new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> type) {
            return type.getSimpleName();
        }
    };

    /** Class.getSimpleName, cached: on Java 8 it walks the class file's attributes every time. */
    static String simpleName(Class<?> type) {
        return SIMPLE_NAMES.get(type);
    }

    /** Dispatch keys already built, per declaring class and method name: {parameter types, key} pairs. */
    private static final ClassValue<Map<String, List<Object[]>>> KEYS = new ClassValue<Map<String, List<Object[]>>>() {
        @Override
        protected Map<String, List<Object[]>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    /** "Declaring.method(Param,Types)", built once per method: every generated call asks for it. */
    static String key(Class<?> declaring, String name, Class<?>[] params) {
        Map<String, List<Object[]>> byName = KEYS.get(declaring);
        List<Object[]> known = byName.get(name);
        if (known != null) {
            for (Object[] entry : known) {
                if (java.util.Arrays.equals((Class<?>[]) entry[0], params)) {
                    return (String) entry[1];
                }
            }
        }
        String key = simpleName(declaring) + "." + name + signature(params);
        byName.computeIfAbsent(name, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(new Object[]{params.clone(), key});
        return key;
    }

    /**
     * A JX node method in native mode. {@code self} is null for static methods; types are the JX
     * erasures of the declared parameters and result.
     */
    public static Object call(Object self, Class<?> declaring, String name, Class<?>[] params, Class<?> returns, Object... args) {
        String key = key(declaring, name, params);
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
                child.constraints.put(simpleName(declaring) + "." + bean(name, 3), Fx.fx(args[1]));
                changed(child);
                return null;
            }
            if (name.startsWith("get") && params.length == 1) {
                return Fx.jx(child.constraints.get(simpleName(declaring) + "." + bean(name, 3)));
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
        Object initial = unset(model, name);
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
        if (created instanceof javafx.beans.value.ObservableNumberValue || created instanceof SimpleBooleanProperty) {
            // a number or flag is never a node: no change listener, which would box old and new values
            created.addListener((javafx.beans.InvalidationListener) o -> {
                revalidate(o);
                changed(model, name, null);
            });
        } else {
            created.addListener((obs, before, after) -> changed(model, name, after));
        }
        model.values.put(name, created);
        return created;
    }

    /** Reads the value without boxing, so the property is valid again and reports its next change. */
    private static void revalidate(javafx.beans.Observable o) {
        if (o instanceof javafx.beans.value.ObservableIntegerValue) {
            ((javafx.beans.value.ObservableIntegerValue) o).get();
        } else if (o instanceof javafx.beans.value.ObservableDoubleValue) {
            ((javafx.beans.value.ObservableDoubleValue) o).get();
        } else if (o instanceof javafx.beans.value.ObservableLongValue) {
            ((javafx.beans.value.ObservableLongValue) o).get();
        } else if (o instanceof javafx.beans.value.ObservableFloatValue) {
            ((javafx.beans.value.ObservableFloatValue) o).get();
        } else if (o instanceof javafx.beans.value.ObservableBooleanValue) {
            ((javafx.beans.value.ObservableBooleanValue) o).get();
        }
    }

    @SuppressWarnings("unchecked")
    static ObservableList<Object> list(NativeModel model, String name) {
        Object existing = model.values.get(name);
        if (existing instanceof ObservableList) {
            watch(model, name, (ObservableList<Object>) existing);
            return (ObservableList<Object>) existing;
        }
        if (existing instanceof List) {
            ObservableList<Object> wrapped = FXCollections.observableList((List<Object>) existing);
            model.values.put(name, wrapped);
            watch(model, name, wrapped);
            return wrapped;
        }
        ObservableList<Object> created = FXCollections.observableArrayList();
        model.values.put(name, created);
        watch(model, name, created);
        return created;
    }

    /**
     * Re-renders when a list the node shows changes, and marks item lists as changed so cells run
     * updateItem again. A list the application handed over (setItems) is watched once per node.
     */
    private static void watch(NativeModel model, String name, ObservableList<Object> list) {
        String key = "watched:" + name;
        if (model.state.get(key) == list) {
            return;
        }
        model.state.put(key, list);
        boolean items = "items".equals(name) || "columns".equals(name);
        list.addListener((javafx.beans.InvalidationListener) o -> {
            if (model.values.get(name) != list) {
                return; // replaced by another list
            }
            if (items) {
                NativeCells.itemsChanged(model);
            }
            changed(model, name, null);
        });
        if (items) {
            NativeCells.itemsChanged(model);
        }
    }

    /** A bean value for rendering (JavaFX-side), or the JavaFX default when never set. */
    public static Object value(NativeModel model, String name) {
        Object v = model.values.get(name);
        if (v instanceof ObservableValue) {
            return ((ObservableValue<?>) v).getValue();
        }
        if (v == null && "editable".equals(name)
                && (model.is(javafx.scene.control.ComboBox.class) || model.is(javafx.scene.control.Spinner.class))) {
            return false; // unlike text inputs and DatePicker, combo boxes and spinners start read-only
        }
        return v != null ? v : DEFAULTS.get(name);
    }

    /**
     * The value a getter reports for a property the application never set: JavaFX's own default for
     * that class (a Slider's max is 100, a TitledPane is expanded, a no-argument ProgressBar is
     * indeterminate), read once from a prototype of the JavaFX class; else the name-based default.
     * Only simple values (numbers, booleans, strings) come from the prototype: object defaults such
     * as padding or alignment are set by Modena's CSS in JavaFX, which the prototype has not seen.
     */
    static Object unset(NativeModel model, String name) {
        Object prototype = javafxDefault(model.fxType, name);
        return prototype != null ? prototype : value(model, name);
    }

    private static final Map<Class<?>, Object> PROTOTYPES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Object NO_PROTOTYPE = new Object();

    private static final Map<Class<?>, Map<String, Object>> DEFAULT_VALUES = new ConcurrentHashMap<>();

    /** JavaFX's default of a simple property of a JavaFX class (or null), looked up once per class and name. */
    static Object javafxDefault(Class<?> fxType, String name) {
        Map<String, Object> known = DEFAULT_VALUES.computeIfAbsent(fxType, t -> new ConcurrentHashMap<>());
        Object cached = known.get(name);
        if (cached != null) {
            return cached == NO_PROTOTYPE ? null : cached;
        }
        Object found = lookupDefault(fxType, name);
        if (found != null || PROTOTYPES.containsKey(fxType)) {
            known.put(name, found == null ? NO_PROTOTYPE : found); // not cached while the prototype may still come
        }
        return found;
    }

    private static Object lookupDefault(Class<?> fxType, String name) {
        if (!fxType.getName().startsWith("javafx.") || name.isEmpty()) {
            return null;
        }
        Object prototype = PROTOTYPES.get(fxType);
        if (prototype == null) {
            try {
                prototype = fxType.getConstructor().newInstance();
            } catch (IllegalStateException e) {
                return null; // JavaFX 8 builds some classes (Stage) only on its thread: try again from there
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                prototype = NO_PROTOTYPE; // abstract, no public no-argument constructor, or needs arguments
            }
            PROTOTYPES.put(fxType, prototype);
        }
        if (prototype == NO_PROTOTYPE) {
            return null;
        }
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String getter : new String[]{"get" + cap, "is" + cap}) {
            try {
                Object v = fxType.getMethod(getter).invoke(prototype);
                return v instanceof Number || v instanceof Boolean || v instanceof String || v instanceof Character ? v : null;
            } catch (NoSuchMethodException e) {
                // try the next accessor
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    public static Object constraint(NativeModel model, String key) {
        return model.constraints.get(key);
    }

    private static Object read(NativeModel model, String name, Class<?> returns) {
        Object v = model.values.containsKey(name) ? value(model, name) : unset(model, name);
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
        String n = simpleName(propertyType);
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
            s.append(i == 0 ? "" : ",").append(simpleName(params[i]));
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
