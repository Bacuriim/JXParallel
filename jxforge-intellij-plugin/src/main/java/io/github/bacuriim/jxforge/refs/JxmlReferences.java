package io.github.bacuriim.jxforge.refs;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The references a JXML attribute value holds toward Java: handlers, binding segments and loop variables. */
public final class JxmlReferences {
    private JxmlReferences() {
    }

    /** Offset of the value text inside the {@link XmlAttributeValue} element (after the opening quote). */
    static int valueOffset(XmlAttributeValue element) {
        return element.getValueTextRange().getStartOffset() - element.getTextRange().getStartOffset();
    }

    public static PsiReference[] forValue(XmlAttributeValue element) {
        XmlAttribute attribute = JxmlModel.attributeOf(element);
        if (attribute == null) {
            return PsiReference.EMPTY_ARRAY;
        }
        JxmlValue value = JxmlValue.parse(attribute.getName(), attribute.getValue() == null ? "" : attribute.getValue());
        if (value == null) {
            return PsiReference.EMPTY_ARRAY;
        }
        int base = valueOffset(element);
        List<PsiReference> refs = new ArrayList<>();
        if (value.kind == JxmlValue.Kind.HANDLER) {
            JxmlValue.Seg name = value.handler;
            if (name != null) {
                refs.add(new MethodRef(element, new TextRange(base + name.start, base + name.end), name.text));
            }
            for (JxmlValue.Seg arg : value.args) {
                refs.add(new VariableRef(element, new TextRange(base + arg.start, base + arg.end), arg.text));
            }
        } else {
            for (int i = 0; i < value.segments.size(); i++) {
                JxmlValue.Seg seg = value.segments.get(i);
                refs.add(new BindingRef(element, new TextRange(base + seg.start, base + seg.end), i, value.kind));
            }
        }
        return refs.toArray(PsiReference.EMPTY_ARRAY);
    }

    // ---- #handler --------------------------------------------------------------------------------------------

    /** {@code #name}: the public methods of the controller with that name. */
    public static final class MethodRef extends PsiReferenceBase.Poly<XmlAttributeValue> {
        private final String name;

        MethodRef(XmlAttributeValue element, TextRange range, String name) {
            super(element, range, false);
            this.name = name;
        }

        public String name() {
            return name;
        }

        public @Nullable PsiClass controller() {
            PsiFile file = getElement().getContainingFile();
            return file instanceof XmlFile ? JxmlModel.controller((XmlFile) file) : null;
        }

        @Override
        public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
            PsiClass controller = controller();
            if (controller == null || name.isEmpty()) {
                return ResolveResult.EMPTY_ARRAY;
            }
            List<ResolveResult> results = new ArrayList<>();
            for (PsiMethod m : JxmlModel.publicMethods(controller, name)) {
                results.add(new PsiElementResolveResult(m));
            }
            return results.toArray(ResolveResult.EMPTY_ARRAY);
        }

        @Override
        public boolean isReferenceTo(@NotNull PsiElement element) {
            for (ResolveResult r : multiResolve(false)) {
                if (getElement().getManager().areElementsEquivalent(r.getElement(), element)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object @NotNull [] getVariants() {
            PsiClass controller = controller();
            if (controller == null) {
                return new Object[0];
            }
            List<LookupElement> variants = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (PsiMethod m : controller.getAllMethods()) {
                PsiClass owner = m.getContainingClass();
                if (!m.hasModifierProperty(PsiModifier.PUBLIC) || m.isConstructor() || m.hasModifierProperty(PsiModifier.STATIC)
                        || owner != null && "java.lang.Object".equals(owner.getQualifiedName()) || !seen.add(m.getName())) {
                    continue;
                }
                variants.add(LookupElementBuilder.createWithIcon(m).withTailText(
                        "(" + paramNames(m) + ")", true).withTypeText(owner == null ? "" : String.valueOf(owner.getName()), true));
            }
            return variants.toArray();
        }

        @Override
        public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
            return replace(newElementName);
        }

        private PsiElement replace(String text) {
            return ElementManipulators.handleContentChange(getElement(), getRangeInElement(), text);
        }

        @Override
        public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
            return element instanceof PsiMethod ? replace(((PsiMethod) element).getName()) : getElement();
        }
    }

    static String paramNames(PsiMethod m) {
        StringBuilder sb = new StringBuilder();
        for (PsiParameter p : m.getParameterList().getParameters()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p.getType().getPresentableText());
        }
        return sb.toString();
    }

    // ---- ${a.b.c} and @{x} -----------------------------------------------------------------------------------

    /** One segment of a binding path. */
    public static final class BindingRef extends PsiReferenceBase<XmlAttributeValue> {
        private final int index;
        private final JxmlValue.Kind kind;

        BindingRef(XmlAttributeValue element, TextRange range, int index, JxmlValue.Kind kind) {
            super(element, range, true);
            this.index = index;
            this.kind = kind;
        }

        public int index() {
            return index;
        }

        public @Nullable JxmlModel.Step step() {
            XmlAttribute attribute = JxmlModel.attributeOf(getElement());
            if (attribute == null) {
                return null;
            }
            JxmlValue value = JxmlValue.parse(attribute.getName(), attribute.getValue() == null ? "" : attribute.getValue());
            if (value == null) {
                return null;
            }
            JxmlModel.Chain chain = JxmlModel.resolve(attribute, value);
            return index < chain.steps.size() ? chain.steps.get(index) : null;
        }

        @Override
        public @Nullable PsiElement resolve() {
            JxmlModel.Step step = step();
            return step == null ? null : step.target;
        }

        @Override
        public Object @NotNull [] getVariants() {
            XmlAttribute attribute = JxmlModel.attributeOf(getElement());
            if (attribute == null) {
                return new Object[0];
            }
            JxmlValue value = JxmlValue.parse(attribute.getName(), attribute.getValue() == null ? "" : attribute.getValue());
            if (value == null) {
                return new Object[0];
            }
            List<LookupElement> variants = new ArrayList<>();
            PsiClass owner;
            if (index == 0) {
                for (String var : JxmlModel.varsInScope(attribute)) {
                    variants.add(LookupElementBuilder.create(var).withTypeText("loop variable", true));
                }
                PsiFile file = attribute.getContainingFile();
                owner = file instanceof XmlFile ? JxmlModel.controller((XmlFile) file) : null;
            } else {
                JxmlModel.Chain chain = JxmlModel.resolve(attribute, value);
                JxmlModel.Step previous = chain.steps.get(index - 1);
                owner = previous.type == null ? null : com.intellij.psi.util.PsiUtil.resolveClassInClassTypeOnly(previous.type);
            }
            if (owner != null) {
                for (Map.Entry<String, PsiMethod> e : JxmlModel.properties(owner).entrySet()) {
                    PsiType type = e.getValue().getReturnType();
                    variants.add(LookupElementBuilder.create(e.getKey())
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Property)
                            .withTypeText(type == null ? "" : type.getPresentableText(), true));
                }
            }
            return variants.toArray();
        }

        @Override
        public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
            JxmlModel.Step step = step();
            String text = newElementName;
            if (step != null && step.target instanceof PsiMethod) {
                // a method renamed to getTitle is written ${title}; a record accessor or x() keeps the call form
                text = step.seg.isCall() ? newElementName + "()" : JxmlModel.propertyOf(newElementName);
                if (!step.seg.isCall() && ((PsiMethod) step.target).getName().equals(step.seg.text)) {
                    text = newElementName; // the segment was written as the bare method name
                }
            }
            return ElementManipulators.handleContentChange(getElement(), getRangeInElement(), text);
        }

        @Override
        public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
            return getElement();
        }

        @Override
        public boolean isReferenceTo(@NotNull PsiElement element) {
            PsiElement target = resolve();
            return target != null && getElement().getManager().areElementsEquivalent(target, element);
        }
    }

    // ---- #open(item): a loop variable --------------------------------------------------------------------------

    /** An argument of a handler: the loop variable it names. */
    public static final class VariableRef extends PsiReferenceBase<XmlAttributeValue> {
        private final String name;

        VariableRef(XmlAttributeValue element, TextRange range, String name) {
            super(element, range, true);
            this.name = name;
        }

        @Override
        public @Nullable PsiElement resolve() {
            XmlAttribute attribute = JxmlModel.attributeOf(getElement());
            JxmlModel.LoopVar var = attribute == null ? null : JxmlModel.findVar(attribute, name);
            return var == null ? null : var.declaration.getValueElement();
        }

        @Override
        public Object @NotNull [] getVariants() {
            XmlAttribute attribute = JxmlModel.attributeOf(getElement());
            return attribute == null ? new Object[0] : JxmlModel.varsInScope(attribute).toArray();
        }
    }
}
