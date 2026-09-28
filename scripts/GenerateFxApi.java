import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.jar.JarFile;

import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;

/**
 * Generates the JavaFX-compatible API of jxparallel-fx (phase 2 of docs/migracao-deviceconfig.md).
 *
 * For every JavaFX type in the closure of the seed list (seeds, their public supertypes and public
 * nested types) it writes com.jxparallel.fx.X with the same members, where every JavaFX type in a
 * signature becomes its JX counterpart. A JX object wraps a JavaFX object (its peer) and converts
 * values with com.jxparallel.fx.Fx. When JX creates the object, the peer is
 * com.jxparallel.fx.peer.X, a JavaFX subclass that forwards the overridable methods applications
 * override (abstract ones and HOOKS) to the JX object, so a JX subclass behaves like a JavaFX one.
 *
 *   javac -d target scripts/GenerateFxApi.java
 *   java -cp target GenerateFxApi seeds.txt handwritten.txt jxparallel-fx/src/main/java
 *
 * seeds.txt lines: a class name, or a package name ending in ".*" (all its public top-level classes).
 */
public class GenerateFxApi {
    private static final String JX = "com.jxparallel.fx.";
    private static final String PEER = "com.jxparallel.fx.peer.";
    /** Overridable non-abstract methods that applications override; the peer forwards them. */
    private static final Set<String> HOOKS = new HashSet<>(Arrays.asList(
            "updateItem", "startEdit", "commitEdit", "cancelEdit", "updateIndex", "updateSelected",
            "init", "stop", "handleStateChangeNotification", "handleProgressNotification",
            "handleApplicationNotification", "handleErrorNotification",
            "succeeded", "failed", "cancelled", "running", "scheduled"));

    private final Set<Class<?>> mapped = new LinkedHashSet<>();
    /** Type variables of an interface as seen from the class being generated (empty otherwise). */
    private Map<String, Type> env = new HashMap<>();
    private final Set<String> handwritten = new HashSet<>();
    private final Map<String, String> report = new LinkedHashMap<>();
    private final String out;

    GenerateFxApi(String out) {
        this.out = out;
    }

    public static void main(String[] args) throws Exception {
        GenerateFxApi g = new GenerateFxApi(args[2]);
        for (String line : lines(args[1])) {
            g.handwritten.add(line.split("\\s+")[0]);
        }
        List<Class<?>> seeds = new ArrayList<>();
        for (String line : lines(args[0])) {
            String name = line.split("\\s+")[0];
            if (name.endsWith(".*")) {
                seeds.addAll(packageClasses(name.substring(0, name.length() - 2)));
            } else {
                seeds.add(load(name));
            }
        }
        g.close(seeds);
        for (Class<?> c : g.mapped) {
            if (c.getEnclosingClass() == null) {
                g.write(JX + rest(c.getName()), g.topLevel(c));
            }
            if (g.needsPeer(c)) {
                g.write(PEER + rest(c.getName()).replace('$', '_'), g.peer(c));
            }
        }
        System.out.println("NATIVE " + g.nativeClasses().size());
        for (Map.Entry<String, String> e : g.report.entrySet()) {
            System.out.println(e.getValue() + " " + e.getKey());
        }
        System.out.println("MAPPED " + g.mapped.size());
    }

    // ---------------------------------------------------------------- the mapped set

    private void close(List<Class<?>> seeds) {
        List<Class<?>> queue = new ArrayList<>(seeds);
        while (!queue.isEmpty()) {
            Class<?> c = queue.remove(queue.size() - 1);
            if (mapped.contains(c) || !isFx(c) || handwritten.contains(c.getName())) {
                continue;
            }
            String skip = skip(c);
            if (skip != null) {
                report.put(c.getName(), "SKIP (" + skip + ")");
                continue;
            }
            mapped.add(c);
            report.put(c.getName(), "GENERATED");
            if (c.getSuperclass() != null) {
                queue.add(c.getSuperclass());
            }
            queue.addAll(Arrays.asList(c.getInterfaces()));
            for (Class<?> nested : c.getDeclaredClasses()) {
                if (Modifier.isPublic(nested.getModifiers()) && Modifier.isStatic(nested.getModifiers())) {
                    queue.add(nested);
                }
            }
            if (c.getEnclosingClass() != null) {
                queue.add(c.getEnclosingClass());
            }
        }
    }

    private static boolean isFx(Class<?> c) {
        return c.getName().startsWith("javafx.") || c.getName().startsWith("org.controlsfx.");
    }

    private static String skip(Class<?> c) {
        if (!Modifier.isPublic(c.getModifiers())) {
            return "not public";
        }
        if (c.isAnnotation()) {
            return "annotation";
        }
        if (c.getSimpleName().endsWith("Builder")) {
            return "deprecated builder";
        }
        if (c.getSuperclass() != null && (c.getSuperclass().getName().startsWith("javafx.css.")
                || c.getSuperclass().getName().startsWith("com.sun."))) {
            return "CSS converter internals";
        }
        if (Collection.class.isAssignableFrom(c) || Map.class.isAssignableFrom(c)) {
            return "a java.util collection";
        }
        if (c.getEnclosingClass() != null && !Modifier.isStatic(c.getModifiers()) && !c.isInterface() && !c.isEnum()) {
            return "inner class";
        }
        return null;
    }

    private boolean isMapped(Class<?> c) {
        return mapped.contains(c) || handwritten.contains(c.getName());
    }

    private boolean needsPeer(Class<?> c) {
        if (c.isInterface() || c.isEnum() || Modifier.isFinal(c.getModifiers()) || constructors(c).isEmpty()) {
            return false;
        }
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (Modifier.isAbstract(m.getModifiers()) && (m.getName().startsWith("impl_") || !visible(m)
                        || !isMapped(m.getDeclaringClass())) && implementation(c, m) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether every type in m's signature is public (a protected nested type cannot be named by JX). */
    private static boolean visible(Executable m) {
        List<Class<?>> types = new ArrayList<>(Arrays.asList(m.getParameterTypes()));
        if (m instanceof Method) {
            types.add(((Method) m).getReturnType());
        }
        for (Class<?> t : types) {
            while (t.isArray()) {
                t = t.getComponentType();
            }
            for (Class<?> k = t; k != null; k = k.getEnclosingClass()) {
                if (!k.isPrimitive() && !Modifier.isPublic(k.getModifiers())) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- JX types

    private String topLevel(Class<?> c) {
        StringBuilder s = new StringBuilder();
        s.append("package ").append(JX).append(rest(c.getPackage().getName()).replaceAll("\\.$", "")).append(";\n\n");
        s.append("/** JXParallel counterpart of {@link ").append(c.getName().replace('$', '.'))
                .append("} (generated by scripts/GenerateFxApi.java). */\n");
        s.append("@SuppressWarnings({\"unchecked\", \"rawtypes\", \"deprecation\"})\n");
        type(c, s, "");
        return s.toString().replace("package com.jxparallel.fx.;", "package com.jxparallel.fx;");
    }

    private void type(Class<?> c, StringBuilder s, String indent) {
        if (c.isEnum()) {
            enumType(c, s, indent);
        } else if (c.isInterface()) {
            interfaceType(c, s, indent);
        } else {
            classType(c, s, indent);
        }
    }

    private void nested(Class<?> c, StringBuilder s, String indent) {
        for (Class<?> n : c.getDeclaredClasses()) {
            if (mapped.contains(n)) {
                s.append('\n');
                type(n, s, indent + "    ");
            }
        }
    }

    private void interfaceType(Class<?> c, StringBuilder s, String indent) {
        List<Method> methods = new ArrayList<>();
        for (Method m : declared(c)) {
            if (Modifier.isAbstract(m.getModifiers())) {
                methods.add(m);
            }
        }
        if (methods.size() == 1 && functional(c)) {
            s.append(indent).append("@FunctionalInterface\n");
        }
        s.append(indent).append("public interface ").append(c.getSimpleName()).append(typeParams(c.getTypeParameters()));
        StringJoiner ext = new StringJoiner(", ", " extends ", "");
        ext.setEmptyValue("");
        for (Type i : c.getGenericInterfaces()) {
            if (isMapped(raw(i)) || !isFx(raw(i)) && !raw(i).getName().startsWith("com.sun.")) {
                ext.add(jx(i));
            }
        }
        s.append(ext).append(" {\n");
        for (Method m : methods) {
            s.append('\n').append(indent).append("    ").append(typeParams(m.getTypeParameters()))
                    .append(m.getTypeParameters().length > 0 ? " " : "").append(jx(m.getGenericReturnType())).append(' ')
                    .append(m.getName()).append('(').append(params(m, true)).append(");\n");
        }
        nested(c, s, indent);
        s.append(indent).append("}\n");
    }

    private void enumType(Class<?> c, StringBuilder s, String indent) {
        current = c;
        String fx = fxName(c);
        s.append(indent).append("public enum ").append(c.getSimpleName()).append(" {\n");
        StringJoiner constants = new StringJoiner(",\n" + indent + "    ", indent + "    ", ";\n");
        for (Object constant : c.getEnumConstants()) {
            constants.add(((Enum<?>) constant).name());
        }
        s.append(constants);
        staticFields(c, s, indent + "    ", fx);
        for (Method m : declared(c)) {
            if (m.getName().equals("values") || m.getName().equals("valueOf") || !Modifier.isPublic(m.getModifiers())) {
                continue;
            }
            boolean isStatic = Modifier.isStatic(m.getModifiers());
            String target = isStatic ? fx : "((" + fx + ") com.jxparallel.fx.Fx.fx(this))";
            method(m, s, indent, isStatic ? "public static " : "public ", target, false);
        }
        nested(c, s, indent);
        s.append(indent).append("}\n");
    }

    /** Public static fields (constants such as Region.USE_PREF_SIZE or TransferMode.COPY_OR_MOVE). */
    private void staticFields(Class<?> c, StringBuilder s, String in, String fx) {
        for (Field f : c.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod) || f.isSynthetic() || f.isEnumConstant()) {
                continue;
            }
            Class<?> t = f.getType();
            String source = fx + "." + f.getName();
            String value;
            if (t.isPrimitive() || t == String.class || !jxable(f.getGenericType())) {
                value = source;
            } else if (t.isArray()) {
                value = "com.jxparallel.fx.Fx.jxArray(" + source + ", " + jxErasure(t.getComponentType()) + ".class)";
            } else {
                value = "(" + jxErasure(t) + ") com.jxparallel.fx.Fx.jx(" + source + ")";
            }
            s.append('\n').append(in).append("public static final ").append(jx(f.getGenericType())).append(' ')
                    .append(f.getName()).append(" = ").append(value).append(";\n");
        }
    }

    /** The class whose members are being written (for the native branch of node classes). */
    private Class<?> current;

    private void classType(Class<?> c, StringBuilder s, String indent) {
        current = c;
        String fx = fxName(c);
        Class<?> sup = c.getSuperclass();
        boolean root = sup == null || !isMapped(sup);
        boolean nestedType = c.getEnclosingClass() != null;
        DefaultProperty dp = c.getAnnotation(DefaultProperty.class);
        if (dp != null) {
            s.append(indent).append("@javafx.beans.DefaultProperty(\"").append(dp.value()).append("\")\n");
        }
        s.append(indent).append("public ").append(nestedType ? "static " : "")
                .append(Modifier.isFinal(c.getModifiers()) ? "final " : "").append("class ").append(c.getSimpleName())
                .append(typeParams(c.getTypeParameters()));
        if (!root) {
            s.append(" extends ").append(jx(c.getGenericSuperclass()));
        }
        StringJoiner impl = new StringJoiner(", ", " implements ", "");
        impl.setEmptyValue("");
        for (Type i : c.getGenericInterfaces()) {
            if (isMapped(raw(i))) {
                impl.add(jx(i));
            } else if (!isFx(raw(i)) && !raw(i).getName().startsWith("com.sun.")) {
                impl.add(jx(i));
            }
        }
        List<Class<?>> javaSupers = new ArrayList<>();
        if (root) {
            for (Class<?> j = sup; j != null && j != Object.class; j = j.getSuperclass()) {
                javaSupers.add(j);
                for (Type i : j.getGenericInterfaces()) {
                    impl.add(jx(i));
                }
            }
            impl.add("com.jxparallel.fx.Fx.Backed");
        }
        s.append(impl).append(" {\n");
        String in = indent + "    ";
        if (root) {
            s.append(in).append("private final Object fxPeer;\n\n")
                    .append(in).append("protected ").append(c.getSimpleName()).append("(com.jxparallel.fx.Fx.Wrap wrap, Object peer) {\n")
                    .append(in).append("    this.fxPeer = peer;\n").append(in).append("}\n\n")
                    .append(in).append("@Override\n").append(in).append("public Object fxPeer() {\n")
                    .append(in).append("    return fxPeer;\n").append(in).append("}\n\n")
                    .append(in).append("@Override\n").append(in).append("public boolean equals(Object o) {\n")
                    .append(in).append("    return o == this || fxPeer.equals(com.jxparallel.fx.Fx.fx(o));\n").append(in).append("}\n\n")
                    .append(in).append("@Override\n").append(in).append("public int hashCode() {\n")
                    .append(in).append("    return fxPeer.hashCode();\n").append(in).append("}\n\n")
                    .append(in).append("@Override\n").append(in).append("public String toString() {\n")
                    .append(in).append("    return fxPeer.toString();\n").append(in).append("}\n");
        } else {
            s.append(in).append("protected ").append(c.getSimpleName()).append("(com.jxparallel.fx.Fx.Wrap wrap, Object peer) {\n")
                    .append(in).append("    super(wrap, peer);\n").append(in).append("}\n");
        }
        boolean instantiable = needsPeer(c) || !Modifier.isAbstract(c.getModifiers());
        for (Constructor<?> k : instantiable ? constructors(c) : new ArrayList<Constructor<?>>()) {
            String peerType = needsPeer(c) ? PEER + rest(c.getName()).replace('$', '_') : fx;
            s.append('\n').append(in).append(Modifier.isPublic(k.getModifiers()) ? "public " : "protected ")
                    .append(typeParams(k.getTypeParameters())).append(k.getTypeParameters().length > 0 ? " " : "")
                    .append(c.getSimpleName()).append('(').append(params(k, true)).append(')').append(throwsClause(k)).append(" {\n")
                    .append(in).append("    this(com.jxparallel.fx.Fx.WRAP, ").append(isNode(c) ? nativeCreate(c, k) + " : " : "")
                    .append("new ").append(peerType).append('(').append(fxArgs(k)).append("));\n")
                    .append(in).append("    com.jxparallel.fx.Fx.own(fxPeer(), this);\n")
                    .append(in).append("}\n");
        }
        staticFields(c, s, in, fx);
        Set<String> done = new HashSet<>();
        for (Method m : declared(c)) {
            if (done.add(signature(m))) {
                boolean isStatic = Modifier.isStatic(m.getModifiers());
                String visibility = Modifier.isPublic(m.getModifiers()) ? "public " : "protected ";
                method(m, s, indent, visibility + (isStatic ? "static " : ""), isStatic ? fx : "((" + fx + ") fxPeer())", isHook(m));
            }
        }
        for (Type i : c.getGenericInterfaces()) {
            if (!isMapped(raw(i))) {
                continue;
            }
            for (Method m : raw(i).getMethods()) {
                if (Modifier.isAbstract(m.getModifiers()) && !isObjectMethod(m) && implementation(c, m) == null
                        && inheritedDeclaration(c, m) == null && done.add(signature(m))) {
                    env = allVars(c);
                    method(m, s, indent, "public ", "((" + fx + ") fxPeer())", true);
                    env = new HashMap<>();
                }
            }
        }
        for (Class<?> j : javaSupers) {
            for (Method m : j.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()) && !m.isSynthetic()
                        && !isObjectMethod(m) && done.add(signature(m))) {
                    method(m, s, indent, "public ", "((" + fx + ") fxPeer())", false);
                }
            }
        }
        List<Method> hooks = needsPeer(c) ? hooksOf(c) : new ArrayList<>();
        if (!hooks.isEmpty()) {
            s.append('\n').append(in).append("/** Called by the peer: runs the JX (possibly overridden) method. */\n")
                    .append(in).append("public static Object $hook(Object self, String method, Object[] a) {\n")
                    .append(in).append("    ").append(c.getSimpleName()).append(" jx = (").append(c.getSimpleName()).append(") self;\n")
                    .append(in).append("    try {\n")
                    .append(in).append("    switch (method) {\n");
            for (Method m : hooks) {
                s.append(in).append("        case \"").append(signature(m)).append("\":\n");
                String call = "jx." + m.getName() + "(" + castArgs(m, allVars(c)) + ")";
                if (m.getReturnType().isPrimitive() && m.getReturnType() != void.class) {
                    call = "(Object) " + call;
                }
                if (m.getReturnType() == void.class) {
                    s.append(in).append("            ").append(call).append(";\n").append(in).append("            return null;\n");
                } else {
                    s.append(in).append("            return ").append(call).append(";\n");
                }
            }
            s.append(in).append("        default:\n").append(in).append("            throw new IllegalArgumentException(method);\n")
                    .append(in).append("    }\n")
                    .append(in).append("    } catch (Exception e) {\n")
                    .append(in).append("        throw com.jxparallel.fx.Fx.sneaky(e);\n")
                    .append(in).append("    }\n").append(in).append("}\n");
        }
        nested(c, s, indent);
        s.append(indent).append("}\n");
    }

    /** Native-mode construction: named arguments (@NamedArg) become property values. */
    private String nativeCreate(Class<?> c, Constructor<?> k) {
        StringJoiner names = new StringJoiner(", ");
        StringJoiner args = new StringJoiner(", ");
        Annotation[][] annotations = k.getParameterAnnotations();
        int offset = annotations.length - k.getGenericParameterTypes().length;
        for (int i = 0; i < k.getGenericParameterTypes().length; i++) {
            String name = null;
            for (Annotation a : annotations[i + Math.max(0, offset)]) {
                if (a instanceof NamedArg) {
                    name = ((NamedArg) a).value();
                }
            }
            names.add(name == null ? "null" : "\"" + name + "\"");
            args.add("arg" + i);
        }
        return "com.jxparallel.fx.Fx.NATIVE ? com.jxparallel.fx.nativeimpl.Native.create(" + fxName(c) + ".class, new String[] {"
                + names + "}, new Object[] {" + args + "})";
    }

    /**
     * Classes with a native backend (-Djx.backend=native): nodes, and every class whose API mentions a
     * node or another such class (Tab, Tooltip, Scene, Stage, ToggleGroup, events...). Plain values
     * (Insets, Color, constraints, properties, collections) always use JavaFX.
     */
    private boolean isNode(Class<?> c) {
        return c != null && nativeClasses().contains(c);
    }

    private Set<Class<?>> nativeSet;

    private Set<Class<?>> nativeClasses() {
        if (nativeSet != null) {
            return nativeSet;
        }
        Set<Class<?>> set = new HashSet<>();
        for (Class<?> c : mapped) {
            if (javafx.scene.Node.class.isAssignableFrom(c) || javafx.scene.control.Toggle.class.isAssignableFrom(c)) {
                set.add(c);
            }
        }
        for (boolean grew = true; grew; ) {
            grew = false;
            for (Class<?> c : mapped) {
                if (!set.contains(c) && !c.isEnum() && !c.isInterface() && mentions(c, set)) {
                    set.add(c);
                    grew = true;
                }
            }
        }
        nativeSet = set;
        return set;
    }

    /** Whether a public member of c takes or returns (also as a type argument) a class of the set. */
    private static boolean mentions(Class<?> c, Set<Class<?>> set) {
        List<Type> types = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                types.add(m.getGenericReturnType());
                types.addAll(Arrays.asList(m.getGenericParameterTypes()));
            }
        }
        for (Constructor<?> k : c.getConstructors()) {
            types.addAll(Arrays.asList(k.getGenericParameterTypes()));
        }
        for (Type t : types) {
            if (mentionsType(t, set)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsType(Type t, Set<Class<?>> set) {
        if (t instanceof Class) {
            Class<?> k = (Class<?>) t;
            while (k.isArray()) {
                k = k.getComponentType();
            }
            return set.contains(k) || javafx.scene.Node.class.isAssignableFrom(k);
        }
        if (t instanceof ParameterizedType) {
            if (mentionsType(((ParameterizedType) t).getRawType(), set)) {
                return true;
            }
            for (Type a : ((ParameterizedType) t).getActualTypeArguments()) {
                if (mentionsType(a, set)) {
                    return true;
                }
            }
        }
        if (t instanceof WildcardType) {
            for (Type b : ((WildcardType) t).getUpperBounds()) {
                if (mentionsType(b, set)) {
                    return true;
                }
            }
            for (Type b : ((WildcardType) t).getLowerBounds()) {
                if (mentionsType(b, set)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** In native mode the method is served by com.jxparallel.fx.nativeimpl.Native. */
    private void nativeBranch(Method m, StringBuilder s, String in, Type ret, boolean isStatic) {
        StringJoiner types = new StringJoiner(", ");
        StringJoiner args = new StringJoiner(", ");
        Type[] ps = m.getGenericParameterTypes();
        for (int i = 0; i < ps.length; i++) {
            types.add(classLiteral(resolve(ps[i], env)));
            args.add("arg" + i);
        }
        String call = "com.jxparallel.fx.nativeimpl.Native.call(" + (isStatic ? "null" : "this") + ", "
                + jxErasure(current) + ".class, \"" + m.getName() + "\", new Class<?>[] {" + types + "}, "
                + classLiteral(ret) + (args.length() == 0 ? "" : ", " + args) + ")";
        s.append(in).append("    if (com.jxparallel.fx.Fx.NATIVE) {\n");
        if (m.getReturnType() == void.class) {
            s.append(in).append("        ").append(call).append(";\n").append(in).append("        return;\n");
        } else if (raw(ret).isPrimitive()) {
            s.append(in).append("        return (").append(box(raw(ret))).append(") ").append(call).append(";\n");
        } else {
            s.append(in).append("        return (").append(erasedJx(ret)).append(") ").append(call).append(";\n");
        }
        s.append(in).append("    }\n");
    }

    private String classLiteral(Type t) {
        if (t instanceof TypeVariable) {
            return jxErasure(raw(t)) + ".class";
        }
        Class<?> r = raw(t);
        return (r.isPrimitive() ? r.getName() : jxErasure(r)) + ".class";
    }

    private void method(Method m, StringBuilder s, String indent, String modifiers, String target, boolean hook) {
        String in = indent + "    ";
        Type ret = resolve(m.getGenericReturnType(), env);
        s.append('\n').append(in).append(modifiers).append(typeParams(m.getTypeParameters()))
                .append(m.getTypeParameters().length > 0 ? " " : "").append(jx(ret)).append(' ').append(m.getName())
                .append('(').append(params(m, true)).append(')').append(throwsClause(m)).append(" {\n");
        if (isNode(current)) {
            nativeBranch(m, s, in, ret, modifiers.contains("static "));
        }
        String call;
        boolean isProtected = Modifier.isProtected(m.getModifiers());
        if (hook) {
            call = "(fxPeer() instanceof com.jxparallel.fx.Fx.Owned ? ((com.jxparallel.fx.Fx.Owned) fxPeer()).callSuper(\""
                    + signature(m) + "\", new Object[] {" + fxArgs(m) + "}) : " + reflective(m) + ")";
        } else if (isProtected) {
            call = reflective(m);
        } else {
            call = target + "." + m.getName() + "(" + fxArgs(m) + ")";
        }
        if (m.getReturnType() == void.class && hook) {
            s.append(in).append("    if (fxPeer() instanceof com.jxparallel.fx.Fx.Owned) {\n")
                    .append(in).append("        ((com.jxparallel.fx.Fx.Owned) fxPeer()).callSuper(\"").append(signature(m))
                    .append("\", new Object[] {").append(fxArgs(m)).append("});\n")
                    .append(in).append("    } else {\n").append(in).append("        ").append(reflective(m)).append(";\n")
                    .append(in).append("    }\n");
        } else if (m.getReturnType() == void.class) {
            s.append(in).append("    ").append(call).append(";\n");
        } else {
            s.append(in).append("    return ").append(toJx(ret, call, hook || isProtected)).append(";\n");
        }
        s.append(in).append("}\n");
    }

    private String reflective(Method m) {
        StringJoiner types = new StringJoiner(", ");
        for (Class<?> p : m.getParameterTypes()) {
            types.add(p.getCanonicalName() + ".class");
        }
        String args = fxArgs(m);
        return "com.jxparallel.fx.Fx.invoke(fxPeer(), " + m.getDeclaringClass().getCanonicalName() + ".class, \"" + m.getName()
                + "\", new Class<?>[] {" + types + "}" + (args.isEmpty() ? "" : ", " + args) + ")";
    }

    // ---------------------------------------------------------------- peers

    private String peer(Class<?> c) {
        String name = rest(c.getName()).replace('$', '_');
        String simple = name.substring(name.lastIndexOf('.') + 1);
        String pkg = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : "";
        StringBuilder s = new StringBuilder();
        s.append("package ").append(PEER).append(pkg).append(";\n\n")
                .append("/** JavaFX peer of {@link ").append(JX).append(rest(c.getName()).replace('$', '.'))
                .append("}; forwards overridable methods to it (generated by scripts/GenerateFxApi.java). */\n")
                .append("@SuppressWarnings({\"unchecked\", \"rawtypes\", \"deprecation\", \"serial\"})\n")
                .append("public class ").append(simple).append(typeParams(c.getTypeParameters(), true))
                .append(" extends ").append(fxName(c)).append(diamond(c).equals("<>") ? typeArgs(c) : "")
                .append(" implements com.jxparallel.fx.Fx.Owned {\n")
                .append("    private Object jxOwner;\n\n");
        for (Constructor<?> k : constructors(c)) {
            s.append("    public ").append(simple).append('(').append(params(k, false)).append(')').append(throwsClause(k))
                    .append(" {\n        super(").append(argNames(k)).append(");\n    }\n\n");
        }
        s.append("    @Override\n    public Object jxOwner() {\n        return jxOwner;\n    }\n\n")
                .append("    @Override\n    public void jxOwner(Object owner) {\n        this.jxOwner = owner;\n    }\n");
        Map<String, Type> vars = allVars(c);
        List<Method> hooks = hooksOf(c);
        for (Method m : hooks) {
            boolean isAbstract = Modifier.isAbstract(m.getModifiers());
            Type ret = resolve(m.getGenericReturnType(), vars);
            s.append("\n    @Override\n    ").append(Modifier.isPublic(m.getModifiers()) ? "public " : "protected ")
                    .append(typeParams(m.getTypeParameters(), true)).append(m.getTypeParameters().length > 0 ? " " : "")
                    .append(fxType(ret)).append(' ').append(m.getName()).append('(').append(resolvedParams(m, vars)).append(") {\n");
            String owner = JX + rest(c.getName()).replace('$', '.');
            StringJoiner jxArgs = new StringJoiner(", ");
            for (int i = 0; i < m.getParameterCount(); i++) {
                jxArgs.add(m.getParameterTypes()[i].isPrimitive() ? "arg" + i : "com.jxparallel.fx.Fx.jx(arg" + i + ")");
            }
            String call = owner + ".$hook(jxOwner, \"" + signature(m) + "\", new Object[] {" + jxArgs + "})";
            String superCall = isAbstract ? null : "super." + m.getName() + "(" + argNames(m) + ")";
            s.append("        if (jxOwner == null) {\n");
            if (superCall == null) {
                s.append("            throw new IllegalStateException(\"").append(m.getName()).append(" before the JX object exists\");\n");
            } else if (m.getReturnType() == void.class) {
                s.append("            ").append(superCall).append(";\n            return;\n");
            } else {
                s.append("            return ").append(superCall).append(";\n");
            }
            s.append("        }\n");
            if (m.getReturnType() == void.class) {
                s.append("        ").append(call).append(";\n");
            } else {
                s.append("        return ").append(unbox(ret, "com.jxparallel.fx.Fx.fx(" + call + ")")).append(";\n");
            }
            s.append("    }\n");
        }
        s.append("\n    @Override\n    public Object callSuper(String method, Object[] a) {\n        try {\n        switch (method) {\n");
        for (Method m : hooks) {
            if (Modifier.isAbstract(m.getModifiers())) {
                continue;
            }
            s.append("            case \"").append(signature(m)).append("\":\n");
            StringJoiner args = new StringJoiner(", ");
            Type[] ps = m.getGenericParameterTypes();
            for (int i = 0; i < ps.length; i++) {
                args.add(unbox(resolve(ps[i], vars), "a[" + i + "]"));
            }
            String call = "super." + m.getName() + "(" + args + ")";
            if (m.getReturnType() == void.class) {
                s.append("                ").append(call).append(";\n                return null;\n");
            } else {
                s.append("                return ").append(call).append(";\n");
            }
        }
        s.append("            default:\n                throw new AbstractMethodError(method);\n        }\n")
                .append("        } catch (Exception e) {\n            throw com.jxparallel.fx.Fx.sneaky(e);\n        }\n    }\n}\n");
        return s.toString().replace("package com.jxparallel.fx.peer.;", "package com.jxparallel.fx.peer;");
    }

    /** Hook methods visible in c, most-derived declaration first. */
    private List<Method> hooksOf(Class<?> c) {
        Map<String, Type> vars = allVars(c);
        Map<String, Method> out = new LinkedHashMap<String, Method>() {
            @Override
            public Method putIfAbsent(String key, Method m) {
                return super.putIfAbsent(resolvedSignature(m, vars), m);
            }
        };
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (isHook(m) && isMapped(m.getDeclaringClass())
                        && (!Modifier.isAbstract(m.getModifiers()) || implementation(c, m) == null)) {
                    out.putIfAbsent(signature(m), m);
                }
            }
        }
        for (Method m : c.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers()) && isMapped(m.getDeclaringClass()) && !m.getDeclaringClass().isInterface()
                    && implementation(c, m) == null && !m.getName().startsWith("impl_")) {
                out.putIfAbsent(signature(m), m);
            }
        }
        for (Method m : c.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers()) && isMapped(m.getDeclaringClass()) && m.getDeclaringClass().isInterface()
                    && implementation(c, m) == null) {
                out.putIfAbsent(signature(m), m);
            }
        }
        return new ArrayList<>(out.values());
    }

    /** A JX declaration of m in a mapped superclass of c (generated there already). */
    private Method inheritedDeclaration(Class<?> c, Method m) {
        for (Class<?> k = c.getSuperclass(); k != null && isMapped(k); k = k.getSuperclass()) {
            for (Method d : declared(k)) {
                if (signature(d).equals(signature(m))) {
                    return d;
                }
            }
            for (Type i : k.getGenericInterfaces()) {
                if (isMapped(raw(i))) {
                    for (Method d : raw(i).getMethods()) {
                        if (signature(d).equals(signature(m)) && implementation(k, d) == null) {
                            return d;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Method implementation(Class<?> c, Method m) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method found = k.getDeclaredMethod(m.getName(), m.getParameterTypes());
                if (!Modifier.isAbstract(found.getModifiers())) {
                    return found;
                }
            } catch (NoSuchMethodException ignored) {
                // keep looking
            }
        }
        return null;
    }

    private boolean isHook(Method m) {
        int mod = m.getModifiers();
        if (m.isBridge() || m.isSynthetic() || Modifier.isStatic(mod) || Modifier.isFinal(mod) || Modifier.isPrivate(mod)
                || m.getName().startsWith("impl_")
                || !visible(m)) {
            return false;
        }
        return Modifier.isAbstract(mod) || HOOKS.contains(m.getName());
    }

    // ---------------------------------------------------------------- members

    /** Public and protected methods declared by c (no bridges, no impl_ internals). */
    private List<Method> declared(Class<?> c) {
        List<Method> out = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            int mod = m.getModifiers();
            if ((Modifier.isPublic(mod) || Modifier.isProtected(mod)) && !m.isSynthetic() && !m.isBridge()
                    && (!m.getName().startsWith("impl_") || plainImpl(m)) && !isObjectMethod(m) && visible(m)) {
                out.add(m);
            }
        }
        out.sort((a, b) -> signature(a).compareTo(signature(b)));
        return out;
    }

    /** Deprecated impl_ methods stay out, except public ones with simple signatures (Image.impl_getUrl()). */
    private boolean plainImpl(Method m) {
        if (!Modifier.isPublic(m.getModifiers()) || Modifier.isAbstract(m.getModifiers()) || !jxable(m.getGenericReturnType())) {
            return false;
        }
        for (Type p : m.getGenericParameterTypes()) {
            if (!jxable(p)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isObjectMethod(Method m) {
        try {
            Object.class.getMethod(m.getName(), m.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean functional(Class<?> c) {
        int n = 0;
        for (Method m : c.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers()) && !isObjectMethod(m)) {
                n++;
            }
        }
        return n == 1;
    }

    private static List<Constructor<?>> constructors(Class<?> c) {
        List<Constructor<?>> out = new ArrayList<>();
        if (c.isInterface() || c.isEnum()) {
            return out;
        }
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            int mod = k.getModifiers();
            if ((Modifier.isPublic(mod) || Modifier.isProtected(mod)) && !k.isSynthetic() && visible(k)) {
                out.add(k);
            }
        }
        return out;
    }

    private static String resolvedSignature(Method m, Map<String, Type> vars) {
        StringJoiner out = new StringJoiner(",", m.getName() + "(", ")");
        for (Type p : m.getGenericParameterTypes()) {
            out.add(raw(resolve(p, vars)).getSimpleName());
        }
        return out.toString();
    }

    static String signature(Method m) {
        StringJoiner out = new StringJoiner(",", m.getName() + "(", ")");
        for (Class<?> p : m.getParameterTypes()) {
            out.add(p.getSimpleName());
        }
        return out.toString();
    }

    private String params(Executable e, boolean jxTypes) {
        Type[] types = e.getGenericParameterTypes();
        Annotation[][] annotations = e.getParameterAnnotations();
        int offset = annotations.length - types.length;
        StringJoiner out = new StringJoiner(", ");
        for (int i = 0; i < types.length; i++) {
            StringBuilder p = new StringBuilder();
            for (Annotation a : annotations[i + Math.max(0, offset)]) {
                if (a instanceof NamedArg) {
                    NamedArg n = (NamedArg) a;
                    p.append("@javafx.beans.NamedArg(value = \"").append(n.value()).append('"');
                    if (!n.defaultValue().isEmpty()) {
                        p.append(", defaultValue = \"").append(literal(n.defaultValue())).append('"');
                    }
                    p.append(") ");
                }
            }
            Type type = resolve(types[i], env);
            String name = jxTypes ? jx(type) : fxType(type);
            if (e.isVarArgs() && i == types.length - 1) {
                name = name.substring(0, name.length() - 2) + "...";
            }
            out.add(p + name + " arg" + i);
        }
        return out.toString();
    }

    private String resolvedParams(Method m, Map<String, Type> vars) {
        Type[] types = m.getGenericParameterTypes();
        StringJoiner out = new StringJoiner(", ");
        for (int i = 0; i < types.length; i++) {
            String name = fxType(resolve(types[i], vars));
            if (m.isVarArgs() && i == types.length - 1) {
                name = name.substring(0, name.length() - 2) + "...";
            }
            out.add(name + " arg" + i);
        }
        return out.toString();
    }

    private static String argNames(Executable e) {
        StringJoiner out = new StringJoiner(", ");
        for (int i = 0; i < e.getParameterCount(); i++) {
            out.add("arg" + i);
        }
        return out.toString();
    }

    /** Arguments of e converted from JX to JavaFX. */
    private String fxArgs(Executable e) {
        StringJoiner out = new StringJoiner(", ");
        Type[] types = e.getGenericParameterTypes();
        Class<?>[] raws = e.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            Type type = resolve(types[i], env);
            out.add(toFx(raw(type), type, "arg" + i));
        }
        return out.toString();
    }

    /** a[i] cast to the JX parameter types of m, for $hook. */
    private String castArgs(Method m, Map<String, Type> vars) {
        StringJoiner out = new StringJoiner(", ");
        Class<?>[] raws = m.getParameterTypes();
        for (int i = 0; i < raws.length; i++) {
            Type t = resolve(m.getGenericParameterTypes()[i], vars);
            out.add(unboxJx(t, raws[i], "a[" + i + "]"));
        }
        return out.toString();
    }

    private String toFx(Class<?> raw, Type type, String expr) {
        if (raw.isPrimitive() || raw == String.class || isPlainJava(raw) || !jxable(type)) {
            return expr;
        }
        if (Map.class.isAssignableFrom(raw)) {
            return "(" + raw.getCanonicalName() + ") com.jxparallel.fx.Fx.fxMap(" + expr + ")";
        }
        if (isJavaFunctional(raw) && !jx(type).equals(fxType(type))) {
            return "com.jxparallel.fx.Fx.functional(" + expr + ", " + raw.getCanonicalName() + ".class, true)";
        }
        if (raw.isArray()) {
            Class<?> component = raw.getComponentType();
            if (component.isPrimitive() || !convertible(component)) {
                return expr;
            }
            return "(" + fxType(type) + ") com.jxparallel.fx.Fx.fxArray(" + expr + ", " + component.getCanonicalName() + ".class)";
        }
        if (Collection.class.isAssignableFrom(raw) && !isFx(raw)) {
            return "(" + raw.getCanonicalName() + ") com.jxparallel.fx.Fx.fxCollection(" + expr + ")";
        }
        if (!convertible(raw)) {
            return expr;
        }
        // An unbounded type variable keeps its name so generic JavaFX methods still infer; a bounded one
        // (C extends Control) differs between the JX and JavaFX declarations, so it is cast to the bound.
        String cast = type instanceof TypeVariable && raw == Object.class ? ((TypeVariable<?>) type).getName() : fxErasure(type, raw);
        return "(" + cast + ") com.jxparallel.fx.Fx.fx(" + expr + ")";
    }

    private String toJx(Type type, String expr, boolean fromObject) {
        Class<?> raw = raw(type);
        if (raw.isPrimitive()) {
            return fromObject ? "(" + box(raw) + ") " + expr : expr;
        }
        if (raw == String.class || isPlainJava(raw) || !jxable(type)) {
            return fromObject ? "(" + erasedJx(type) + ") " + expr : expr;
        }
        if (Map.class.isAssignableFrom(raw) && !isFx(raw)) {
            return "(" + raw.getCanonicalName() + ") com.jxparallel.fx.Fx.jxMap((java.util.Map) " + expr + ")";
        }
        if (raw.isArray()) {
            Class<?> component = raw.getComponentType();
            if (component.isPrimitive() || !convertible(component)) {
                return fromObject ? "(" + jx(type) + ") " + expr : expr;
            }
            return "com.jxparallel.fx.Fx.jxArray((Object[]) " + expr + ", " + jxErasure(component) + ".class)";
        }
        if (List.class.isAssignableFrom(raw) && !isFx(raw)) {
            return "(" + jxErasure(raw) + ") com.jxparallel.fx.Fx.jxList((java.util.List) " + expr + ")";
        }
        if (Collection.class.isAssignableFrom(raw) && !isFx(raw)) {
            return "(" + jxErasure(raw) + ") com.jxparallel.fx.Fx.jxCollection((java.util.Collection) " + expr + ")";
        }
        if (isJavaFunctional(raw) && !jx(type).equals(fxType(type))) {
            return "com.jxparallel.fx.Fx.functional(" + expr + ", " + raw.getCanonicalName() + ".class, false)";
        }
        if (raw == java.util.Iterator.class) {
            return "com.jxparallel.fx.Fx.jxIterator((java.util.Iterator) " + expr + ")";
        }
        if (!convertible(raw)) {
            return fromObject ? "(" + jx(type) + ") " + expr : expr;
        }
        return "(" + (type instanceof TypeVariable ? jx(type) : jxErasure(raw)) + ") com.jxparallel.fx.Fx.jx(" + expr + ")";
    }

    /** Whether values of this declared type may need conversion (JavaFX types we map, Object, type variables). */
    private boolean convertible(Class<?> raw) {
        return raw == Object.class || isMapped(raw);
    }

    /** java.util.function interfaces and Comparator: lambdas whose arguments may need conversion. */
    private static boolean isJavaFunctional(Class<?> raw) {
        return raw.isInterface() && (raw.getName().startsWith("java.util.function.") || raw == java.util.Comparator.class);
    }

    private static boolean isPlainJava(Class<?> raw) {
        String n = raw.getName();
        return n.startsWith("java.lang.") && raw != Object.class && !Iterable.class.isAssignableFrom(raw)
                || n.startsWith("java.net.") || n.startsWith("java.io.") || n.startsWith("java.time.")
                || n.startsWith("java.nio.") || n.startsWith("java.text.");
    }

    private String unbox(Type fxResolved, String expr) {
        Class<?> raw = raw(fxResolved);
        if (raw.isPrimitive()) {
            return "(" + box(raw) + ") " + expr;
        }
        return "(" + fxType(fxResolved) + ") " + expr;
    }

    private String unboxJx(Type type, Class<?> raw, String expr) {
        if (raw.isPrimitive()) {
            return "(" + box(raw) + ") " + expr;
        }
        return "(" + jxErasure(raw(type)) + ") " + expr;
    }

    private static String box(Class<?> p) {
        if (p == int.class) {
            return "Integer";
        }
        if (p == char.class) {
            return "Character";
        }
        String n = p.getName();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String throwsClause(Executable e) {
        if (e.getGenericExceptionTypes().length == 0) {
            return "";
        }
        StringJoiner out = new StringJoiner(", ", " throws ", "");
        for (Type t : e.getGenericExceptionTypes()) {
            out.add(fxType(t));
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- type names

    /** Whether t names only mapped JavaFX types (so it has a JX spelling and its values convert). */
    private boolean jxable(Type t) {
        if (t instanceof Class) {
            Class<?> c = (Class<?>) t;
            while (c.isArray()) {
                c = c.getComponentType();
            }
            return !(isFx(c) || c.getName().startsWith("com.sun.")) || isMapped(c);
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) t;
            if (!jxable(p.getRawType())) {
                return false;
            }
            for (Type a : p.getActualTypeArguments()) {
                if (!jxable(a)) {
                    return false;
                }
            }
            return true;
        }
        if (t instanceof WildcardType) {
            for (Type b : ((WildcardType) t).getUpperBounds()) {
                if (!jxable(b)) {
                    return false;
                }
            }
            for (Type b : ((WildcardType) t).getLowerBounds()) {
                if (!jxable(b)) {
                    return false;
                }
            }
            return true;
        }
        if (t instanceof GenericArrayType) {
            return jxable(((GenericArrayType) t).getGenericComponentType());
        }
        return true;
    }

    /** A type as JX code names it: mapped JavaFX types become com.jxparallel.fx types. */
    private String jx(Type t) {
        if (!jxable(t)) {
            return fxType(t);
        }
        if (t instanceof Class) {
            Class<?> c = (Class<?>) t;
            if (c.isArray()) {
                return jx(c.getComponentType()) + "[]";
            }
            return isMapped(c) ? jxErasure(c) : c.getCanonicalName();
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) t;
            Class<?> raw = raw(p);
            boolean translateArgs = isMapped(raw) || !isFx(raw);
            StringJoiner args = new StringJoiner(", ", "<", ">");
            for (Type a : p.getActualTypeArguments()) {
                args.add(translateArgs ? jx(a) : fxType(a));
            }
            return jx(raw) + args;
        }
        if (t instanceof WildcardType) {
            WildcardType w = (WildcardType) t;
            if (w.getLowerBounds().length > 0) {
                return "? super " + jx(w.getLowerBounds()[0]);
            }
            Type upper = w.getUpperBounds()[0];
            return upper == Object.class ? "?" : "? extends " + jx(upper);
        }
        if (t instanceof GenericArrayType) {
            return jx(((GenericArrayType) t).getGenericComponentType()) + "[]";
        }
        return ((TypeVariable<?>) t).getName();
    }

    /** The erasure of t as JX names it (used in casts, where generics only produce warnings). */
    private String erasedJx(Type t) {
        return t instanceof TypeVariable ? ((TypeVariable<?>) t).getName() : jxable(t) ? jxErasure(raw(t)) : raw(t).getCanonicalName();
    }

    private static String fxType(Type t) {
        if (t instanceof Class) {
            return ((Class<?>) t).getCanonicalName();
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) t;
            StringJoiner args = new StringJoiner(", ", "<", ">");
            for (Type a : p.getActualTypeArguments()) {
                args.add(fxType(a));
            }
            return raw(p).getCanonicalName() + args;
        }
        if (t instanceof WildcardType) {
            WildcardType w = (WildcardType) t;
            if (w.getLowerBounds().length > 0) {
                return "? super " + fxType(w.getLowerBounds()[0]);
            }
            Type upper = w.getUpperBounds()[0];
            return upper == Object.class ? "?" : "? extends " + fxType(upper);
        }
        if (t instanceof GenericArrayType) {
            return fxType(((GenericArrayType) t).getGenericComponentType()) + "[]";
        }
        return ((TypeVariable<?>) t).getName();
    }

    private String jxErasure(Class<?> c) {
        if (c.isArray()) {
            return jxErasure(c.getComponentType()) + "[]";
        }
        if (!isMapped(c)) {
            return c.getCanonicalName();
        }
        return JX + rest(c.getName()).replace('$', '.');
    }

    private static String fxErasure(Type type, Class<?> raw) {
        return raw.getCanonicalName();
    }

    private static String fxName(Class<?> c) {
        return c.getCanonicalName();
    }

    private String typeParams(TypeVariable<?>[] vars) {
        return typeParams(vars, false);
    }

    private String typeParams(TypeVariable<?>[] vars, boolean fxBounds) {
        if (vars.length == 0) {
            return "";
        }
        StringJoiner out = new StringJoiner(", ", "<", ">");
        for (TypeVariable<?> v : vars) {
            StringJoiner bounds = new StringJoiner(" & ");
            for (Type b : v.getBounds()) {
                if (b != Object.class) {
                    bounds.add(fxBounds ? fxType(b) : jx(b));
                }
            }
            out.add(v.getName() + (bounds.length() == 0 ? "" : " extends " + bounds));
        }
        return out.toString();
    }

    private static String typeArgs(Class<?> c) {
        StringJoiner out = new StringJoiner(", ", "<", ">");
        for (TypeVariable<?> v : c.getTypeParameters()) {
            out.add(v.getName());
        }
        return out.toString();
    }

    private static String diamond(Class<?> c) {
        return c.getTypeParameters().length > 0 ? "<>" : "";
    }

    private static Class<?> raw(Type t) {
        if (t instanceof Class) {
            return (Class<?>) t;
        }
        if (t instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) t).getRawType();
        }
        if (t instanceof GenericArrayType) {
            return java.lang.reflect.Array.newInstance(raw(((GenericArrayType) t).getGenericComponentType()), 0).getClass();
        }
        if (t instanceof TypeVariable) {
            Type[] bounds = ((TypeVariable<?>) t).getBounds();
            return raw(bounds[0]);
        }
        return Object.class;
    }

    /** Type variables of c's superclasses, resolved to c's own type expressions. */
    private static Map<String, Type> typeVars(Class<?> c) {
        Map<String, Type> vars = new HashMap<>();
        Class<?> k = c;
        Map<TypeVariable<?>, Type> env = new HashMap<>();
        for (TypeVariable<?> v : c.getTypeParameters()) {
            env.put(v, v);
        }
        while (k != null && k.getGenericSuperclass() instanceof Type) {
            Type sup = k.getGenericSuperclass();
            if (sup instanceof ParameterizedType) {
                ParameterizedType p = (ParameterizedType) sup;
                TypeVariable<?>[] params = ((Class<?>) p.getRawType()).getTypeParameters();
                Map<TypeVariable<?>, Type> next = new HashMap<>();
                for (int i = 0; i < params.length; i++) {
                    next.put(params[i], substitute(p.getActualTypeArguments()[i], env));
                }
                env.putAll(next);
            }
            k = k.getSuperclass();
        }
        for (Map.Entry<TypeVariable<?>, Type> e : env.entrySet()) {
            vars.put(e.getKey().getGenericDeclaration() + "#" + e.getKey().getName(), e.getValue());
        }
        return vars;
    }

    /** Every type variable of c's supertypes (classes and interfaces) as c sees it. */
    private static Map<String, Type> allVars(Class<?> c) {
        Map<String, Type> vars = typeVars(c);
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Map.Entry<String, Type> e : interfaceVars(k).entrySet()) {
                vars.putIfAbsent(e.getKey(), resolve(e.getValue(), vars));
            }
        }
        return vars;
    }

    /** Type parameters of c's interfaces (transitively) mapped to what c passes them. */
    private static Map<String, Type> interfaceVars(Class<?> c) {
        Map<String, Type> vars = new HashMap<>();
        List<Type> queue = new ArrayList<>(Arrays.asList(c.getGenericInterfaces()));
        while (!queue.isEmpty()) {
            Type t = queue.remove(0);
            Class<?> r = raw(t);
            if (t instanceof ParameterizedType) {
                TypeVariable<?>[] params = r.getTypeParameters();
                Type[] args = ((ParameterizedType) t).getActualTypeArguments();
                for (int i = 0; i < params.length; i++) {
                    vars.put(params[i].getGenericDeclaration() + "#" + params[i].getName(), resolve(args[i], vars));
                }
            }
            for (Type sup : r.getGenericInterfaces()) {
                queue.add(resolve(sup, vars));
            }
        }
        return vars;
    }

    private static Type substitute(Type t, Map<TypeVariable<?>, Type> env) {
        if (t instanceof TypeVariable && env.containsKey(t)) {
            return env.get(t);
        }
        return t;
    }

    private static Type resolve(Type t, Map<String, Type> vars) {
        if (t instanceof TypeVariable) {
            TypeVariable<?> v = (TypeVariable<?>) t;
            Type r = vars.get(v.getGenericDeclaration() + "#" + v.getName());
            return r == null ? t : r;
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) t;
            Type[] args = p.getActualTypeArguments().clone();
            boolean changed = false;
            for (int i = 0; i < args.length; i++) {
                Type r = resolve(args[i], vars);
                changed |= r != args[i];
                args[i] = r;
            }
            if (!changed) {
                return t;
            }
            Type[] finalArgs = args;
            return new ParameterizedType() {
                public Type[] getActualTypeArguments() {
                    return finalArgs;
                }

                public Type getRawType() {
                    return p.getRawType();
                }

                public Type getOwnerType() {
                    return p.getOwnerType();
                }
            };
        }
        if (t instanceof WildcardType) {
            WildcardType w = (WildcardType) t;
            Type[] upper = w.getUpperBounds().clone();
            Type[] lower = w.getLowerBounds().clone();
            for (int i = 0; i < upper.length; i++) {
                upper[i] = resolve(upper[i], vars);
            }
            for (int i = 0; i < lower.length; i++) {
                lower[i] = resolve(lower[i], vars);
            }
            return new WildcardType() {
                public Type[] getUpperBounds() {
                    return upper;
                }

                public Type[] getLowerBounds() {
                    return lower;
                }
            };
        }
        return t;
    }

    // ---------------------------------------------------------------- io

    /** The name below com.jxparallel.fx of a mapped class: javafx.a.B is a.B, org.controlsfx.a.B is controlsfx.a.B. */
    private static String rest(String name) {
        if (name.startsWith("org.controlsfx.")) {
            return "controlsfx." + name.substring("org.controlsfx.".length());
        }
        return name.startsWith("javafx.") ? name.substring("javafx.".length()) : name;
    }

    private static String literal(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<String> lines(String file) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty() && !line.startsWith("#")) {
                out.add(line.trim());
            }
        }
        return out;
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, GenerateFxApi.class.getClassLoader());
    }

    private static List<Class<?>> packageClasses(String pkg) throws IOException, ClassNotFoundException {
        List<Class<?>> out = new ArrayList<>();
        String jfxrt = System.getProperty("java.home") + "/lib/ext/jfxrt.jar";
        try (JarFile jar = new JarFile(jfxrt)) {
            String prefix = pkg.replace('.', '/') + "/";
            for (java.util.Enumeration<java.util.jar.JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
                String n = e.nextElement().getName();
                if (n.startsWith(prefix) && n.endsWith(".class") && !n.contains("$") && n.indexOf('/', prefix.length()) < 0) {
                    out.add(load(n.substring(0, n.length() - 6).replace('/', '.')));
                }
            }
        }
        return out;
    }

    private void write(String className, String source) throws IOException {
        File file = new File(out, className.replace('.', File.separatorChar) + ".java");
        file.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(file, "UTF-8")) {
            w.print(source);
        }
    }
}
