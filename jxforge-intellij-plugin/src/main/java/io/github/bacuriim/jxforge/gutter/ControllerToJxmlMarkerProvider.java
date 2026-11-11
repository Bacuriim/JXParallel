package io.github.bacuriim.jxforge.gutter;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import io.github.bacuriim.jxforge.JxmlIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/** In a controller class: a gutter icon on the class name that navigates to the {@code .jxml} screens that name it. */
public final class ControllerToJxmlMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiClass)) {
            return;
        }
        PsiClass cls = (PsiClass) element.getParent();
        if (cls.getNameIdentifier() != element || cls.getQualifiedName() == null) {
            return;
        }
        List<PsiFile> screens = JxmlControllerIndex.filesOf(element.getProject(), cls.getQualifiedName());
        if (screens.isEmpty()) {
            return;
        }
        result.add(NavigationGutterIconBuilder.create(JxmlIcons.FILE)
                .setTargets(screens)
                .setTooltipText("Navigate to the JXML screens of this controller")
                .setPopupTitle("JXML screens of " + cls.getName())
                .setAlignment(com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT)
                .createLineMarkerInfo(element));
    }
}
