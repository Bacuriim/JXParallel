package io.github.bacuriim.jxforge.gutter;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import io.github.bacuriim.jxforge.JxmlIcons;
import io.github.bacuriim.jxforge.refs.JxmlModel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/** In a {@code .jxml} file: a gutter icon on the root tag that navigates to the controller class. */
public final class JxmlToControllerMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof XmlToken) || ((XmlToken) element).getTokenType() != XmlTokenType.XML_NAME
                || !(element.getParent() instanceof XmlTag)) {
            return;
        }
        XmlTag tag = (XmlTag) element.getParent();
        if (tag.getParentTag() != null || !(element.getContainingFile() instanceof XmlFile)
                || !JxmlModel.isJxml(element.getContainingFile())
                || !isFirstName(tag, element)) {
            return;
        }
        PsiClass controller = JxmlModel.controller((XmlFile) element.getContainingFile());
        if (controller == null) {
            return;
        }
        result.add(NavigationGutterIconBuilder.create(JxmlIcons.CONTROLLER)
                .setTarget(controller)
                .setTooltipText("Navigate to the controller " + controller.getName())
                .setAlignment(com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT)
                .createLineMarkerInfo(element));
    }

    /** Only the name in the start tag, not the one in the end tag. */
    private static boolean isFirstName(XmlTag tag, PsiElement name) {
        return com.intellij.psi.xml.XmlTokenType.XML_NAME == ((XmlToken) name).getTokenType()
                && name.getTextRange().getStartOffset() == tag.getTextRange().getStartOffset() + 1;
    }
}
