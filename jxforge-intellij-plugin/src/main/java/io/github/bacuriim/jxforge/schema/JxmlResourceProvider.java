package io.github.bacuriim.jxforge.schema;

import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the bundled schema under {@value #SCHEMA_URN}, so a file can name it
 * ({@code xsi:noNamespaceSchemaLocation="urn:jxforge:jxml.xsd"}) without a copy of {@code jxml.xsd} beside it.
 */
public final class JxmlResourceProvider implements StandardResourceProvider {
    public static final String SCHEMA_URN = "urn:jxforge:jxml.xsd";

    @Override
    public void registerResources(@NotNull ResourceRegistrar registrar) {
        registrar.addStdResource(SCHEMA_URN, JxmlSchemaProvider.RESOURCE, JxmlResourceProvider.class);
    }
}
