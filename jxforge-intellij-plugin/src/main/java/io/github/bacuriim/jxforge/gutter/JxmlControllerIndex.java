package io.github.bacuriim.jxforge.gutter;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import io.github.bacuriim.jxforge.JxmlFileType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Maps the fully qualified controller class named by the root {@code controller} attribute to the {@code .jxml} files that use it. */
public final class JxmlControllerIndex extends ScalarIndexExtension<String> {
    public static final ID<String, Void> NAME = ID.create("io.github.bacuriim.jxforge.controller");

    @Override
    public @NotNull ID<String, Void> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
        return inputData -> {
            if (!(inputData.getPsiFile() instanceof XmlFile)) {
                return Collections.emptyMap();
            }
            XmlTag root = ((XmlFile) inputData.getPsiFile()).getRootTag();
            String controller = root == null ? null : root.getAttributeValue("controller");
            return controller == null || controller.isBlank() ? Collections.<String, Void>emptyMap()
                    : Collections.<String, Void>singletonMap(controller.trim(), null);
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(JxmlFileType.INSTANCE);
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    /** The {@code .jxml} files whose root {@code controller} is {@code qualifiedName}. */
    public static List<PsiFile> filesOf(Project project, String qualifiedName) {
        List<PsiFile> result = new ArrayList<>();
        Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, qualifiedName,
                GlobalSearchScope.projectScope(project));
        for (VirtualFile file : files) {
            PsiFile psi = PsiManager.getInstance(project).findFile(file);
            if (psi != null) {
                result.add(psi);
            }
        }
        return result;
    }
}
