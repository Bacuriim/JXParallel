package io.github.bacuriim.jxforge.schema;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Tells the XML machinery that a JXML file without its own schema is in the empty namespace, described by the bundled schema. */
public final class JxmlFileNsInfoProvider implements XmlFileNSInfoProvider {
    @Override
    public String @Nullable [][] getDefaultNamespaces(@NotNull XmlFile file) {
        if (!JxmlSchemaProvider.isJxml(file) || JxmlSchemaProvider.namesOwnSchema(file)) {
            return null;
        }
        return new String[][]{{"", JxmlResourceProvider.SCHEMA_URN}};
    }

    @Override
    public boolean overrideNamespaceFromDocType(@NotNull XmlFile file) {
        return false;
    }
}
