package io.github.bacuriim.jxforge.schema;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlSchemaProvider;
import io.github.bacuriim.jxforge.JxmlFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Set;

/**
 * Supplies the bundled {@code jxml.xsd} (generated from {@code JxmlSchema} of JXForge at build time) to every
 * {@code .jxml} file, so completion, documentation and validation need no configuration. A file that names its
 * own schema with {@code xsi:noNamespaceSchemaLocation} keeps that one.
 */
public final class JxmlSchemaProvider extends XmlSchemaProvider {
    public static final String RESOURCE = "/io/github/bacuriim/jxforge/jxml.xsd";

    public static boolean isJxml(@Nullable PsiFile file) {
        return file != null && file.getFileType() == JxmlFileType.INSTANCE;
    }

    /** True when the root element writes its own {@code xsi:noNamespaceSchemaLocation}, other than the bundled one. */
    static boolean namesOwnSchema(XmlFile file) {
        XmlTag root = file.getRootTag();
        if (root == null) {
            return false;
        }
        String location = null;
        // syntactic lookup: getAttributeValue(name, ns) computes the namespace map, which asks this provider again
        for (com.intellij.psi.xml.XmlAttribute attribute : root.getAttributes()) {
            String name = attribute.getName();
            if (name.equals("noNamespaceSchemaLocation") || name.endsWith(":noNamespaceSchemaLocation")) {
                location = attribute.getValue();
                break;
            }
        }
        return location != null && !JxmlResourceProvider.SCHEMA_URN.equals(location.trim());
    }

    @Override
    public boolean isAvailable(@NotNull XmlFile file) {
        return isJxml(file) && !namesOwnSchema(file);
    }

    @Override
    public @Nullable XmlFile getSchema(@NotNull String url, @Nullable Module module, @NotNull PsiFile baseFile) {
        if (!isJxml(baseFile) || !url.isEmpty() && !JxmlResourceProvider.SCHEMA_URN.equals(url)) {
            return null;
        }
        return bundledSchema(baseFile);
    }

    static @Nullable XmlFile bundledSchema(@NotNull PsiFile baseFile) {
        URL resource = JxmlSchemaProvider.class.getResource(RESOURCE);
        if (resource == null) {
            return null;
        }
        VirtualFile file = VfsUtil.findFileByURL(resource);
        if (file == null) {
            return null;
        }
        PsiFile psi = PsiManager.getInstance(baseFile.getProject()).findFile(file);
        return psi instanceof XmlFile ? (XmlFile) psi : null;
    }

    @Override
    public @NotNull Set<String> getAvailableNamespaces(@NotNull XmlFile file, @Nullable String tagName) {
        return isAvailable(file) ? Set.of("") : Set.of();
    }
}
