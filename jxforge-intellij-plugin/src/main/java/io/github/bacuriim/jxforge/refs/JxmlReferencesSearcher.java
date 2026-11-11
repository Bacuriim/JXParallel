package io.github.bacuriim.jxforge.refs;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import io.github.bacuriim.jxforge.JxmlFileType;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Find Usages and rename of a controller method also look in {@code .jxml} files: a getter {@code getName()} is written
 * {@code name} in a binding, so the search for the method name alone would not find it. Registered for the generic
 * reference search and, in {@link JxmlMethodReferencesSearcher}, for the method search that rename of a method uses.
 */
public final class JxmlReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
    public JxmlReferencesSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters params, @NotNull Processor<? super PsiReference> consumer) {
        PsiElement element = params.getElementToSearch();
        if (element instanceof PsiMethod) {
            search((PsiMethod) element, params.getEffectiveSearchScope(), params.getOptimizer());
        }
    }

    static void search(PsiMethod method, SearchScope scope, SearchRequestCollector optimizer) {
        if (!(scope instanceof GlobalSearchScope)) {
            return;
        }
        GlobalSearchScope jxml = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope) scope, JxmlFileType.INSTANCE);
        Set<String> words = new LinkedHashSet<>();
        words.add(method.getName());
        words.add(JxmlModel.propertyOf(method.getName()));
        for (String word : words) {
            if (!word.isEmpty()) {
                optimizer.searchWord(word, jxml, UsageSearchContext.ANY, true, method);
            }
        }
    }
}
