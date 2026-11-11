package io.github.bacuriim.jxforge.refs;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/** The same search for {@link MethodReferencesSearch}, which the rename of a Java method uses to find its usages. */
public final class JxmlMethodReferencesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
    public JxmlMethodReferencesSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@NotNull MethodReferencesSearch.SearchParameters params, @NotNull Processor<? super PsiReference> consumer) {
        JxmlReferencesSearcher.search(params.getMethod(), params.getEffectiveSearchScope(), params.getOptimizer());
    }
}
