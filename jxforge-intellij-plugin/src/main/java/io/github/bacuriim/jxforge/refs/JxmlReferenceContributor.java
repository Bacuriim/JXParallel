package io.github.bacuriim.jxforge.refs;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import io.github.bacuriim.jxforge.JxmlFileType;
import org.jetbrains.annotations.NotNull;

/** Registers the references of {@code .jxml} attribute values: controller class, handlers, bindings, {@code type}, {@code Include src}. */
public final class JxmlReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class).inFile(
                        PlatformPatterns.psiFile().withFileType(StandardPatterns.instanceOf(JxmlFileType.class))),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                        XmlAttributeValue value = (XmlAttributeValue) element;
                        if (!(value.getParent() instanceof XmlAttribute)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        XmlAttribute attribute = (XmlAttribute) value.getParent();
                        XmlTag tag = attribute.getParent();
                        String name = attribute.getName();
                        String text = value.getValue();
                        if (tag == null || text == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        if ("controller".equals(name) && tag.getParentTag() == null) {
                            return classReferences(value, text, false);
                        }
                        if ("type".equals(name) && tag.getAttribute("var") != null) {
                            return classReferences(value, text, true);
                        }
                        if ("src".equals(name) && "Include".equals(tag.getName())) {
                            FileReferenceSet set = FileReferenceSet.createSet(value, false, false, false);
                            return set == null ? PsiReference.EMPTY_ARRAY : set.getAllReferences();
                        }
                        return JxmlReferences.forValue(value);
                    }
                });
    }

    private static PsiReference[] classReferences(XmlAttributeValue element, String text, boolean soft) {
        if (text.isBlank()) {
            return PsiReference.EMPTY_ARRAY;
        }
        JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
        provider.setSoft(soft);
        provider.setAllowEmpty(false);
        provider.setOption(JavaClassReferenceProvider.RESOLVE_QUALIFIED_CLASS_NAME, Boolean.TRUE);
        return provider.getReferencesByString(text, element, JxmlReferences.valueOffset(element));
    }
}
