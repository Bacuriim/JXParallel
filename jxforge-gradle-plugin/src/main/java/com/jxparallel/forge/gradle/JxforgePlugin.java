package com.jxparallel.forge.gradle;

import java.util.Collections;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * {@code io.github.bacuriim.jxforge}: the tasks {@code jxforgeGenerate}, {@code jxforgeXsd} and {@code jxforgeFxml},
 * configured by {@code jxforge { ... }}. {@code jxforgeGenerate} output is a source folder of the main source set, so
 * {@code compileJava} runs it first.
 */
public class JxforgePlugin implements Plugin<Project> {
    public static final String GROUP = "jxforge";

    @Override
    public void apply(Project project) {
        JxforgeExtension jxforge = project.getExtensions().create("jxforge", JxforgeExtension.class);
        ProviderFactory providers = project.getProviders();
        FxmlSpec fxmlSpec = jxforge.getFxml();

        jxforge.getSourceDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/jxml"));
        jxforge.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/sources/jxforge/java/main"));
        jxforge.getClassSuffix().convention("View");
        jxforge.getHoistConstants().convention(true);
        jxforge.getXsdFile().convention(project.getLayout().getBuildDirectory().file("jxml.xsd"));

        fxmlSpec.getEnabled().convention(false);
        fxmlSpec.getSourceDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/resources"));
        fxmlSpec.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/jxforge/jxml"));
        fxmlSpec.getStrict().convention(providers.gradleProperty("jxforge.fxml.strict").map(Boolean::parseBoolean).orElse(false));
        fxmlSpec.getReportFile().convention(project.getLayout().getBuildDirectory().file("reports/jxforge/fxml-report.txt"));

        TaskProvider<JxforgeFxmlTask> fxml = project.getTasks().register("jxforgeFxml", JxforgeFxmlTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Converts FXML files to JXML (the goal jxforge:fxml); jxforgeGenerate compiles the result when jxforge.fxml.enabled is true.");
            task.getSourceDirectory().convention(fxmlSpec.getSourceDirectory());
            task.getOutputDirectory().convention(fxmlSpec.getOutputDirectory());
            task.getStrict().convention(fxmlSpec.getStrict());
            task.getBundle().convention(fxmlSpec.getBundle());
            task.getItemTypes().convention(fxmlSpec.getItemTypes());
            task.getReportFile().convention(fxmlSpec.getReportFile());
        });

        TaskProvider<JxforgeGenerateTask> generate = project.getTasks().register("jxforgeGenerate", JxforgeGenerateTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Compiles the JXML screens to Java (the goal jxforge:generate).");
            task.getPackageName().convention(jxforge.getPackageName());
            task.getClassSuffix().convention(jxforge.getClassSuffix());
            task.getHoistConstants().convention(jxforge.getHoistConstants());
            task.getSourceDirectory().convention(jxforge.getSourceDirectory());
            task.getOutputDirectory().convention(jxforge.getOutputDirectory());
            // With fxml.enabled, the FXML is converted first and its JXML compiled too.
            task.getIncludeFxml().convention(fxmlSpec.getEnabled());
            task.getFxmlJxmlDirectory().convention(fxml.flatMap(JxforgeFxmlTask::getOutputDirectory));
            task.dependsOn(fxmlSpec.getEnabled().map(enabled -> enabled
                    ? Collections.singletonList(fxml) : Collections.<TaskProvider<JxforgeFxmlTask>>emptyList()));
        });

        project.getTasks().register("jxforgeXsd", JxforgeXsdTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Writes the XML Schema of JXML (the goal jxforge:xsd).");
            Provider<org.gradle.api.file.RegularFile> custom = providers.gradleProperty("jxforge.xsd")
                    .flatMap(path -> project.getLayout().file(providers.provider(() -> project.file(path))));
            task.getOutputFile().convention(custom.orElse(jxforge.getXsdFile()));
        });

        project.getPlugins().withType(JavaPlugin.class, java -> {
            SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            main.getJava().srcDir(generate.flatMap(JxforgeGenerateTask::getOutputDirectory));
            project.getTasks().named(main.getCompileJavaTaskName(), task -> task.dependsOn(generate));
        });
    }
}
