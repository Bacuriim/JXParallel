package io.github.bacuriim.jxforge.template;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import io.github.bacuriim.jxforge.JxmlIcons;
import org.jetbrains.annotations.NotNull;

/** New | JXML Screen: a {@code .jxml} file with a root {@code Column}, a {@code controller} attribute and the schema line. */
public final class NewJxmlScreenAction extends CreateFileFromTemplateAction implements DumbAware {
    public static final String TEMPLATE = "JXML Screen";

    public NewJxmlScreenAction() {
        super("JXML Screen", "Create a new JXML screen", JxmlIcons.FILE);
    }

    @Override
    protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory, CreateFileFromTemplateDialog.@NotNull Builder builder) {
        builder.setTitle("New JXML Screen").addKind("JXML Screen", JxmlIcons.FILE, TEMPLATE);
    }

    @Override
    protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return "Create JXML Screen " + newName;
    }
}
