package io.github.bacuriim.jxforge;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import io.github.bacuriim.jxforge.refs.JxmlModel;
import io.github.bacuriim.jxforge.refs.JxmlValue;
import org.jetbrains.annotations.NotNull;


/**
 * Colors {@code ${...}}, {@code @{...}} and {@code #handler} values, and reports what does not resolve against the
 * controller: an unknown handler (error, with a quick fix), an unknown property (warning), {@code @{x}} without a setter (error).
 */
public final class JxmlAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlAttributeValue) || !(element.getParent() instanceof XmlAttribute)) {
            return;
        }
        PsiFile file = element.getContainingFile();
        if (!JxmlModel.isJxml(file) || !(file instanceof XmlFile)) {
            return;
        }
        XmlAttribute attribute = (XmlAttribute) element.getParent();
        XmlAttributeValue valueElement = (XmlAttributeValue) element;
        String text = attribute.getValue();
        checkTargets(attribute, valueElement, text, (XmlFile) file, holder);
        JxmlValue value = text == null ? null : JxmlValue.parse(attribute.getName(), text);
        if (value == null) {
            return;
        }
        int base = valueElement.getValueTextRange().getStartOffset();
        TextRange whole = new TextRange(base, valueElement.getValueTextRange().getEndOffset());
        if (whole.isEmpty()) {
            return;
        }
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(whole).textAttributes(switch (value.kind) {
            case ONE_WAY -> JxmlHighlightKeys.BINDING;
            case TWO_WAY -> JxmlHighlightKeys.TWO_WAY_BINDING;
            case HANDLER -> JxmlHighlightKeys.HANDLER;
        }).create();

        if (value.kind == JxmlValue.Kind.HANDLER) {
            checkHandler(attribute, value, base, (XmlFile) file, holder);
        } else {
            checkBinding(attribute, value, base, holder);
        }
    }

    /** The controller class and the included file must exist; the platform does not report unresolved references in attribute values. */
    private static void checkTargets(XmlAttribute attribute, XmlAttributeValue valueElement, String text, XmlFile file, AnnotationHolder holder) {
        com.intellij.psi.xml.XmlTag tag = attribute.getParent();
        if (text == null || text.isBlank() || tag == null) {
            return;
        }
        TextRange range = valueElement.getValueTextRange();
        if ("controller".equals(attribute.getName()) && tag.getParentTag() == null) {
            if (JxmlModel.findClass(text.trim(), file) == null) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve class '" + text.trim() + "'").range(range).create();
            }
        } else if ("src".equals(attribute.getName()) && "Include".equals(tag.getName()) && JxmlModel.resolveInclude(tag) == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve file '" + text.trim() + "'").range(range).create();
        }
    }

    private static void checkHandler(XmlAttribute attribute, JxmlValue value, int base, XmlFile file, AnnotationHolder holder) {
        JxmlValue.Seg name = value.handler;
        PsiClass controller = JxmlModel.controller(file);
        if (name == null || name.text.isEmpty() || controller == null) {
            return;
        }
        if (JxmlModel.publicMethods(controller, name.text).isEmpty()) {
            TextRange range = new TextRange(base + name.start, base + name.end);
            holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve method '" + name.text + "' in '" + controller.getName() + "'")
                    .range(range)
                    .withFix(new CreateMethodFix(attribute, name.text, controller))
                    .create();
        }
        for (JxmlValue.Seg arg : value.args) {
            if (JxmlModel.findVar(attribute, arg.text) == null) {
                holder.newAnnotation(HighlightSeverity.ERROR, "'" + arg.text + "' is not a loop variable in scope")
                        .range(new TextRange(base + arg.start, base + arg.end)).create();
            }
        }
    }

    private static void checkBinding(XmlAttribute attribute, JxmlValue value, int base, AnnotationHolder holder) {
        JxmlModel.Chain chain = JxmlModel.resolve(attribute, value);
        for (JxmlModel.Step step : chain.steps) {
            TextRange range = new TextRange(base + step.seg.start, base + step.seg.end);
            if (step.seg.text.isEmpty() || range.isEmpty()) {
                continue;
            }
            if (step.status == JxmlModel.Status.UNRESOLVED) {
                String where = step.owner == null ? "" : " in '" + step.owner.getName() + "'";
                holder.newAnnotation(HighlightSeverity.WARNING, "Cannot resolve property '" + step.seg.text + "'" + where
                        + ": expected a public getter " + getterHint(step.seg)).range(range).create();
            } else if (step.status == JxmlModel.Status.WEAK && step.target instanceof PsiMethod) {
                holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "The JXML runtime calls get" + Character.toUpperCase(step.seg.text.charAt(0)) + step.seg.text.substring(1)
                        + "(); " + ((PsiMethod) step.target).getName() + "() is not called for '" + step.seg.text + "'")
                        .range(range).create();
            }
        }
        if (value.kind == JxmlValue.Kind.TWO_WAY && !chain.steps.isEmpty()) {
            JxmlModel.Step last = chain.steps.get(chain.steps.size() - 1);
            TextRange range = new TextRange(base + last.seg.start, base + last.seg.end);
            if (last.seg.text.isEmpty() || range.isEmpty()) {
                return;
            }
            if (last.seg.isCall()) {
                holder.newAnnotation(HighlightSeverity.ERROR, "'@{" + last.seg.text + "}' cannot be bound two ways: "
                        + "a record accessor has no setter").range(range).create();
            } else if (last.loopVar && chain.steps.size() == 1) {
                holder.newAnnotation(HighlightSeverity.ERROR, "'@{" + last.seg.text + "}' cannot be bound two ways: "
                        + "a loop variable has nothing to set").range(range).create();
            } else if (!last.loopVar && last.owner != null && last.status != JxmlModel.Status.UNRESOLVED
                    && last.status != JxmlModel.Status.UNKNOWN) {
                if (JxmlModel.findSetter(last.owner, last.seg.text) == null) {
                    String setter = "set" + Character.toUpperCase(last.seg.text.charAt(0)) + last.seg.text.substring(1);
                    holder.newAnnotation(HighlightSeverity.ERROR, "'@{" + last.seg.text + "}' cannot be bound two ways: '"
                            + last.owner.getName() + "' has no public " + setter + "(value)").range(range).create();
                }
            }
        }
    }

    private static String getterHint(JxmlValue.Seg seg) {
        if (seg.isCall()) {
            return seg.name() + "()";
        }
        String cap = seg.text.isEmpty() ? "" : Character.toUpperCase(seg.text.charAt(0)) + seg.text.substring(1);
        return "get" + cap + "() or " + seg.text + "()";
    }
}
