package com.gradle.quarkus;

import com.gradle.configuration.AbstractNativeBuildCachingConfiguration;
import com.gradle.develocity.agent.maven.api.cache.MojoMetadataProvider;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

/**
 * Caching instructions for the test goals.
 */
final class TestPluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestPluginManager.class);

    // Quarkus artifact descriptor
    private static final String QUARKUS_ARTIFACT_PROPERTIES_FILE_NAME = "quarkus-artifact.properties";

    void configureQuarkusExtraTestInputs(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        QuarkusTestConfiguration testConfiguration = new QuarkusTestConfiguration(context, configuration);
        LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage(testConfiguration.toString()));
        if (testConfiguration.isAddQuarkusInputs()) {
            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Adding Quarkus extra test inputs"));
            context.inputs(inputs -> addQuarkusDependenciesInputs(inputs, ((QuarkusBuildCachingConfiguration) configuration).getDependencyFileName()));
        }
        if (testConfiguration.isAddQuarkusPackageInputs()) {
            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Adding Quarkus extra test package inputs"));
            context.inputs(inputs -> addQuarkusJarInput(inputs, configuration, testConfiguration));
        }
    }

    void configureQuarkusExtraIntegrationTestInputs(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        QuarkusTestConfiguration testConfiguration = new QuarkusTestConfiguration(context, configuration);
        if (testConfiguration.isAddQuarkusInputs()) {
            context.inputs(inputs -> addQuarkusArtifactPropertiesInput(inputs, configuration.getBuildDir()));
        }
        if (testConfiguration.isAddQuarkusPackageInputs()) {
            context.inputs(inputs -> addQuarkusExeInput(inputs, configuration, testConfiguration));
        }
    }

    private void addQuarkusJarInput(MojoMetadataProvider.Context.Inputs inputs, AbstractNativeBuildCachingConfiguration configuration, QuarkusTestConfiguration testConfiguration) {
        inputs.fileSet("quarkusJarFile", new File(configuration.getBuildDir()), fileSet -> fileSet.include(testConfiguration.getQuarkusJarFilePattern()).normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
    }

    private void addQuarkusExeInput(MojoMetadataProvider.Context.Inputs inputs, AbstractNativeBuildCachingConfiguration configuration, QuarkusTestConfiguration testConfiguration) {
        inputs.fileSet("quarkusExeFile", new File(configuration.getBuildDir()), fileSet -> fileSet.include(testConfiguration.getQuarkusExeFilePattern()).normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));
    }

    private void addQuarkusArtifactPropertiesInput(MojoMetadataProvider.Context.Inputs inputs, String buildDir) {
        inputs.fileSet("quarkusArtifactProperties", new File(buildDir + QUARKUS_ARTIFACT_PROPERTIES_FILE_NAME), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));
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
        } else {
            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage(quarkusDependencyFile + " not found"));
        }
    }

    private static class QuarkusTestConfiguration {
        private static final String TEST_GOAL_KEY_ADD_QUARKUS_INPUTS = "addQuarkusInputs";
        private static final String TEST_GOAL_KEY_ADD_QUARKUS_PACKAGE_INPUTS = "addQuarkusPackageInputs";
        private static final String TEST_GOAL_KEY_QUARKUS_PACKAGE_PATTERN = "quarkusPackagePattern";
        private static final String TEST_GOAL_DEFAULT_QUARKUS_PACKAGE_JAR_PATTERN = "*.jar";
        private static final String TEST_GOAL_DEFAULT_QUARKUS_PACKAGE_EXE_PATTERN = "*-runner";

        private boolean addQuarkusInputs;
        private boolean addQuarkusPackageInputs;
        private String quarkusPackagePattern;

        QuarkusTestConfiguration(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
            if (configuration.isNativeBuildCachingEnabled()) {
                Xpp3Dom properties = context.getMojoExecution().getConfiguration().getChild("properties");
                if (properties != null) {
                    Xpp3Dom addQuarkusInputsProperty = properties.getChild(TEST_GOAL_KEY_ADD_QUARKUS_INPUTS);
                    if (addQuarkusInputsProperty != null) {
                        addQuarkusInputs = Boolean.parseBoolean(addQuarkusInputsProperty.getValue());
                    }
                    Xpp3Dom addQuarkusPackageInputsProperty = properties.getChild(TEST_GOAL_KEY_ADD_QUARKUS_PACKAGE_INPUTS);
                    if (addQuarkusPackageInputsProperty != null) {
                        addQuarkusPackageInputs = Boolean.parseBoolean(addQuarkusPackageInputsProperty.getValue());
                    }
                    Xpp3Dom quarkusPackagePatternProperty = properties.getChild(TEST_GOAL_KEY_QUARKUS_PACKAGE_PATTERN);
                    if (quarkusPackagePatternProperty != null) {
                        quarkusPackagePattern = quarkusPackagePatternProperty.getValue();
                    }
                }
            }
        }

        boolean isAddQuarkusInputs() {
            return addQuarkusInputs;
        }

        boolean isAddQuarkusPackageInputs() {
            return addQuarkusPackageInputs;
        }

        String getQuarkusJarFilePattern() {
            return quarkusPackagePattern != null ? quarkusPackagePattern : TEST_GOAL_DEFAULT_QUARKUS_PACKAGE_JAR_PATTERN;
        }

        String getQuarkusExeFilePattern() {
            return quarkusPackagePattern != null ? quarkusPackagePattern : TEST_GOAL_DEFAULT_QUARKUS_PACKAGE_EXE_PATTERN;
        }

        @Override
        public String toString() {
            return "TestConfiguration{" +
                    "addQuarkusInputs=" + addQuarkusInputs +
                    ", addQuarkusPackageInputs=" + addQuarkusPackageInputs +
                    ", quarkusPackagePattern='" + quarkusPackagePattern + '\'' +
                    '}';
        }

    }
}
