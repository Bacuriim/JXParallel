package io.github.bacuriim.jxforge;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import io.github.bacuriim.jxforge.refs.JxmlModel;
import io.github.bacuriim.jxforge.refs.JxmlValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Quick fix: {@code Create method 'name' in Controller}. The parameters are the loop variables the handler passes. */
public final class CreateMethodFix implements IntentionAction {
    private final SmartPsiElementPointer<XmlAttribute> attribute;
    private final String name;
    private final SmartPsiElementPointer<PsiClass> controller;

    public CreateMethodFix(XmlAttribute attribute, String name, PsiClass controller) {
        SmartPointerManager manager = SmartPointerManager.getInstance(attribute.getProject());
        this.attribute = manager.createSmartPsiElementPointer(attribute);
        this.name = name;
        this.controller = manager.createSmartPsiElementPointer(controller);
    }

    @Override
    public @NotNull String getText() {
        return "Create method '" + name + "' in Controller";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Create method in JXML controller";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        PsiClass cls = controller.getElement();
        return cls != null && attribute.getElement() != null && cls.isValid() && !cls.isInterface() && !cls.isAnnotationType()
                && cls.getContainingFile() != null && cls.getContainingFile().isWritable();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiClass cls = controller.getElement();
        XmlAttribute attr = attribute.getElement();
        if (cls == null || attr == null || !FileModificationService.getInstance().preparePsiElementForWrite(cls)) {
            return;
        }
        String parameters = parameters(attr);
        WriteCommandAction.writeCommandAction(project, cls.getContainingFile()).withName(getText()).run(() -> {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiMethod method = factory.createMethodFromText("public void " + name + "(" + parameters + ") {\n}", cls);
            PsiElement added = cls.add(method);
            com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project).reformat(added);
        });
    }

    /** {@code #open(o)} becomes {@code open(Order o)}; a type that is not known is {@code Object}. */
    private static String parameters(XmlAttribute attr) {
        JxmlValue value = JxmlValue.parse(attr);
        if (value == null || value.args.isEmpty()) {
            return "";
        }
        List<String> out = new ArrayList<>();
        for (JxmlValue.Seg arg : value.args) {
            JxmlModel.LoopVar var = JxmlModel.findVar(attr, arg.text);
            PsiType type = var == null ? null : JxmlModel.loopVarType(var);
            out.add((type == null ? "Object" : type.getCanonicalText()) + " " + arg.text);
        }
        return String.join(", ", out);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        return IntentionPreviewInfo.EMPTY;
    }

}
