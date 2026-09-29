package com.jxparallel.fx;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.jxparallel.fx.collections.JxObservableList;

/**
 * Converts between JX objects and the JavaFX objects that currently implement them. Every JX class
 * wraps a JavaFX object (its peer); values crossing the boundary are converted by name:
 * javafx.X (and com.sun.javafx.X, org.controlsfx.X) corresponds to com.jxparallel.fx.X
 * (com.jxparallel.fx.sun.X, com.jxparallel.fx.controlsfx.X). Objects without a counterpart
 * (strings, numbers, application objects) pass through unchanged.
 */
public final class Fx {

    /** Marker for the constructor that wraps an existing JavaFX object. */
    public static final class Wrap {
        private Wrap() {
        }
    }

    public static final Wrap WRAP = new Wrap();

    /** -Djx.backend=native: node classes run on the native renderer instead of JavaFX. */
    public static final boolean NATIVE = "native".equals(System.getProperty("jx.backend"));

    static {
        if (NATIVE && System.getProperty("prism.order") == null) {
            // In native mode JavaFX is only the event loop and never draws: its software pipeline
            // spares a Direct3D/OpenGL device next to the native renderer's (measured on Java 8
            // 32-bit: 16 MB less working set, 46 MB less private memory, 10% less CPU).
            System.setProperty("prism.order", "sw");
        }
        if (NATIVE) {
            // window system, graphics driver and fonts warm up while the application builds its screen
            com.jxparallel.ui.native2d.JXDisplay.prewarm();
        }
    }

    /** Implemented by every JX object backed by a JavaFX object. */
    public interface Backed {
        Object fxPeer();
    }

    /**
     * Implemented by the JavaFX subclasses a JX object creates for itself: they know their JX owner,
     * forward overridable methods to it and let the owner call the JavaFX implementation.
     */
    public interface Owned {
        Object jxOwner();

        void jxOwner(Object owner);

        Object callSuper(String method, Object[] args);
    }

    private static final String[][] PREFIXES = {
        {"com.sun.javafx.", "com.jxparallel.fx.sun."},
        {"org.controlsfx.", "com.jxparallel.fx.controlsfx."},
        {"javafx.", "com.jxparallel.fx."},
    };
    private static final Object NONE = new Object();

    private static final ClassValue<Class<?>> JX_CLASS = new ClassValue<Class<?>>() {
        @Override
        protected Class<?> computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                Class<?> jx = counterpart(c.getName(), 0, 1);
                if (jx != null) {
                    return jx;
                }
            }
            return null;
        }
    };
    private static final ClassValue<Class<?>> FX_CLASS = new ClassValue<Class<?>>() {
        @Override
        protected Class<?> computeValue(Class<?> type) {
            return counterpart(type.getName(), 1, 0);
        }
    };
    private static final ClassValue<Constructor<?>> WRAPPER = new ClassValue<Constructor<?>>() {
        @Override
        protected Constructor<?> computeValue(Class<?> jxType) {
            try {
                Constructor<?> c = jxType.getDeclaredConstructor(Wrap.class, Object.class);
                c.setAccessible(true);
                return c;
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    };
    /** For a JX class: the JavaFX interfaces its instances must present to JavaFX; for a JavaFX class, the reverse. */
    private static final ClassValue<Class<?>[]> FX_INTERFACES = new ClassValue<Class<?>[]>() {
        @Override
        protected Class<?>[] computeValue(Class<?> type) {
            return interfaces(type, 1, 0);
        }
    };
    private static final ClassValue<Class<?>[]> JX_INTERFACES = new ClassValue<Class<?>[]>() {
        @Override
        protected Class<?>[] computeValue(Class<?> type) {
            return interfaces(type, 0, 1);
        }
    };

    /** Wrappers of JavaFX objects JX did not create; weak both ways, a wrapper holds no state of its own. */
    private static final Map<Object, WeakReference<Object>> WRAPPERS = Collections.synchronizedMap(new WeakHashMap<>());
    /** JavaFX-side adapters of JX listeners and callbacks, so removeListener finds the adapter addListener used. */
    private static final Map<Object, WeakReference<Object>> ADAPTERS = Collections.synchronizedMap(new WeakHashMap<>());

    private Fx() {
    }

    private static Class<?> counterpart(String name, int from, int to) {
        for (String[] prefix : PREFIXES) {
            if (name.startsWith(prefix[from])) {
                String other = prefix[to] + name.substring(prefix[from].length());
                if (other.startsWith("com.jxparallel.fx.") && name.startsWith("com.jxparallel.fx.sun.") && from == 0) {
                    return null;
                }
                try {
                    return Class.forName(other, false, Fx.class.getClassLoader());
                } catch (ClassNotFoundException | LinkageError e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Class<?>[] interfaces(Class<?> type, int from, int to) {
        Set<Class<?>> out = new LinkedHashSet<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            collect(c.getInterfaces(), from, to, out);
        }
        return out.toArray(new Class<?>[0]);
    }

    private static void collect(Class<?>[] interfaces, int from, int to, Set<Class<?>> out) {
        for (Class<?> i : interfaces) {
            Class<?> other = counterpart(i.getName(), from, to);
            if (other != null && other.isInterface()) {
                out.add(other);
            }
            collect(i.getInterfaces(), from, to, out);
        }
    }

    /** The JX counterpart of a JavaFX value. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object jx(Object fx) {
        if (fx == null || fx instanceof Backed) {
            return fx;
        }
        if (fx instanceof Owned) {
            Object owner = ((Owned) fx).jxOwner();
            if (owner != null && (!(owner instanceof Backed) || ((Backed) owner).fxPeer() == fx)) {
                return owner;
            }
            if (owner != null) {
                // a clone of an owned peer (Event.copyFor clones the event with its owner field):
                // it is another object, so it gets its own wrapper of the owner's class
                WeakReference<Object> cached = WRAPPERS.get(fx);
                Object wrapper = cached == null ? null : cached.get();
                Constructor<?> c = WRAPPER.get(owner.getClass());
                if (wrapper == null && c != null) {
                    wrapper = newInstance(c, WRAP, fx);
                    WRAPPERS.put(fx, new WeakReference<>(wrapper));
                }
                return wrapper != null ? wrapper : owner;
            }
        }
        if (fx instanceof Enum) {
            Class<?> jxType = JX_CLASS.get(((Enum<?>) fx).getDeclaringClass());
            return jxType == null || !jxType.isEnum() ? fx : Enum.valueOf((Class) jxType, ((Enum<?>) fx).name());
        }
        if (fx instanceof javafx.collections.ObservableList) {
            return new JxObservableList<>((javafx.collections.ObservableList<Object>) fx);
        }
        if (fx instanceof AdapterHandler.Adapter) {
            return ((AdapterHandler.Adapter) fx).jxTarget();
        }
        Class<?> jxType = JX_CLASS.get(fx.getClass());
        if (jxType != null && !jxType.isInterface()) {
            WeakReference<Object> cached = WRAPPERS.get(fx);
            Object wrapper = cached == null ? null : cached.get();
            if (wrapper == null) {
                Constructor<?> c = WRAPPER.get(jxType);
                if (c == null) {
                    return fx;
                }
                wrapper = newInstance(c, WRAP, fx);
                WRAPPERS.put(fx, new WeakReference<>(wrapper));
            }
            return wrapper;
        }
        Class<?>[] jxInterfaces = JX_INTERFACES.get(fx.getClass());
        return jxInterfaces.length == 0 ? fx : adapter(fx, jxInterfaces, false);
    }

    /** The JavaFX counterpart of a JX value. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object fx(Object jx) {
        if (jx == null) {
            return null;
        }
        if (jx instanceof Backed) {
            return ((Backed) jx).fxPeer();
        }
        if (jx instanceof Enum) {
            Class<?> fxType = FX_CLASS.get(((Enum<?>) jx).getDeclaringClass());
            return fxType == null || !fxType.isEnum() ? jx : Enum.valueOf((Class) fxType, ((Enum<?>) jx).name());
        }
        if (jx instanceof AdapterHandler.Adapter) {
            return ((AdapterHandler.Adapter) jx).jxTarget();
        }
        Class<?>[] fxInterfaces = FX_INTERFACES.get(jx.getClass());
        return fxInterfaces.length == 0 ? jx : adapter(jx, fxInterfaces, true);
    }

    private static Object adapter(Object target, Class<?>[] interfaces, boolean toFx) {
        WeakReference<Object> cached = ADAPTERS.get(target);
        Object adapter = cached == null ? null : cached.get();
        if (adapter == null) {
            Class<?>[] all = new Class<?>[interfaces.length + 1];
            System.arraycopy(interfaces, 0, all, 0, interfaces.length);
            all[interfaces.length] = AdapterHandler.Adapter.class;
            adapter = Proxy.newProxyInstance(Fx.class.getClassLoader(), all, new AdapterHandler(target, toFx));
            ADAPTERS.put(target, new WeakReference<>(adapter));
        }
        return adapter;
    }

    /**
     * A java.util.function (or Comparator) object whose arguments and result are converted: a JX
     * lambda given to JavaFX ({@code toFx}), or a JavaFX one handed to JX code.
     */
    @SuppressWarnings("unchecked")
    public static <F> F functional(Object target, Class<F> iface, boolean toFx) {
        if (target == null || target instanceof AdapterHandler.Adapter) {
            return target == null ? null : (F) ((AdapterHandler.Adapter) target).jxTarget();
        }
        return (F) Proxy.newProxyInstance(Fx.class.getClassLoader(), new Class<?>[]{iface, AdapterHandler.Adapter.class},
                new AdapterHandler(target, toFx));
    }

    /** Makes {@link #jx} return this JX object for this JavaFX object (for handwritten wrappers). */
    public static void register(Object fx, Object jx) {
        WRAPPERS.put(fx, new WeakReference<>(jx));
    }

    /** Called by generated constructors once the JX object exists, so JavaFX callbacks reach it. */
    public static <T> T own(Object peer, Object owner) {
        if (peer instanceof Owned) {
            ((Owned) peer).jxOwner(owner);
        }
        @SuppressWarnings("unchecked")
        T t = (T) peer;
        return t;
    }

    /** JX objects converted to a JavaFX array of the given component type. */
    @SuppressWarnings("unchecked")
    public static <T> T[] fxArray(Object[] jx, Class<T> fxComponent) {
        if (jx == null) {
            return null;
        }
        T[] out = (T[]) Array.newInstance(fxComponent, jx.length);
        for (int i = 0; i < jx.length; i++) {
            out[i] = (T) fx(jx[i]);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] jxArray(Object[] fx, Class<T> jxComponent) {
        if (fx == null) {
            return null;
        }
        T[] out = (T[]) Array.newInstance(jxComponent, fx.length);
        for (int i = 0; i < fx.length; i++) {
            out[i] = (T) jx(fx[i]);
        }
        return out;
    }

    /** A copy of a collection with each element converted to JavaFX, of the same kind (list or set). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <C extends Collection> C fxCollection(Collection<?> jx) {
        if (jx == null || jx instanceof Backed) {
            return (C) fx(jx);
        }
        Collection<Object> out = jx instanceof Set ? new LinkedHashSet<>() : new ArrayList<>(jx.size());
        for (Object o : jx) {
            out.add(fx(o));
        }
        return (C) out;
    }

    /** A view of a JavaFX list whose elements read and write as JX objects. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <L extends List> L jxList(List<?> fx) {
        if (fx == null || fx instanceof javafx.collections.ObservableList) {
            return (L) jx(fx);
        }
        List<Object> list = (List<Object>) fx;
        return (L) new AbstractList<Object>() {
            @Override
            public Object get(int index) {
                return jx(list.get(index));
            }

            @Override
            public Object set(int index, Object element) {
                return jx(list.set(index, fx(element)));
            }

            @Override
            public void add(int index, Object element) {
                list.add(index, fx(element));
            }

            @Override
            public Object remove(int index) {
                return jx(list.remove(index));
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <I extends java.util.Iterator> I jxIterator(java.util.Iterator<?> fx) {
        if (fx == null) {
            return null;
        }
        return (I) new java.util.Iterator<Object>() {
            @Override
            public boolean hasNext() {
                return fx.hasNext();
            }

            @Override
            public Object next() {
                return jx(fx.next());
            }

            @Override
            public void remove() {
                fx.remove();
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <C extends Collection> C jxCollection(Collection<?> fx) {
        if (fx == null || fx instanceof List) {
            return (C) jxList((List<?>) fx);
        }
        Collection<Object> out = fx instanceof Set ? new LinkedHashSet<>() : new ArrayList<>(fx.size());
        for (Object o : fx) {
            out.add(jx(o));
        }
        return (C) Collections.unmodifiableCollection(out);
    }

    /** A copy of a map with keys and values converted to JavaFX (for example clipboard content keyed by DataFormat). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <M extends Map> M fxMap(Map<?, ?> jx) {
        if (jx == null) {
            return null;
        }
        Map<Object, Object> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : jx.entrySet()) {
            out.put(fx(e.getKey()), fx(e.getValue()));
        }
        return (M) out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <M extends Map> M jxMap(Map<?, ?> fx) {
        if (fx == null) {
            return null;
        }
        Map<Object, Object> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : fx.entrySet()) {
            out.put(jx(e.getKey()), jx(e.getValue()));
        }
        return (M) out;
    }

    private static final ClassValue<Map<String, Method>> DECLARED = new ClassValue<Map<String, Method>>() {
        @Override
        protected Map<String, Method> computeValue(Class<?> type) {
            return new java.util.concurrent.ConcurrentHashMap<>();
        }
    };

    /**
     * The peer as the JavaFX type a generated method needs. In native mode a native model stands
     * where a plain superclass expects its JavaFX object (FadeTransition's inherited Animation.play()),
     * and the native layer supplies that object.
     */
    public static Object peerAs(Object peer, Class<?> type) {
        return !NATIVE || type.isInstance(peer) ? peer : com.jxparallel.fx.nativeimpl.Native.adapter(peer, type);
    }

    /**
     * {@link #fx} for an argument of a plain JavaFX class whose type is a node class: in native mode
     * the JX object has no JavaFX counterpart, so the JavaFX method gets {@code null}.
     */
    public static Object fxAs(Object jx, Class<?> type) {
        Object fx = fx(jx);
        return NATIVE && fx != null && !type.isInstance(fx) ? null : fx;
    }

    /** Calls a non-public JavaFX method on an object JX did not create (for example a protected hook). */
    public static Object invoke(Object target, Class<?> declaring, String name, Class<?>[] types, Object... args) {
        try {
            String key = name + java.util.Arrays.toString(types);
            Map<String, Method> methods = DECLARED.get(declaring);
            Method m = methods.get(key);
            if (m == null) {
                m = declaring.getDeclaredMethod(name, types);
                m.setAccessible(true);
                methods.put(key, m);
            }
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw sneaky(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object newInstance(Constructor<?> c, Object... args) {
        try {
            return c.newInstance(args);
        } catch (InvocationTargetException e) {
            throw sneaky(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Rethrows a checked exception from code whose signature cannot declare it (hooks, reflection). */
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException sneaky(Throwable e) throws E {
        throw (E) e;
    }

    /** Presents a listener or callback written against one API to the other, converting arguments and results. */
    static final class AdapterHandler implements InvocationHandler {
        interface Adapter {
            Object jxTarget();
        }

        private final Object target;
        private final boolean toFx;

        AdapterHandler(Object target, boolean toFx) {
            this.target = target;
            this.toFx = toFx;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "jxTarget":
                    return target;
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(target);
                case "toString":
                    return target.toString();
                default:
                    break;
            }
            Method targetMethod = find(target.getClass(), method);
            Object[] converted = args == null ? null : new Object[args.length];
            for (int i = 0; converted != null && i < args.length; i++) {
                converted[i] = toFx ? jx(args[i]) : fx(args[i]);
            }
            try {
                Object result = targetMethod.invoke(target, converted);
                return toFx ? fx(result) : jx(result);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private static final ClassValue<Map<Method, Method>> TARGETS = new ClassValue<Map<Method, Method>>() {
            @Override
            protected Map<Method, Method> computeValue(Class<?> type) {
                return Collections.synchronizedMap(new java.util.HashMap<>());
            }
        };

        private static Method find(Class<?> type, Method method) {
            return TARGETS.get(type).computeIfAbsent(method, m -> {
                for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                    for (Method candidate : c.getDeclaredMethods()) {
                        if (candidate.getName().equals(m.getName()) && !candidate.isBridge()
                                && candidate.getParameterCount() == m.getParameterCount()
                                && !java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) {
                            candidate.setAccessible(true);
                            return candidate;
                        }
                    }
                }
                for (Class<?> i : interfacesOf(type)) {
                    for (Method candidate : i.getMethods()) {
                        if (candidate.getName().equals(m.getName()) && candidate.getParameterCount() == m.getParameterCount()) {
                            return candidate;
                        }
                    }
                }
                throw new IllegalStateException("No " + m.getName() + " in " + type.getName());
            });
        }

        private static List<Class<?>> interfacesOf(Class<?> type) {
            List<Class<?>> out = new ArrayList<>();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                Collections.addAll(out, c.getInterfaces());
            }
            return out;
        }
    }
}
