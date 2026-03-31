package com.gradle.quarkus;

import com.gradle.AbstractNativeBuildPluginManager;
import com.gradle.configuration.AbstractNativeBuildCachingConfiguration;
import com.gradle.develocity.agent.maven.api.cache.MojoMetadataProvider;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Caching instructions for the Quarkus build goal.
 */
public final class QuarkusPluginManager extends AbstractNativeBuildPluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuarkusPluginManager.class);

    // Quarkus artifact descriptor
    private static final String QUARKUS_ARTIFACT_PROPERTIES_FILE_NAME = "quarkus-artifact.properties";

    private final TestPluginManager testPluginManager = new TestPluginManager();

    @Override
    protected String getPluginName() {
        return "quarkus-maven-plugin";
    }

    @Override
    protected List<String> getCacheableGoals() {
        return Collections.singletonList("build");
    }

    @Override
    protected AbstractNativeBuildCachingConfiguration getConfiguration(MavenProject project, String localMavenRepoDir) {
        return new QuarkusBuildCachingConfiguration(project, localMavenRepoDir);
    }

    @Override
    protected void configureExtraPlugins(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        context.withPlugin("maven-surefire-plugin", () -> {
            testPluginManager.configureQuarkusExtraTestInputs(context, configuration);
        });
        context.withPlugin("maven-failsafe-plugin", () -> {
            testPluginManager.configureQuarkusExtraTestInputs(context, configuration);
            testPluginManager.configureQuarkusExtraIntegrationTestInputs(context, configuration);
        });
    }

    @Override
    protected void configureMojoInputs(MojoMetadataProvider.Context.Inputs inputs, AbstractNativeBuildCachingConfiguration configuration) {
        inputs
                .fileSet("generatedSourcesDirectory", fileSet -> {
                })
                .properties("appArtifact", "closeBootstrappedApp", "finalName", "ignoredEntries", "manifestEntries", "manifestSections", "skip", "skipOriginalJarRename", "systemProperties", "properties", "attachSboms")
                .ignore("project", "buildDir", "mojoExecution", "session", "repoSession", "repos", "pluginRepos", "attachRunnerAsMainArtifact", "bootstrapId", "buildDirectory", "reloadPoms");

        addQuarkusDependenciesInputs(inputs, ((QuarkusBuildCachingConfiguration) configuration).getDependencyFileName());
    }

    private void addQuarkusDependenciesInputs(MojoMetadataProvider.Context.Inputs inputs, String currentDependencyFileName) {
        File quarkusDependencyFile = new File(currentDependencyFileName);
        if (quarkusDependencyFile.exists()) {
            try {
                List<String> quarkusDependencies = Files.readAllLines(quarkusDependencyFile.toPath(), Charset.defaultCharset());
                inputs.fileSet("quarkusDependencies", quarkusDependencies, fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
            } catch (IOException e) {
                LOGGER.error(AbstractNativeBuildCachingConfiguration.getLogMessage("Error while loading " + quarkusDependencyFile), e);
            }
        }
    }

    @Override
    protected void configurePrepareCacheOutputs(MojoMetadataProvider.Context.Outputs outputs, AbstractNativeBuildCachingConfiguration configuration) {
        String bundleFile = configuration.getBuildDir() + configuration.getBundleFile();
        String sourceJarDir = configuration.getBuildDir() + ((QuarkusBuildCachingConfiguration) configuration).getFinalName() + "-native-image-source-jar";

        outputs.file("nativeImageBundle", bundleFile);
        outputs.directory("nativeImageSourceJar", sourceJarDir);
    }

    @Override
    protected void configureCompileOutputs(MojoMetadataProvider.Context.Outputs outputs,  AbstractNativeBuildCachingConfiguration configuration) {
        String quarkusFinalName = configuration.getBuildDir() + ((QuarkusBuildCachingConfiguration) configuration).getFinalName();
        String quarkusExeFileName = quarkusFinalName + "-runner";
        String quarkusJarFileName = quarkusFinalName + ".jar";
        String quarkusUberJarFileName = quarkusFinalName + "-runner.jar";
        String quarkusFastJarDirectoryName = configuration.getBuildDir()  + "quarkus-app";
        String quarkusArtifactProperties = configuration.getBuildDir()  + QUARKUS_ARTIFACT_PROPERTIES_FILE_NAME;

        outputs.file("quarkusExe", quarkusExeFileName);
        outputs.file("quarkusJar", quarkusJarFileName);
        outputs.file("quarkusUberJar", quarkusUberJarFileName);
        outputs.file("quarkusArtifactProperties", quarkusArtifactProperties);
        outputs.directory("quarkusFastJar", quarkusFastJarDirectoryName);
    }

}
