package com.gradle;

import com.gradle.develocity.agent.maven.api.DevelocityApi;
import com.gradle.develocity.agent.maven.api.DevelocityListener;
import com.gradle.graalvm.GraalVMPluginManager;
import com.gradle.quarkus.QuarkusPluginManager;
import org.apache.maven.execution.MavenSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NativeBuildCachingDevelocityListener implements DevelocityListener {

    private final Logger LOGGER = LoggerFactory.getLogger(NativeBuildCachingDevelocityListener.class);

    private final QuarkusPluginManager quarkusPluginManager = new QuarkusPluginManager();
    private final GraalVMPluginManager graalVMPluginManager = new GraalVMPluginManager();

    @Override
    public void configure(DevelocityApi api, MavenSession session) {
        LOGGER.debug("Executing extension: " + getClass().getSimpleName());
        api.getBuildCache().registerMojoMetadataProvider(context -> {
            quarkusPluginManager.configureBuildCache(api.getBuildCache(), context);
            graalVMPluginManager.configureBuildCache(api.getBuildCache(), context);
        });
    }

}
