package com.jxparallel.forge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * Registers {@code jxforgeGenerate}, runs it before {@code compileJava} and adds its output to the
 * main source set. Applying the plugin without the {@code java} plugin only registers the task.
 */
public class JXForgePlugin implements Plugin<Project> {
    public static final String EXTENSION = "jxforge";
    public static final String TASK = "jxforgeGenerate";

    @Override
    public void apply(Project project) {
        JXForgeExtension ext = project.getExtensions().create(EXTENSION, JXForgeExtension.class);
        ext.getSourceDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/jxml"));
        ext.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/sources/jxforge"));
        ext.getClassSuffix().convention("View");
        ext.getHoistConstants().convention(true);
        ext.getL10nDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/l10n"));
        ext.getL10nStrict().convention(false);
        ext.getForgeVersion().convention(ownVersion());

        // Resolved only when the task runs; the forge is not bundled in the plugin.
        Configuration forge = project.getConfigurations().detachedConfiguration();
        forge.setDescription("The JXForge compiler and its dependencies.");
        forge.setVisible(false);
        forge.getDependencies().addLater(ext.getForgeVersion().map(
                v -> project.getDependencies().create("com.jxparallel:jxparallel-forge:" + v)));

        TaskProvider<JXForgeGenerateTask> generate = project.getTasks().register(TASK, JXForgeGenerateTask.class, t -> {
            t.setDescription("Compiles .jxml files to Java.");
            t.setGroup("build");
            t.getSourceDirectory().set(ext.getSourceDirectory());
            t.getSources().from(project.provider(() -> project.fileTree(ext.getSourceDirectory())));
            t.getOutputDirectory().set(ext.getOutputDirectory());
            t.getPackageName().set(ext.getPackageName());
            t.getClassSuffix().set(ext.getClassSuffix());
            t.getHoistConstants().set(ext.getHoistConstants());
            t.getL10nDirectory().set(ext.getL10nDirectory());
            t.getL10nFiles().from(project.provider(() -> project.fileTree(ext.getL10nDirectory())));
            t.getL10nStrict().set(ext.getL10nStrict());
            t.getForgeClasspath().from(forge);
        });

        project.getPlugins().withType(JavaPlugin.class, p -> {
            SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            main.getJava().srcDir(generate.flatMap(JXForgeGenerateTask::getOutputDirectory));
            project.getTasks().named(main.getCompileJavaTaskName(), t -> t.dependsOn(generate));
        });
    }

    private static String ownVersion() {
        try (InputStream in = JXForgePlugin.class.getResourceAsStream("/jxforge-plugin.properties")) {
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("forgeVersion");
        } catch (IOException | RuntimeException e) {
            throw new GradleException("JXForge plugin is missing its jxforge-plugin.properties", e);
        }
    }
}
