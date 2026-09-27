package com.jxparallel.fx.fxml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;

/**
 * An FXML file parsed once into a build plan: classes, constructors, setters and controller members
 * are resolved and attribute values converted when the template is created, so each
 * {@link #instantiate} only calls constructors and setters.
 *
 * <p>It does not depend on a UI toolkit: annotations are recognized by simple name ({@code @FXML},
 * {@code @NamedArg}, {@code @DefaultProperty}), an event handler is any single-method interface a
 * setter takes, and a JavaFX class named in the FXML resolves to its com.jxparallel.fx counterpart
 * when one exists. So unchanged FXML, including Scene Builder output, builds JX objects.
 *
 * <p>Supported: {@code <?import?>}, object elements, attributes, static properties, property
 * elements, default properties, {@code @NamedArg} constructors, list properties (also as
 * comma-separated attributes), {@code @location} values, {@code <URL value>}, {@code fx:id},
 * {@code fx:controller}, {@code fx:include}, {@code fx:constant}, {@code #handler} methods and
 * {@code initialize}. Anything else raises {@link Unsupported}.
 */
public final class FxmlTemplate {
    private static final String FXML_NAMESPACE_PREFIX = "http://javafx.com/fxml";
    private static final String[][] COUNTERPARTS = {
        {"com.sun.javafx.", "com.jxparallel.fx.sun."},
        {"org.controlsfx.", "com.jxparallel.fx.controlsfx."},
        {"javafx.", "com.jxparallel.fx."},
    };

    private final ObjectPlan root;
    private final Class<?> controllerType;
    private final List<Injection> injections;
    private final Method initializeWithArgs;
    private final Method initialize;
    private final URL location;
    private final Field locationField;
    private final Field resourcesField;

    private FxmlTemplate(ObjectPlan root, Parser parser, URL location) {
        this.root = root;
        this.controllerType = parser.controllerType;
        this.injections = parser.injections;
        this.initializeWithArgs = controllerType == null ? null : findInitializeWithArgs(controllerType);
        this.initialize = controllerType == null || initializeWithArgs != null ? null : findInitialize(controllerType);
        this.location = location;
        this.locationField = controllerType == null ? null : findField(controllerType, "location", URL.class);
        this.resourcesField = controllerType == null ? null : findField(controllerType, "resources", ResourceBundle.class);
    }

    /** Thrown when the FXML uses a feature the template does not implement. */
    public static final class Unsupported extends Exception {
        Unsupported(String message) {
            super(message);
        }
    }

    /** A built object graph and its controller. */
    public static final class Result {
        public final Object root;
        public final Object controller;

        Result(Object root, Object controller) {
            this.root = root;
            this.controller = controller;
        }
    }

    private static final Map<String, FxmlTemplate> CACHE = new ConcurrentHashMap<>();

    /** The template of an FXML file, parsed once per location and class loader. */
    public static FxmlTemplate of(URL location, ClassLoader loader) throws IOException, Unsupported {
        String key = location.toExternalForm() + "|" + System.identityHashCode(loader);
        FxmlTemplate cached = CACHE.get(key);
        if (cached == null) {
            byte[] source;
            try (InputStream in = location.openStream()) {
                source = readAll(in);
            }
            cached = parse(source, location, loader);
            CACHE.put(key, cached);
        }
        return cached;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    public static FxmlTemplate parse(byte[] source, URL location, ClassLoader loader) throws Unsupported {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(new ByteArrayInputStream(source));
        } catch (Exception e) {
            throw new Unsupported("Not parseable as XML: " + e.getMessage());
        }
        Parser parser = new Parser(loader, location);
        for (Node node = document.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof ProcessingInstruction) {
                ProcessingInstruction pi = (ProcessingInstruction) node;
                if (!"import".equals(pi.getTarget())) {
                    throw new Unsupported("Processing instruction <?" + pi.getTarget() + "?>");
                }
                parser.imports.add(pi.getData().trim());
            }
        }
        Element rootElement = document.getDocumentElement();
        String controllerName = rootElement.getAttributeNS(fxNamespace(rootElement), "controller");
        if (controllerName != null && !controllerName.isEmpty()) {
            parser.controllerType = parser.load(controllerName);
        }
        return new FxmlTemplate(parser.object(rootElement), parser, location);
    }

    /** Builds a new object graph with a new controller. */
    @SuppressWarnings("unchecked")
    public <T> T instantiate() {
        return (T) build(null, null).root;
    }

    /**
     * Builds a new object graph. {@code controller} replaces the one fx:controller would create;
     * {@code factory} creates controllers when given; both may be null.
     */
    public Result build(Object controller, ControllerFactory factory) {
        try {
            if (controller == null && controllerType != null) {
                controller = factory != null ? factory.create(controllerType) : newController(controllerType);
            }
            Context context = new Context(controller, factory);
            Object result = root.build(context);
            if (controller != null) {
                for (Injection injection : injections) {
                    Object value = context.built.get(injection.plan);
                    if (value != null) {
                        injection.field.set(controller, injection.controllerOfInclude ? context.includeControllers.get(injection.plan) : value);
                    }
                }
                if (locationField != null) {
                    locationField.set(controller, location);
                }
                if (initializeWithArgs != null) {
                    initializeWithArgs.invoke(controller, location, null);
                } else if (initialize != null) {
                    initialize.invoke(controller);
                }
            }
            return new Result(result, controller);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("FXML construction failed: " + location, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FXML construction failed: " + location, e);
        }
    }

    /** Creates a controller of the given type (like FXMLLoader's controller factory). */
    public interface ControllerFactory {
        Object create(Class<?> type) throws ReflectiveOperationException;
    }

    private static Object newController(Class<?> type) throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /** initialize(URL, ResourceBundle) from an interface named Initializable (JavaFX's or JX's). */
    private static Method findInitializeWithArgs(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                if (i.getSimpleName().equals("Initializable")) {
                    try {
                        return type.getMethod("initialize", URL.class, ResourceBundle.class);
                    } catch (NoSuchMethodException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static Method findInitialize(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod("initialize");
                if (Modifier.isPublic(method.getModifiers()) || annotated(method, "FXML")) {
                    method.setAccessible(true);
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // keep looking in superclasses
            }
        }
        return null;
    }

    private static boolean annotated(java.lang.reflect.AnnotatedElement element, String simpleName) {
        return annotation(element, simpleName) != null;
    }

    private static Annotation annotation(java.lang.reflect.AnnotatedElement element, String simpleName) {
        for (Annotation a : element.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals(simpleName)) {
                return a;
            }
        }
        return null;
    }

    private static String annotationValue(Annotation a) {
        try {
            return (String) a.annotationType().getMethod("value").invoke(a);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name, Class<?> fieldType) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                if (fieldType != null && !fieldType.equals(field.getType())) {
                    return null;
                }
                // Like FXMLLoader: only @FXML or public fields are injected.
                if (!annotated(field, "FXML") && !Modifier.isPublic(field.getModifiers())) {
                    return null;
                }
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // keep looking in superclasses
            }
        }
        return null;
    }

    private static String fxNamespace(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            String value = attributes.item(i).getNodeValue();
            if ("xmlns:fx".equals(attributes.item(i).getNodeName()) && value.startsWith(FXML_NAMESPACE_PREFIX)) {
                return value;
            }
        }
        return FXML_NAMESPACE_PREFIX;
    }

    // ------------------------------------------------------------------ plan

    private static final class Injection {
        final Field field;
        final ObjectPlan plan;
        final boolean controllerOfInclude;

        Injection(Field field, ObjectPlan plan, boolean controllerOfInclude) {
            this.field = field;
            this.plan = plan;
            this.controllerOfInclude = controllerOfInclude;
        }
    }

    /** State of one build: the controller and what fx:id elements produced. */
    private static final class Context {
        final Object controller;
        final ControllerFactory factory;
        final Map<ObjectPlan, Object> built = new HashMap<>();
        final Map<ObjectPlan, Object> includeControllers = new HashMap<>();

        Context(Object controller, ControllerFactory factory) {
            this.controller = controller;
            this.factory = factory;
        }
    }

    private interface Step {
        void apply(Object target, Context context) throws ReflectiveOperationException;
    }

    private interface Factory {
        Object create(Context context) throws ReflectiveOperationException;
    }

    private static final class ObjectPlan {
        final Factory factory;
        final List<Step> steps = new ArrayList<>();
        boolean remember;

        ObjectPlan(Factory factory) {
            this.factory = factory;
        }

        Object build(Context context) throws ReflectiveOperationException {
            Object target = factory.create(context);
            for (Step step : steps) {
                step.apply(target, context);
            }
            if (remember) {
                context.built.put(this, target);
            }
            return target;
        }
    }

    // ---------------------------------------------------------------- parser

    private static final class Parser {
        final ClassLoader loader;
        final URL location;
        final List<String> imports = new ArrayList<>();
        final Map<String, Class<?>> classes = new HashMap<>();
        final List<Injection> injections = new ArrayList<>();
        Class<?> controllerType;

        Parser(ClassLoader loader, URL location) {
            this.loader = loader;
            this.location = location;
        }

        Class<?> load(String name) throws Unsupported {
            Class<?> type = tryLoad(name);
            if (type == null) {
                throw new Unsupported("Class not found: " + name);
            }
            return type;
        }

        /** The com.jxparallel.fx counterpart of a JavaFX class when there is one, else the class itself. */
        Class<?> tryLoad(String binaryName) {
            for (String[] prefix : COUNTERPARTS) {
                if (binaryName.startsWith(prefix[0])) {
                    try {
                        return Class.forName(prefix[1] + binaryName.substring(prefix[0].length()), false, loader);
                    } catch (ClassNotFoundException | LinkageError notMirrored) {
                        break;
                    }
                }
            }
            try {
                return Class.forName(binaryName, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
        }

        Class<?> tryLoad(String packageName, String className) {
            return tryLoad(packageName + "." + className.replace('.', '$'));
        }

        /** FXMLLoader's split: the package ends before the first segment that starts upper case. */
        static int packageEnd(String name) {
            int i = name.indexOf('.');
            while (i != -1 && i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1))) {
                i = name.indexOf('.', i + 1);
            }
            return i;
        }

        /** Same rules as FXMLLoader.getType, so FXML that loads here also loads with FXMLLoader. */
        Class<?> resolve(String name) throws Unsupported {
            Class<?> known = classes.get(name);
            if (known != null) {
                return known;
            }
            Class<?> type = null;
            if (Character.isLowerCase(name.charAt(0))) {
                int end = packageEnd(name);
                type = end < 1 ? null : tryLoad(name.substring(0, end), name.substring(end + 1));
            } else {
                for (String entry : imports) {
                    if (entry.endsWith(".*")) {
                        type = tryLoad(entry.substring(0, entry.length() - 2), name);
                    } else {
                        int end = packageEnd(entry);
                        if (end > 0 && entry.substring(end + 1).equals(name)) {
                            type = tryLoad(entry.substring(0, end), name);
                        }
                    }
                    if (type != null) {
                        break;
                    }
                }
            }
            if (type == null) {
                throw new Unsupported("Unresolved element <" + name + ">");
            }
            classes.put(name, type);
            return type;
        }

        /** "@path" relative to the FXML file, like FXMLLoader's location resolution. */
        String location(String value) throws Unsupported {
            if (!value.startsWith("@")) {
                return value;
            }
            String path = value.substring(1).trim();
            try {
                if (path.startsWith("/")) {
                    URL resource = loader.getResource(path.substring(1));
                    if (resource == null) {
                        throw new Unsupported("Resource not found: " + value);
                    }
                    return resource.toExternalForm();
                }
                return new URL(location, path).toExternalForm();
            } catch (MalformedURLException e) {
                throw new Unsupported("Bad location " + value);
            }
        }

        ObjectPlan object(Element element) throws Unsupported {
            if (isFx(element)) {
                if ("include".equals(element.getLocalName())) {
                    return include(element);
                }
                throw new Unsupported("<fx:" + element.getLocalName() + ">");
            }
            Class<?> type = resolve(element.getLocalName());
            Map<String, String> plain = new LinkedHashMap<>();
            List<String[]> statics = new ArrayList<>();
            String fxId = null;
            String constant = null;
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attribute = (Attr) attributes.item(i);
                String name = attribute.getName();
                String value = attribute.getValue();
                if (name.startsWith("xmlns")) {
                    continue;
                }
                if (isFx(attribute)) {
                    String fxName = attribute.getLocalName();
                    if ("id".equals(fxName)) {
                        fxId = value;
                    } else if ("constant".equals(fxName)) {
                        constant = value;
                    } else if (!"controller".equals(fxName)) {
                        throw new Unsupported("fx:" + fxName);
                    }
                    continue;
                }
                if (value.startsWith("$") || value.startsWith("%")) {
                    throw new Unsupported("Expression or resource value: " + name + "=\"" + value + "\"");
                }
                if (name.indexOf('.') > 0) {
                    statics.add(new String[]{name, value});
                } else {
                    plain.put(name, value);
                }
            }
            if (constant != null) {
                return constant(type, constant);
            }
            if (type == URL.class) {
                return url(plain.get("value"));
            }

            ObjectPlan plan = chooseConstructor(type, plain);
            for (Map.Entry<String, String> entry : plain.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if (name.startsWith("on") && value.startsWith("#")) {
                    plan.steps.add(handler(type, name, value.substring(1)));
                } else {
                    plan.steps.add(property(type, name, value));
                }
            }
            for (String[] entry : statics) {
                plan.steps.add(staticProperty(entry[0], entry[1]));
            }
            if (fxId != null && !plain.containsKey("id") && hasIdProperty(type)) {
                // Like FXMLLoader: fx:id also sets the node id, so lookup("#name") works.
                plan.steps.add(0, property(type, "id", fxId));
            }
            children(element, type, plan);
            if (fxId != null && controllerType != null) {
                Field field = findField(controllerType, fxId, null);
                if (field != null) {
                    plan.remember = true;
                    injections.add(new Injection(field, plan, false));
                }
            }
            return plan;
        }

        /** FXMLLoader sets fx:id into the property named by @IDProperty; JavaFX puts it on nodes, tabs, menu items and columns. */
        static boolean hasIdProperty(Class<?> type) {
            try {
                type.getMethod("setId", String.class);
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        ObjectPlan constant(Class<?> type, String name) throws Unsupported {
            try {
                Field field = type.getField(name);
                Object value = field.get(null);
                return new ObjectPlan(context -> value);
            } catch (ReflectiveOperationException e) {
                throw new Unsupported("No constant " + type.getSimpleName() + "." + name);
            }
        }

        ObjectPlan url(String value) throws Unsupported {
            if (value == null) {
                throw new Unsupported("<URL> without value");
            }
            try {
                URL url = new URL(location(value));
                return new ObjectPlan(context -> url);
            } catch (MalformedURLException e) {
                throw new Unsupported("Bad URL " + value);
            }
        }

        ObjectPlan include(Element element) throws Unsupported {
            String source = element.getAttribute("source");
            URL included;
            try {
                included = source.startsWith("/") ? loader.getResource(source.substring(1)) : new URL(location, source);
            } catch (MalformedURLException e) {
                throw new Unsupported("Bad fx:include source " + source);
            }
            if (included == null) {
                throw new Unsupported("fx:include not found: " + source);
            }
            FxmlTemplate template;
            try {
                template = FxmlTemplate.of(included, loader);
            } catch (IOException e) {
                throw new Unsupported("fx:include unreadable: " + source);
            }
            ObjectPlan[] self = new ObjectPlan[1];
            ObjectPlan plan = new ObjectPlan(context -> {
                Result result = template.build(null, context.factory);
                context.includeControllers.put(self[0], result.controller);
                return result.root;
            });
            self[0] = plan;
            String fxId = fxAttribute(element, "id");
            if (fxId != null && !fxId.isEmpty()) {
                plan.steps.add((target, context) -> {
                    if (hasIdProperty(target.getClass())) {
                        target.getClass().getMethod("setId", String.class).invoke(target, fxId);
                    }
                });
                if (controllerType != null) {
                    Field field = findField(controllerType, fxId, null);
                    Field controllerField = findField(controllerType, fxId + "Controller", null);
                    if (field != null) {
                        injections.add(new Injection(field, plan, false));
                    }
                    if (controllerField != null) {
                        injections.add(new Injection(controllerField, plan, true));
                    }
                    plan.remember = field != null || controllerField != null;
                }
            }
            return plan;
        }

        /** Picks the no-arg constructor, or the @NamedArg constructor that best matches the attributes. */
        ObjectPlan chooseConstructor(Class<?> type, Map<String, String> attributes) throws Unsupported {
            Constructor<?> best = null;
            Object[] bestArgs = null;
            List<String> bestNames = null;
            int bestScore = Integer.MAX_VALUE;
            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 0) {
                    if (best == null) {
                        best = constructor;
                        bestArgs = new Object[0];
                        bestNames = new ArrayList<>();
                        bestScore = 1000;
                    }
                    continue;
                }
                String[] names = new String[params.length];
                String[] defaults = new String[params.length];
                if (!namedArgs(constructor, names, defaults)) {
                    continue;
                }
                Object[] args = new Object[params.length];
                int matched = 0;
                int score = 0;
                boolean usable = true;
                for (int i = 0; i < params.length; i++) {
                    String raw = attributes.get(names[i]);
                    Object converted;
                    if (raw == null && defaults[i] == null) {
                        // Like FXMLLoader: a missing @NamedArg without default is the type's default value.
                        converted = zero(params[i]);
                    } else {
                        if (raw == null) {
                            raw = defaults[i];
                        } else {
                            matched++;
                            raw = location(raw);
                        }
                        converted = coerce(raw, params[i]);
                    }
                    if (converted == NOT_CONVERTIBLE) {
                        usable = false;
                        break;
                    }
                    args[i] = converted;
                    score += preference(params[i]);
                }
                if (!usable) {
                    continue;
                }
                // More matched attributes first, then the narrowest parameter types (int before double);
                // all defaults (<Insets/>) only when there is no no-arg constructor.
                int total = matched == 0 ? 2000 + score : score - matched * 100;
                if (total < bestScore) {
                    best = constructor;
                    bestArgs = args;
                    bestNames = new ArrayList<>();
                    for (int i = 0; i < names.length; i++) {
                        if (attributes.containsKey(names[i])) {
                            bestNames.add(names[i]);
                        }
                    }
                    bestScore = total;
                }
            }
            if (best == null) {
                throw new Unsupported("No usable constructor for " + type.getName());
            }
            for (String name : bestNames) {
                attributes.remove(name);
            }
            Constructor<?> chosen = best;
            Object[] chosenArgs = bestArgs;
            return new ObjectPlan(context -> chosen.newInstance(chosenArgs));
        }

        /** Fills names and default values from @NamedArg; false when a parameter has none. */
        boolean namedArgs(Constructor<?> constructor, String[] names, String[] defaults) {
            Annotation[][] annotations = constructor.getParameterAnnotations();
            for (int i = 0; i < annotations.length; i++) {
                for (Annotation annotation : annotations[i]) {
                    if (annotation.annotationType().getSimpleName().equals("NamedArg")) {
                        names[i] = annotationValue(annotation);
                        try {
                            String d = (String) annotation.annotationType().getMethod("defaultValue").invoke(annotation);
                            defaults[i] = d == null || d.isEmpty() ? null : d;
                        } catch (ReflectiveOperationException ignored) {
                            defaults[i] = null;
                        }
                    }
                }
                if (names[i] == null) {
                    return false;
                }
            }
            return true;
        }

        Step property(Class<?> type, String name, String raw) throws Unsupported {
            String value0 = location(raw);
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (Method method : type.getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterTypes().length == 1
                        && !Modifier.isStatic(method.getModifiers())) {
                    Object value = coerce(value0, method.getParameterTypes()[0]);
                    if (value != NOT_CONVERTIBLE) {
                        Method setter = method;
                        return (target, context) -> setter.invoke(target, value);
                    }
                }
            }
            // A read-only list property given as an attribute: comma-separated values, like FXMLLoader.
            Method getter = getter(type, name);
            if (getter != null && Collection.class.isAssignableFrom(getter.getReturnType())) {
                List<Object> values = new ArrayList<>();
                for (String part : raw.split(",")) {
                    if (!part.trim().isEmpty()) {
                        values.add(location(part.trim()));
                    }
                }
                return (target, context) -> addAll(getter, target, values);
            }
            throw new Unsupported("No setter " + type.getSimpleName() + "." + setterName + " for \"" + raw + "\"");
        }

        Method getter(Class<?> type, String name) {
            try {
                return type.getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        Step staticProperty(String qualified, String raw) throws Unsupported {
            int dot = qualified.lastIndexOf('.');
            Class<?> owner = resolve(qualified.substring(0, dot));
            String name = qualified.substring(dot + 1);
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterTypes().length == 2
                        && Modifier.isStatic(method.getModifiers())) {
                    Object value = coerce(location(raw), method.getParameterTypes()[1]);
                    if (value != NOT_CONVERTIBLE) {
                        Method setter = method;
                        return (target, context) -> setter.invoke(null, target, value);
                    }
                }
            }
            throw new Unsupported("No static setter " + qualified);
        }

        /** onX="#method": a proxy of the setter's single-method listener type calling the controller method. */
        Step handler(Class<?> type, String property, String methodName) throws Unsupported {
            if (controllerType == null) {
                throw new Unsupported("Event handler without fx:controller: #" + methodName);
            }
            String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            Method setter = null;
            for (Method method : type.getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].isInterface()) {
                    setter = method;
                }
            }
            if (setter == null) {
                throw new Unsupported("No event property " + type.getSimpleName() + "." + property);
            }
            Method target = null;
            for (Class<?> c = controllerType; c != null && c != Object.class && target == null; c = c.getSuperclass()) {
                for (Method method : c.getDeclaredMethods()) {
                    if (method.getName().equals(methodName) && method.getParameterTypes().length <= 1
                            && (target == null || method.getParameterTypes().length == 1)) {
                        target = method;
                    }
                }
            }
            if (target == null) {
                throw new Unsupported("Controller has no method " + methodName);
            }
            target.setAccessible(true);
            Method eventSetter = setter;
            Method eventMethod = target;
            Class<?> listenerType = setter.getParameterTypes()[0];
            return (node, context) -> {
                Object controller = context.controller;
                Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "equals":
                                    return proxy == args[0];
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "toString":
                                    return "#" + methodName;
                                default:
                                    try {
                                        return eventMethod.getParameterTypes().length == 1
                                                ? eventMethod.invoke(controller, args[0])
                                                : eventMethod.invoke(controller);
                                    } catch (InvocationTargetException e) {
                                        throw e.getCause();
                                    }
                            }
                        });
                eventSetter.invoke(node, listener);
            };
        }

        void children(Element element, Class<?> type, ObjectPlan plan) throws Unsupported {
            List<ObjectPlan> defaults = new ArrayList<>();
            for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                    if (!node.getNodeValue().trim().isEmpty()) {
                        throw new Unsupported("Text content inside <" + element.getLocalName() + ">");
                    }
                    continue;
                }
                if (node.getNodeType() == Node.COMMENT_NODE) {
                    continue;
                }
                if (!(node instanceof Element)) {
                    throw new Unsupported("Unexpected node inside <" + element.getLocalName() + ">");
                }
                Element child = (Element) node;
                String name = child.getLocalName();
                if (isStaticProperty(name) && !isFx(child)) {
                    List<ObjectPlan> values = values(child);
                    if (values.size() != 1) {
                        throw new Unsupported("Static property element <" + name + "> needs one value");
                    }
                    plan.steps.add(staticElement(name, values.get(0)));
                } else if (isFx(child) || Character.isUpperCase(name.charAt(0))) {
                    defaults.add(object(child));
                } else {
                    if (child.getAttributes().getLength() > 0) {
                        throw new Unsupported("Attributes on property element <" + name + ">");
                    }
                    plan.steps.add(assign(type, name, values(child)));
                }
            }
            if (!defaults.isEmpty()) {
                Annotation annotation = annotation(type, "DefaultProperty");
                if (annotation == null) {
                    throw new Unsupported(type.getSimpleName() + " has no @DefaultProperty");
                }
                plan.steps.add(assign(type, annotationValue(annotation), defaults));
            }
        }

        /** GridPane.margin, VBox.vgrow...: a type name, then a lower-case property. */
        static boolean isStaticProperty(String name) {
            int dot = name.lastIndexOf('.');
            return dot > 0 && Character.isUpperCase(name.charAt(0)) && Character.isLowerCase(name.charAt(dot + 1));
        }

        List<ObjectPlan> values(Element property) throws Unsupported {
            List<ObjectPlan> values = new ArrayList<>();
            for (Node inner = property.getFirstChild(); inner != null; inner = inner.getNextSibling()) {
                if (inner instanceof Element) {
                    values.add(object((Element) inner));
                } else if (inner.getNodeType() == Node.TEXT_NODE && !inner.getNodeValue().trim().isEmpty()) {
                    throw new Unsupported("Text value in property element <" + property.getLocalName() + ">");
                }
            }
            return values;
        }

        Step staticElement(String qualified, ObjectPlan value) throws Unsupported {
            int dot = qualified.lastIndexOf('.');
            Class<?> owner = resolve(qualified.substring(0, dot));
            String name = qualified.substring(dot + 1);
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterTypes().length == 2
                        && Modifier.isStatic(method.getModifiers())) {
                    Method setter = method;
                    return (target, context) -> setter.invoke(null, target, value.build(context));
                }
            }
            throw new Unsupported("No static setter " + qualified);
        }

        /** Adds to a list property (getX()) or sets a single-valued property (setX()). */
        Step assign(Class<?> type, String property, List<ObjectPlan> values) throws Unsupported {
            Method getter = getter(type, property);
            if (getter != null && Collection.class.isAssignableFrom(getter.getReturnType())) {
                return (target, context) -> {
                    List<Object> built = new ArrayList<>();
                    for (ObjectPlan value : values) {
                        built.add(value.build(context));
                    }
                    addAll(getter, target, built);
                };
            }
            if (values.size() != 1) {
                throw new Unsupported(type.getSimpleName() + "." + property + " is not a list but has " + values.size() + " values");
            }
            String capital = Character.toUpperCase(property.charAt(0)) + property.substring(1);
            for (Method method : type.getMethods()) {
                if (method.getName().equals("set" + capital) && method.getParameterTypes().length == 1) {
                    Method setter = method;
                    ObjectPlan value = values.get(0);
                    return (target, context) -> setter.invoke(target, value.build(context));
                }
            }
            throw new Unsupported("No property " + type.getSimpleName() + "." + property);
        }

        /** An fx: attribute, wherever its namespace is declared. */
        static String fxAttribute(Element element, String name) {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                if (isFx(attributes.item(i)) && name.equals(attributes.item(i).getLocalName())) {
                    return attributes.item(i).getNodeValue();
                }
            }
            return null;
        }

        static boolean isFx(Node node) {
            String namespace = node.getNamespaceURI();
            return namespace != null && namespace.startsWith(FXML_NAMESPACE_PREFIX);
        }
    }

    /** Adds values to a list property; a list of strings (stylesheets) gets toString() of URLs. */
    @SuppressWarnings("unchecked")
    private static void addAll(Method getter, Object target, List<Object> values) throws ReflectiveOperationException {
        Collection<Object> list = (Collection<Object>) getter.invoke(target);
        boolean strings = elementType(getter) == String.class;
        for (Object value : values) {
            list.add(strings && value != null && !(value instanceof String) ? value.toString() : value);
        }
    }

    private static Type elementType(Method getter) {
        Type t = getter.getGenericReturnType();
        if (t instanceof ParameterizedType && ((ParameterizedType) t).getActualTypeArguments().length == 1) {
            return ((ParameterizedType) t).getActualTypeArguments()[0];
        }
        return Object.class;
    }

    // ------------------------------------------------------------ conversion

    private static final Object NOT_CONVERTIBLE = new Object();

    private static Object zero(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return coerce("0", type);
    }

    private static int preference(Class<?> type) {
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 1;
        if (type == float.class || type == Float.class) return 2;
        if (type == double.class || type == Double.class) return 3;
        return 5;
    }

    private static Object coerce(String raw, Class<?> type) {
        try {
            if (type == String.class || type == Object.class || type == CharSequence.class) return raw;
            if (type == int.class || type == Integer.class) return Integer.valueOf(raw.trim());
            if (type == double.class || type == Double.class) return Double.valueOf(raw.trim());
            if (type == boolean.class || type == Boolean.class) {
                String value = raw.trim();
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return Boolean.valueOf(value);
                return NOT_CONVERTIBLE;
            }
            if (type == long.class || type == Long.class) return Long.valueOf(raw.trim());
            if (type == float.class || type == Float.class) return Float.valueOf(raw.trim());
            if (type.isEnum()) {
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(raw.trim())) return constant;
                }
                return NOT_CONVERTIBLE;
            }
            Method valueOf = type.getMethod("valueOf", String.class);
            if (Modifier.isStatic(valueOf.getModifiers()) && type.isAssignableFrom(valueOf.getReturnType())) {
                return valueOf.invoke(null, raw);
            }
        } catch (NumberFormatException e) {
            return NOT_CONVERTIBLE;
        } catch (InvocationTargetException e) {
            return NOT_CONVERTIBLE;
        } catch (ReflectiveOperationException e) {
            return NOT_CONVERTIBLE;
        }
        return NOT_CONVERTIBLE;
    }
}
