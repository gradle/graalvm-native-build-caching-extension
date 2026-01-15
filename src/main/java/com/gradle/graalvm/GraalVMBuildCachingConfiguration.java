package com.gradle.graalvm;

import com.gradle.configuration.AbstractNativeBuildCachingConfiguration;
import org.apache.maven.project.MavenProject;

final class GraalVMBuildCachingConfiguration extends AbstractNativeBuildCachingConfiguration {

    // GraalVM native binary name
    private static final String DEVELOCITY_NATIVE_KEY_GRAALVM_IMAGE_NAME = "DEVELOCITY_NATIVE_GRAALVM_IMAGE_NAME";

    GraalVMBuildCachingConfiguration(MavenProject project, String mavenLocalRepoDir) {
        super(project, mavenLocalRepoDir);
    }

    @Override
    protected void initPluginSpecificDefaults(MavenProject project) {
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_GRAALVM_IMAGE_NAME, project.getArtifactId());
    }

    /**
     * @return GraalVM native binary name
     */
    String getGraalVMNativeBinaryName() {
        return configuration.getProperty(DEVELOCITY_NATIVE_KEY_GRAALVM_IMAGE_NAME);
    }
}
