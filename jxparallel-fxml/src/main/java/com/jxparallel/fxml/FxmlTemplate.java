package com.jxparallel.fxml;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;

/**
 * An FXML file parsed once into an immutable build plan: classes, constructors, setters and
 * controller members are resolved and attribute values converted when the template is created, so
 * each {@link #instantiate()} only calls constructors and setters. Every instance gets new objects
 * and a new controller; nothing mutable is shared between instances.
 *
 * <p>Supported: {@code <?import?>}, object elements, attributes as properties, static properties
 * ({@code GridPane.rowIndex}), property elements, default properties, {@code @NamedArg}
 * constructors, {@code fx:id}, {@code fx:controller} with field injection, {@code #handler} event
 * methods and {@code initialize()}. Anything else ({@code fx:include}, {@code fx:define},
 * expressions, {@code %resources}, {@code @locations}, scripts, text content) raises
 * {@link Unsupported}, and callers fall back to {@code FXMLLoader}.
 */
public final class FxmlTemplate {
    private static final String FXML_NAMESPACE_PREFIX = "http://javafx.com/fxml";

    private final ObjectPlan root;
    private final Class<?> controllerType;
    private final List<Injection> injections;
    private final Method initialize;
    private final URL location;
    private final Field locationField;

    private FxmlTemplate(ObjectPlan root, Class<?> controllerType, List<Injection> injections,
                         Method initialize, URL location, Field locationField) {
        this.root = root;
        this.controllerType = controllerType;
        this.injections = injections;
        this.initialize = initialize;
        this.location = location;
        this.locationField = locationField;
    }

    /** Thrown when the FXML uses a feature the template does not implement. */
    public static final class Unsupported extends Exception {
        Unsupported(String message) {
            super(message);
        }
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
        Parser parser = new Parser(loader);
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
        ObjectPlan rootPlan = parser.object(rootElement);
        Method initialize = parser.controllerType == null ? null : findInitialize(parser.controllerType);
        Field locationField = parser.controllerType == null ? null
                : findField(parser.controllerType, "location", URL.class);
        return new FxmlTemplate(rootPlan, parser.controllerType, parser.injections, initialize, location, locationField);
    }

    /** Builds a new object graph with a new controller. */
    @SuppressWarnings("unchecked")
    public <T> T instantiate() {
        try {
            Object controller = controllerType == null ? null : newController(controllerType);
            Object result = root.build(controller);
            if (controller != null) {
                for (Injection injection : injections) {
                    injection.field.set(controller, injection.plan.lastBuilt.get());
                }
                if (locationField != null) {
                    locationField.set(controller, location);
                }
                if (controller instanceof Initializable) {
                    ((Initializable) controller).initialize(location, (ResourceBundle) null);
                } else if (initialize != null) {
                    initialize.invoke(controller);
                }
            }
            return (T) result;
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("FXML construction failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FXML construction failed", e);
        } finally {
            for (Injection injection : injections) {
                injection.plan.lastBuilt.remove();
            }
        }
    }

    private static Object newController(Class<?> type) throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Method findInitialize(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod("initialize");
                if (Modifier.isPublic(method.getModifiers()) || method.isAnnotationPresent(FXML.class)) {
                    method.setAccessible(true);
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // keep looking in superclasses
            }
        }
        return null;
    }

    /**
     * The property named by JavaFX's internal {@code com.sun.javafx.beans.IDProperty} (inherited,
     * {@code "id"} on Node), or null. FXMLLoader copies fx:id only into that property.
     */
    private static String idProperty(Class<?> type) {
        for (Annotation annotation : type.getAnnotations()) {
            if ("com.sun.javafx.beans.IDProperty".equals(annotation.annotationType().getName())) {
                try {
                    return (String) annotation.annotationType().getMethod("value").invoke(annotation);
                } catch (ReflectiveOperationException | RuntimeException notExported) {
                    return "id"; // module path without access to com.sun.javafx.beans; Node's value
                }
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name, Class<?> fieldType) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                if (fieldType != null && !fieldType.equals(field.getType())) {
                    return null;
                }
                // Like FXMLLoader: only @FXML or public fields are injected.
                if (!field.isAnnotationPresent(FXML.class) && !Modifier.isPublic(field.getModifiers())) {
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

        Injection(Field field, ObjectPlan plan) {
            this.field = field;
            this.plan = plan;
        }
    }

    private interface Step {
        void apply(Object target, Object controller) throws ReflectiveOperationException;
    }

    private static final class ObjectPlan {
        final Constructor<?> constructor;
        final Object[] constructorArgs;
        final List<Step> steps = new ArrayList<Step>();
        /** Set during a build when the node has an fx:id, read back for controller injection. */
        final ThreadLocal<Object> lastBuilt = new ThreadLocal<Object>();
        boolean remember;

        ObjectPlan(Constructor<?> constructor, Object[] constructorArgs) {
            this.constructor = constructor;
            this.constructorArgs = constructorArgs;
        }

        Object build(Object controller) throws ReflectiveOperationException {
            Object target = constructor.newInstance(constructorArgs);
            for (Step step : steps) {
                step.apply(target, controller);
            }
            if (remember) {
                lastBuilt.set(target);
            }
            return target;
        }
    }

    // ---------------------------------------------------------------- parser

    private static final class Parser {
        final ClassLoader loader;
        final List<String> imports = new ArrayList<String>();
        final Map<String, Class<?>> classes = new HashMap<String, Class<?>>();
        final List<Injection> injections = new ArrayList<Injection>();
        Class<?> controllerType;

        Parser(ClassLoader loader) {
            this.loader = loader;
        }

        Class<?> load(String name) throws Unsupported {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException e) {
                throw new Unsupported("Class not found: " + name);
            }
        }

        Class<?> tryLoad(String packageName, String className) {
            try {
                return Class.forName(packageName + "." + className.replace('.', '$'), false, loader);
            } catch (ClassNotFoundException e) {
                return null;
            }
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

        ObjectPlan object(Element element) throws Unsupported {
            if (isFx(element)) {
                throw new Unsupported("<fx:" + element.getLocalName() + ">");
            }
            Class<?> type = resolve(element.getLocalName());
            Map<String, String> plain = new LinkedHashMap<String, String>();
            List<String[]> statics = new ArrayList<String[]>();
            String fxId = null;
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
                    } else if (!"controller".equals(fxName)) {
                        throw new Unsupported("fx:" + fxName);
                    }
                    continue;
                }
                if (value.startsWith("$") || value.startsWith("%") || value.startsWith("@")) {
                    throw new Unsupported("Expression, resource or location value: " + name + "=\"" + value + "\"");
                }
                if (name.indexOf('.') > 0) {
                    statics.add(new String[]{name, value});
                } else {
                    plain.put(name, value);
                }
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
            String idProperty = fxId == null ? null : idProperty(type);
            if (idProperty != null && !plain.containsKey(idProperty)) {
                // Like FXMLLoader: fx:id also sets the @IDProperty (Node.id), so lookup("#name") works.
                plan.steps.add(0, property(type, idProperty, fxId));
            }
            children(element, type, plan);
            if (fxId != null && controllerType != null) {
                Field field = findField(controllerType, fxId, null);
                if (field != null) {
                    plan.remember = true;
                    injections.add(new Injection(field, plan));
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
                        bestNames = new ArrayList<String>();
                        bestScore = 1000;
                    }
                    continue;
                }
                String[] names = namedArgs(constructor);
                if (names == null) {
                    continue;
                }
                Object[] args = new Object[params.length];
                int matched = 0;
                int score = 0;
                boolean usable = true;
                for (int i = 0; i < params.length && usable; i++) {
                    String raw = attributes.get(names[i]);
                    if (raw == null) {
                        usable = false;
                        break;
                    }
                    Object converted = coerce(raw, params[i]);
                    if (converted == NOT_CONVERTIBLE) {
                        usable = false;
                        break;
                    }
                    args[i] = converted;
                    matched++;
                    score += preference(params[i]);
                }
                // More matched attributes first, then the narrowest parameter types (int before double).
                int total = usable ? score - matched * 100 : Integer.MAX_VALUE;
                if (usable && total < bestScore) {
                    best = constructor;
                    bestArgs = args;
                    bestNames = java.util.Arrays.asList(names);
                    bestScore = total;
                }
            }
            if (best == null) {
                throw new Unsupported("No usable constructor for " + type.getName());
            }
            for (String name : bestNames) {
                attributes.remove(name);
            }
            return new ObjectPlan(best, bestArgs);
        }

        String[] namedArgs(Constructor<?> constructor) {
            Annotation[][] annotations = constructor.getParameterAnnotations();
            String[] names = new String[annotations.length];
            for (int i = 0; i < annotations.length; i++) {
                for (Annotation annotation : annotations[i]) {
                    if (annotation instanceof NamedArg) {
                        names[i] = ((NamedArg) annotation).value();
                    }
                }
                if (names[i] == null) {
                    return null;
                }
            }
            return names;
        }

        Step property(Class<?> type, String name, String raw) throws Unsupported {
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (Method method : type.getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterTypes().length == 1
                        && !Modifier.isStatic(method.getModifiers())) {
                    final Object value = coerce(raw, method.getParameterTypes()[0]);
                    if (value != NOT_CONVERTIBLE) {
                        final Method setter = method;
                        return new Step() {
                            @Override
                            public void apply(Object target, Object controller) throws ReflectiveOperationException {
                                setter.invoke(target, value);
                            }
                        };
                    }
                }
            }
            throw new Unsupported("No setter " + type.getSimpleName() + "." + setterName + " for \"" + raw + "\"");
        }

        Step staticProperty(String qualified, String raw) throws Unsupported {
            int dot = qualified.lastIndexOf('.');
            Class<?> owner = resolve(qualified.substring(0, dot));
            String name = qualified.substring(dot + 1);
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterTypes().length == 2
                        && Modifier.isStatic(method.getModifiers())) {
                    final Object value = coerce(raw, method.getParameterTypes()[1]);
                    if (value != NOT_CONVERTIBLE) {
                        final Method setter = method;
                        return new Step() {
                            @Override
                            public void apply(Object target, Object controller) throws ReflectiveOperationException {
                                setter.invoke(null, target, value);
                            }
                        };
                    }
                }
            }
            throw new Unsupported("No static setter " + qualified);
        }

        Step handler(Class<?> type, String property, String methodName) throws Unsupported {
            if (controllerType == null) {
                throw new Unsupported("Event handler without fx:controller: #" + methodName);
            }
            String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            Method setter = null;
            for (Method method : type.getMethods()) {
                if (method.getName().equals(setterName) && method.getParameterTypes().length == 1
                        && EventHandler.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    setter = method;
                }
            }
            if (setter == null) {
                throw new Unsupported("No event property " + type.getSimpleName() + "." + property);
            }
            Method target = null;
            boolean withEvent = false;
            for (Class<?> c = controllerType; c != null && c != Object.class && target == null; c = c.getSuperclass()) {
                for (Method method : c.getDeclaredMethods()) {
                    if (!method.getName().equals(methodName)) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 1 && Event.class.isAssignableFrom(params[0])) {
                        target = method;
                        withEvent = true;
                    } else if (params.length == 0 && target == null) {
                        target = method;
                    }
                }
            }
            if (target == null) {
                throw new Unsupported("Controller has no method " + methodName);
            }
            target.setAccessible(true);
            final Method eventSetter = setter;
            final Method eventMethod = target;
            final boolean passEvent = withEvent;
            return new Step() {
                @Override
                public void apply(Object node, final Object controller) throws ReflectiveOperationException {
                    eventSetter.invoke(node, new EventHandler<Event>() {
                        @Override
                        public void handle(Event event) {
                            try {
                                if (passEvent) {
                                    eventMethod.invoke(controller, event);
                                } else {
                                    eventMethod.invoke(controller);
                                }
                            } catch (InvocationTargetException e) {
                                throw new RuntimeException(e.getCause());
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                }
            };
        }

        void children(Element element, Class<?> type, ObjectPlan plan) throws Unsupported {
            List<ObjectPlan> defaults = new ArrayList<ObjectPlan>();
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
                if (isFx(child)) {
                    throw new Unsupported("<fx:" + child.getLocalName() + ">");
                }
                String name = child.getLocalName();
                if (Character.isUpperCase(name.charAt(0))) {
                    defaults.add(object(child));
                } else {
                    if (child.getAttributes().getLength() > 0) {
                        throw new Unsupported("Attributes on property element <" + name + ">");
                    }
                    List<ObjectPlan> values = new ArrayList<ObjectPlan>();
                    for (Node inner = child.getFirstChild(); inner != null; inner = inner.getNextSibling()) {
                        if (inner instanceof Element) {
                            values.add(object((Element) inner));
                        } else if (inner.getNodeType() == Node.TEXT_NODE && !inner.getNodeValue().trim().isEmpty()) {
                            throw new Unsupported("Text value in property element <" + name + ">");
                        }
                    }
                    plan.steps.add(assign(type, name, values));
                }
            }
            if (!defaults.isEmpty()) {
                DefaultProperty annotation = type.getAnnotation(DefaultProperty.class);
                if (annotation == null) {
                    throw new Unsupported(type.getSimpleName() + " has no @DefaultProperty");
                }
                plan.steps.add(assign(type, annotation.value(), defaults));
            }
        }

        /** Adds to a list property (getX()) or sets a single-valued property (setX()). */
        Step assign(Class<?> type, String property, final List<ObjectPlan> values) throws Unsupported {
            String capital = Character.toUpperCase(property.charAt(0)) + property.substring(1);
            try {
                final Method getter = type.getMethod("get" + capital);
                if (Collection.class.isAssignableFrom(getter.getReturnType())) {
                    return new Step() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public void apply(Object target, Object controller) throws ReflectiveOperationException {
                            Collection<Object> list = (Collection<Object>) getter.invoke(target);
                            for (ObjectPlan value : values) {
                                list.add(value.build(controller));
                            }
                        }
                    };
                }
            } catch (NoSuchMethodException ignored) {
                // not a list property; try a setter
            }
            if (values.size() != 1) {
                throw new Unsupported(type.getSimpleName() + "." + property + " is not a list but has " + values.size() + " values");
            }
            for (Method method : type.getMethods()) {
                if (method.getName().equals("set" + capital) && method.getParameterTypes().length == 1) {
                    final Method setter = method;
                    final ObjectPlan value = values.get(0);
                    return new Step() {
                        @Override
                        public void apply(Object target, Object controller) throws ReflectiveOperationException {
                            setter.invoke(target, value.build(controller));
                        }
                    };
                }
            }
            throw new Unsupported("No property " + type.getSimpleName() + "." + property);
        }

        static boolean isFx(Node node) {
            String namespace = node.getNamespaceURI();
            return namespace != null && namespace.startsWith(FXML_NAMESPACE_PREFIX);
        }
    }

    // ------------------------------------------------------------ conversion

    private static final Object NOT_CONVERTIBLE = new Object();

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
        } catch (ReflectiveOperationException e) {
            return NOT_CONVERTIBLE;
        }
        return NOT_CONVERTIBLE;
    }
}
