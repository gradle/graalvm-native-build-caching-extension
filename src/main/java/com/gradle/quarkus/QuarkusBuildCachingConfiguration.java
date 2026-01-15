package com.gradle.quarkus;

import com.gradle.configuration.AbstractNativeBuildCachingConfiguration;
import org.apache.maven.project.MavenProject;

final class QuarkusBuildCachingConfiguration extends AbstractNativeBuildCachingConfiguration {

    // Quarkus config file prefix key
    private static final String DEVELOCITY_NATIVE_KEY_QUARKUS_CONFIG_PREFIX = "DEVELOCITY_NATIVE_QUARKUS_CONFIG_PREFIX";

    // Quarkus build profile key
    private static final String DEVELOCITY_NATIVE_KEY_QUARKUS_BUILD_PROfILE = "DEVELOCITY_NATIVE_QUARKUS_BUILD_PROFILE";

    // Quarkus final name
    private static final String DEVELOCITY_NATIVE_KEY_QUARKUS_FINAL_NAME = "DEVELOCITY_NATIVE_QUARKUS_FINAL_NAME";

    // Default Quarkus dump config file prefix
    private static final String DEVELOCITY_NATIVE_DEFAULT_QUARKUS_CONFIG_PREFIX = "quarkus";

    // Default Quarkus build profile
    private static final String DEVELOCITY_NATIVE_DEFAULT_QUARKUS_BUILD_PROFILE = "prod";

    QuarkusBuildCachingConfiguration(MavenProject project, String mavenLocalRepoDir) {
        super(project, mavenLocalRepoDir);
    }

    @Override
    protected void initPluginSpecificDefaults(MavenProject project) {
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_QUARKUS_BUILD_PROfILE, DEVELOCITY_NATIVE_DEFAULT_QUARKUS_BUILD_PROFILE);
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_QUARKUS_CONFIG_PREFIX, DEVELOCITY_NATIVE_DEFAULT_QUARKUS_CONFIG_PREFIX);
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_QUARKUS_FINAL_NAME, project.getBuild().getFinalName());
    }

    /**
     * This file contains the list of absolute paths to runtime dependencies used by the Quarkus application.
     *
     * @return dependency file name
     */
    String getDependencyFileName() {
        return String.format("target/%s-%s-dependencies.txt",
                configuration.getProperty(DEVELOCITY_NATIVE_KEY_QUARKUS_CONFIG_PREFIX),
                configuration.getProperty(DEVELOCITY_NATIVE_KEY_QUARKUS_BUILD_PROfILE)
        );
    }

    /**
     * @return Quarkus final name
     */
    String getFinalName() {
        return configuration.getProperty(DEVELOCITY_NATIVE_KEY_QUARKUS_FINAL_NAME);
    }
}
