package com.gradle.graalvm;

import com.gradle.AbstractNativeBuildPluginManager;
import com.gradle.configuration.AbstractNativeBuildCachingConfiguration;
import com.gradle.develocity.agent.maven.api.cache.MojoMetadataProvider;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Caching instructions for the GraalVM compile goal.
 */
public final class GraalVMPluginManager extends AbstractNativeBuildPluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraalVMPluginManager.class);

    @Override
    protected String getPluginName() {
        return "native-maven-plugin";
    }

    @Override
    protected List<String> getCacheableGoals() {
        return Arrays.asList("compile", "compile-no-fork");
    }

    @Override
    protected AbstractNativeBuildCachingConfiguration getConfiguration(MavenProject project, String localMavenRepoDir) {
        return new GraalVMBuildCachingConfiguration(project, localMavenRepoDir);
    }

    @Override
    protected void configureExtraPlugins(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        // unused
    }

    @Override
    protected void configureMojoInputs(MojoMetadataProvider.Context.Inputs inputs, AbstractNativeBuildCachingConfiguration configuration) {
        inputs
                .properties("skip", "skipNativeBuildForPom", "skipBaseSBOM", "mainClass", "imageName", "debug", "fallback", "environment", "environmentVariables", "systemPropertyVariables", "dryRun", "requiredVersion")
                .ignore("exclusions", "plugin", "session", "mojoExecution", "pluginArtifacts", "outputDirectory", "classpath", "classesDirectory", "defaultClassesDirectory", "verbose", "sharedLibrary", "quickBuild", "useArgFile", "buildArgs", "resourcesConfigDirectory", "agentResourceDirectory", "excludeConfig", "configurationFileDirectories", "jvmArgs", "runtimeArgs", "project", "reachabilityMetadataOutputDirectory", "metadataRepositoryConfiguration", "metadataRepositoryMaxRetries", "metadataRepositoryInitialBackoffMillis", "configFiles", "mavenSession", "systemProperties", "enforceToolchain", "mavenProject");
    }

    @Override
    protected void configurePrepareCacheOutputs(MojoMetadataProvider.Context.Outputs outputs, AbstractNativeBuildCachingConfiguration configuration) {
        String bundleFile = configuration.getBuildDir() + configuration.getBundleFile();
        outputs.file("nativeImageBundle", bundleFile);
    }

    @Override
    protected void configureCompileOutputs(MojoMetadataProvider.Context.Outputs outputs, AbstractNativeBuildCachingConfiguration configuration) {
        String graalVMExeFileName = configuration.getBuildDir() + ((GraalVMBuildCachingConfiguration) configuration).getGraalVMNativeBinaryName();
        String graalVMJarFileName = configuration.getBuildDir() + ((GraalVMBuildCachingConfiguration) configuration).getGraalVMNativeBinaryName() + ".jar";

        outputs.file("nativeBuildCachingExe", graalVMExeFileName);
        outputs.file("nativeBuildCachingJar", graalVMJarFileName);
    }

}
