package io.github.bacuriim.jxforge.refs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.bacuriim.jxforge.JxmlFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What a {@code .jxml} file means to Java: its controller, the loop variables in scope, and where a binding path leads. */
public final class JxmlModel {
    private JxmlModel() {
    }

    public static boolean isJxml(@Nullable PsiFile file) {
        return file != null && file.getFileType() == JxmlFileType.INSTANCE;
    }

    // ---- controller -------------------------------------------------------------------------------------------

    public static @Nullable String controllerName(@NotNull XmlFile file) {
        XmlTag root = file.getRootTag();
        String name = root == null ? null : root.getAttributeValue("controller");
        return name == null || name.isBlank() ? null : name.trim();
    }

    public static @Nullable PsiClass findClass(@NotNull String name, @NotNull PsiElement context) {
        Project project = context.getProject();
        GlobalSearchScope scope = context.getResolveScope();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass found = facade.findClass(name, scope);
        if (found == null) {
            found = facade.findClass(name, GlobalSearchScope.allScope(project));
        }
        return found;
    }

    /**
     * The controller class of a file: the root {@code controller}, or for a part without one the controller of a file
     * that includes it.
     */
    public static @Nullable PsiClass controller(@NotNull XmlFile file) {
        return controller(file, 0);
    }

    private static @Nullable PsiClass controller(XmlFile file, int depth) {
        String name = controllerName(file);
        if (name != null) {
            return findClass(name, file);
        }
        if (depth > 5) {
            return null;
        }
        for (XmlFile includer : includers(file)) {
            PsiClass found = controller(includer, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The JXML files whose {@code <Include src>} names {@code part}. */
    private static List<XmlFile> includers(XmlFile part) {
        return CachedValuesManager.getCachedValue(part, () -> {
            List<XmlFile> result = new ArrayList<>();
            Project project = part.getProject();
            com.intellij.openapi.vfs.VirtualFile partFile = part.getVirtualFile();
            if (partFile != null) {
                for (com.intellij.openapi.vfs.VirtualFile vf : com.intellij.psi.search.FileTypeIndex.getFiles(JxmlFileType.INSTANCE,
                        GlobalSearchScope.projectScope(project))) {
                    PsiFile psi = PsiManager.getInstance(project).findFile(vf);
                    if (psi instanceof XmlFile && psi != part && includes((XmlFile) psi, partFile)) {
                        result.add((XmlFile) psi);
                    }
                }
            }
            return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    private static boolean includes(XmlFile file, com.intellij.openapi.vfs.VirtualFile part) {
        XmlTag root = file.getRootTag();
        if (root == null || !file.getText().contains("Include")) {
            return false;
        }
        for (XmlTag include : com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(root, XmlTag.class)) {
            if ("Include".equals(include.getName())) {
                PsiFile target = resolveInclude(include);
                if (target != null && part.equals(target.getVirtualFile())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The file an {@code <Include src="...">} names: a path relative to the folder of the including file. */
    public static @Nullable PsiFile resolveInclude(@NotNull XmlTag include) {
        String src = include.getAttributeValue("src");
        PsiFile file = include.getContainingFile().getOriginalFile();
        if (src == null || src.isBlank() || file.getVirtualFile() == null || file.getVirtualFile().getParent() == null) {
            return null;
        }
        com.intellij.openapi.vfs.VirtualFile target = file.getVirtualFile().getParent().findFileByRelativePath(src.trim());
        return target == null ? null : PsiManager.getInstance(file.getProject()).findFile(target);
    }

    // ---- loop variables ---------------------------------------------------------------------------------------

    /** A loop variable: the tag that declares it ({@code For}, {@code ListView}, {@code TableView}) and its {@code var} attribute. */
    public static final class LoopVar {
        public final XmlTag tag;
        public final XmlAttribute declaration;

        LoopVar(XmlTag tag, XmlAttribute declaration) {
            this.tag = tag;
            this.declaration = declaration;
        }
    }

    /** The loop variable {@code name} visible from {@code attribute}: the nearest enclosing tag that declares it. */
    public static @Nullable LoopVar findVar(@NotNull XmlAttribute attribute, @NotNull String name) {
        XmlTag tag = attribute.getParent();
        boolean ownItems = "items".equals(attribute.getName());
        for (XmlTag t = tag; t != null; t = t.getParentTag()) {
            if (t == tag && ownItems) {
                continue; // the items of a loop are read outside of it
            }
            XmlAttribute var = t.getAttribute("var");
            if (var != null && name.equals(var.getValue())) {
                return new LoopVar(t, var);
            }
        }
        return null;
    }

    /** Names of the loop variables visible from {@code attribute}, nearest first. */
    public static List<String> varsInScope(@NotNull XmlAttribute attribute) {
        List<String> names = new ArrayList<>();
        XmlTag tag = attribute.getParent();
        boolean ownItems = "items".equals(attribute.getName());
        for (XmlTag t = tag; t != null; t = t.getParentTag()) {
            if (t == tag && ownItems) {
                continue;
            }
            String var = t.getAttributeValue("var");
            if (var != null && !var.isBlank() && !names.contains(var)) {
                names.add(var);
            }
        }
        return names;
    }

    /** The class of the items of a loop: the {@code type} attribute, or what {@code items} yields. */
    public static @Nullable PsiType loopVarType(@NotNull LoopVar var) {
        XmlAttribute type = var.tag.getAttribute("type");
        if (type != null && type.getValue() != null && !type.getValue().isBlank()) {
            PsiClass cls = findTypeClass(type.getValue().trim(), var.tag);
            return cls == null ? null : PsiTypesUtil.getClassType(cls);
        }
        XmlAttribute items = var.tag.getAttribute("items");
        if (items == null) {
            return null;
        }
        JxmlValue value = JxmlValue.parse(items);
        if (value == null || value.kind == JxmlValue.Kind.HANDLER) {
            return null;
        }
        Chain chain = resolve(items, value);
        return elementType(chain.resultType());
    }

    /** A class named in {@code type}: fully qualified, or relative to the controller's package or the controller class. */
    public static @Nullable PsiClass findTypeClass(@NotNull String name, @NotNull PsiElement context) {
        PsiClass direct = findClass(name, context);
        if (direct != null) {
            return direct;
        }
        PsiFile file = context.getContainingFile();
        PsiClass controller = file instanceof XmlFile ? controller((XmlFile) file) : null;
        if (controller != null) {
            PsiClass inner = controller.findInnerClassByName(name, true);
            if (inner != null) {
                return inner;
            }
            if (controller.getQualifiedName() != null) {
                String pkg = ((PsiJavaFile) controller.getContainingFile()).getPackageName();
                PsiClass samePackage = findClass(pkg.isEmpty() ? name : pkg + "." + name, context);
                if (samePackage != null) {
                    return samePackage;
                }
                PsiClass nested = findClass(controller.getQualifiedName() + "." + name, context);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    static @Nullable PsiType elementType(@Nullable PsiType type) {
        if (type == null) {
            return null;
        }
        if (type instanceof PsiArrayType) {
            return ((PsiArrayType) type).getComponentType();
        }
        PsiType item = PsiUtil.extractIterableTypeParameter(type, false);
        if (item instanceof PsiWildcardType) {
            PsiWildcardType wildcard = (PsiWildcardType) item;
            return wildcard.isExtends() ? wildcard.getExtendsBound() : null;
        }
        return item;
    }

    // ---- property paths ---------------------------------------------------------------------------------------

    public enum Status {
        /** Resolved. */
        OK,
        /** Resolved, but not the way the JXML runtime calls it ({@code isX()}, or {@code x()} written without parentheses). */
        WEAK,
        /** The owner type is known and has no such property. */
        UNRESOLVED,
        /** The owner type is not known (no controller, no {@code type} on a loop), so nothing can be said. */
        UNKNOWN
    }

    /** One segment of a path. */
    public static final class Step {
        public final JxmlValue.Seg seg;
        public final @Nullable PsiElement target;
        public final @Nullable PsiClass owner;
        public final @Nullable PsiType type;
        public final Status status;
        public final boolean loopVar;

        Step(JxmlValue.Seg seg, @Nullable PsiElement target, @Nullable PsiClass owner, @Nullable PsiType type, Status status,
             boolean loopVar) {
            this.seg = seg;
            this.target = target;
            this.owner = owner;
            this.type = type;
            this.status = status;
            this.loopVar = loopVar;
        }
    }

    /** Where a path leads, segment by segment. */
    public static final class Chain {
        public final List<Step> steps = new ArrayList<>();

        public @Nullable PsiType resultType() {
            return steps.isEmpty() ? null : steps.get(steps.size() - 1).type;
        }
    }

    public static @NotNull Chain resolve(@NotNull XmlAttribute attribute, @NotNull JxmlValue value) {
        Chain chain = new Chain();
        PsiFile file = attribute.getContainingFile();
        PsiType current = null;
        boolean known = false;
        int from = 0;
        if (!value.segments.isEmpty()) {
            JxmlValue.Seg first = value.segments.get(0);
            LoopVar var = first.isCall() ? null : findVar(attribute, first.text);
            if (var != null) {
                PsiType type = loopVarType(var);
                chain.steps.add(new Step(first, var.declaration.getValueElement(), null, type, Status.OK, true));
                current = type;
                known = type != null;
                from = 1;
            } else {
                PsiClass controller = file instanceof XmlFile ? controller((XmlFile) file) : null;
                if (controller != null) {
                    current = PsiTypesUtil.getClassType(controller);
                    known = true;
                }
            }
        }
        for (int i = from; i < value.segments.size(); i++) {
            JxmlValue.Seg seg = value.segments.get(i);
            PsiClass owner = known && current != null ? PsiUtil.resolveClassInClassTypeOnly(current) : null;
            if (owner == null) {
                chain.steps.add(new Step(seg, null, null, null, Status.UNKNOWN, false));
                known = false;
                current = null;
                continue;
            }
            Found found = findGetter(owner, seg);
            if (found == null) {
                chain.steps.add(new Step(seg, null, owner, null, seg.text.isEmpty() ? Status.UNKNOWN : Status.UNRESOLVED, false));
                known = false;
                current = null;
                continue;
            }
            PsiType returned = substitute(found.method, current);
            chain.steps.add(new Step(seg, found.method, owner, returned, found.weak ? Status.WEAK : Status.OK, false));
            current = returned;
            known = returned != null;
        }
        return chain;
    }

    private static @Nullable PsiType substitute(PsiMethod method, @Nullable PsiType ownerType) {
        PsiType returned = method.getReturnType();
        if (returned == null || !(ownerType instanceof PsiClassType) || method.getContainingClass() == null) {
            return returned;
        }
        PsiClassType.ClassResolveResult resolved = ((PsiClassType) ownerType).resolveGenerics();
        PsiClass cls = resolved.getElement();
        if (cls == null) {
            return returned;
        }
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(method.getContainingClass(), cls,
                resolved.getSubstitutor());
        PsiType substituted = substitutor.substitute(returned);
        return substituted == null ? returned : substituted;
    }

    public static final class Found {
        public final PsiMethod method;
        public final boolean weak;

        Found(PsiMethod method, boolean weak) {
            this.method = method;
            this.weak = weak;
        }
    }

    /** The getter a segment calls: {@code getX()}, else (weakly) {@code isX()} or {@code x()}; {@code x()} as written. */
    public static @Nullable Found findGetter(@NotNull PsiClass cls, @NotNull JxmlValue.Seg seg) {
        String name = seg.name();
        if (name.isEmpty()) {
            return null;
        }
        if (seg.isCall()) {
            PsiMethod m = publicMethod(cls, name, 0);
            return m == null ? null : new Found(m, false);
        }
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        PsiMethod m = publicMethod(cls, "get" + cap, 0);
        if (m != null) {
            return new Found(m, false);
        }
        m = publicMethod(cls, "is" + cap, 0);
        if (m != null) {
            return new Found(m, true);
        }
        m = publicMethod(cls, name, 0);
        return m == null ? null : new Found(m, true);
    }

    public static @Nullable PsiMethod findSetter(@NotNull PsiClass cls, @NotNull String property) {
        if (property.isEmpty()) {
            return null;
        }
        return publicMethod(cls, "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1), 1);
    }

    /** A public, non-void-or-void method with {@code arity} parameters (any arity when negative). */
    public static @Nullable PsiMethod publicMethod(PsiClass cls, String name, int arity) {
        for (PsiMethod m : cls.findMethodsByName(name, true)) {
            if (m.hasModifierProperty(PsiModifier.PUBLIC) && !m.isConstructor()
                    && (arity < 0 || m.getParameterList().getParametersCount() == arity)) {
                return m;
            }
        }
        return null;
    }

    /** Public methods named {@code name}, any arity. */
    public static List<PsiMethod> publicMethods(PsiClass cls, String name) {
        List<PsiMethod> result = new ArrayList<>();
        for (PsiMethod m : cls.findMethodsByName(name, true)) {
            if (m.hasModifierProperty(PsiModifier.PUBLIC) && !m.isConstructor()) {
                result.add(m);
            }
        }
        return result;
    }

    /** Property names of a class: {@code getX}/{@code isX} give {@code x}; a record's components give their names. */
    public static Map<String, PsiMethod> properties(@NotNull PsiClass cls) {
        Map<String, PsiMethod> result = new LinkedHashMap<>();
        for (PsiMethod m : cls.getAllMethods()) {
            if (!m.hasModifierProperty(PsiModifier.PUBLIC) || m.isConstructor() || m.getParameterList().getParametersCount() != 0
                    || PsiTypes.voidType().equals(m.getReturnType())) {
                continue;
            }
            PsiClass owner = m.getContainingClass();
            if (owner != null && "java.lang.Object".equals(owner.getQualifiedName())) {
                continue;
            }
            String n = m.getName();
            String property = null;
            if (n.length() > 3 && n.startsWith("get") && Character.isUpperCase(n.charAt(3))) {
                property = decapitalize(n.substring(3));
            } else if (n.length() > 2 && n.startsWith("is") && Character.isUpperCase(n.charAt(2))) {
                property = decapitalize(n.substring(2));
            } else if (cls.isRecord() && cls.findFieldByName(n, false) != null) {
                property = n;
            }
            if (property != null) {
                result.putIfAbsent(property, m);
            }
        }
        return result;
    }

    public static String decapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** The property a method stands for in a binding: {@code getX} to {@code x}, otherwise its own name. */
    public static String propertyOf(String methodName) {
        if (methodName.length() > 3 && methodName.startsWith("get") && Character.isUpperCase(methodName.charAt(3))) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.length() > 2 && methodName.startsWith("is") && Character.isUpperCase(methodName.charAt(2))) {
            return decapitalize(methodName.substring(2));
        }
        return methodName;
    }

    /** The attribute of a value element, when it is one. */
    public static @Nullable XmlAttribute attributeOf(PsiElement valueElement) {
        return valueElement instanceof XmlAttributeValue && valueElement.getParent() instanceof XmlAttribute
                ? (XmlAttribute) valueElement.getParent() : null;
    }
}
