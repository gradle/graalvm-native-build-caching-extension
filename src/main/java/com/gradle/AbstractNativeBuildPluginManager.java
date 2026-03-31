package com.gradle;

import com.gradle.configuration.AbstractNativeBuildCachingConfiguration;
import com.gradle.develocity.agent.maven.api.cache.BuildCacheApi;
import com.gradle.develocity.agent.maven.api.cache.MojoMetadataProvider;
import com.gradle.normalization.AbstractNormalizer;
import com.gradle.normalization.JsonNormalizer;
import com.gradle.normalization.PropertiesNormalizer;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract plugin manager
 */
public abstract class AbstractNativeBuildPluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNativeBuildPluginManager.class);

    private static final List<String> NATIVE_IMAGE_BUNDLE_DIRS_TO_COPY = Arrays.asList(
            "input/",
            "META-INF/"
    );

    private static final String PREPARE_CACHE_EXECUTION_ID = "prepare-cache";

    private static final AbstractNormalizer jsonNormalizer = new JsonNormalizer();
    private static final AbstractNormalizer propertiesNormalizer = new PropertiesNormalizer();

    // Tracks mojo executions that have already been processed to avoid duplicate bundle extraction and logging
    private final Map<String, Boolean> processedExecutions = Collections.synchronizedMap(new HashMap<>());

    protected abstract String getPluginName();

    protected abstract List<String> getCacheableGoals();

    protected abstract AbstractNativeBuildCachingConfiguration getConfiguration(MavenProject project);

    protected abstract void configureExtraPlugins(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration);

    protected abstract void configureMojoInputs(MojoMetadataProvider.Context.Inputs inputs, AbstractNativeBuildCachingConfiguration configuration);

    protected abstract void configureCompileOutputs(MojoMetadataProvider.Context.Outputs outputs, AbstractNativeBuildCachingConfiguration configuration);

    protected abstract void configurePrepareCacheOutputs(MojoMetadataProvider.Context.Outputs outputs, AbstractNativeBuildCachingConfiguration configuration);

    protected void configureBuildCache(BuildCacheApi buildCache, MojoMetadataProvider.Context context) {
        AbstractNativeBuildCachingConfiguration configuration = getConfiguration(context.getProject());

        if (configuration.isNativeBuildCachingEnabled()) {
            context.withPlugin(getPluginName(), () -> {
                configureNativeBuildCaching(context, configuration);
            });

            configureExtraPlugins(context, configuration);
        } else {
            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Native build caching is disabled"));
        }
    }

    private void configureNativeBuildCaching(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        if (getCacheableGoals().contains(context.getMojoExecution().getGoal())) {
            String executionId = context.getMojoExecution().getExecutionId();
            if(PREPARE_CACHE_EXECUTION_ID.equals(executionId)) {
                // only caching prepare cache execution if fast mode is enabled
                if(configuration.isNativeBuildCachingFastModeEnabled()) {
                    configurePrepareCacheInputs(context, configuration);
                    configurePrepareCacheOutputs(context, configuration);
                }
            } else {
                processedExecutions.computeIfAbsent(executionId, key -> {
                    LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage(configuration.toString()));
                    extractBundle(configuration);
                    transformBundle(configuration);
                    LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Native build cache configured"));
                    return Boolean.TRUE;
                });

                configureCompileInputs(context, configuration);
                configureCompileOutputs(context, configuration);
            }
        }
    }

    private void extractBundle(AbstractNativeBuildCachingConfiguration configuration) {
        Path bundlePath = Paths.get(configuration.getBuildDir(), configuration.getBundleFile());
        if (!Files.isRegularFile(bundlePath)) {
            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Native image bundle not found"));
            return;
        }

        LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Found native image bundle"));

        try (ZipFile zipFile = new ZipFile(bundlePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                for (String dirToCopy : NATIVE_IMAGE_BUNDLE_DIRS_TO_COPY) {
                    if (name.startsWith(dirToCopy)) {
                        Path targetPath = Paths.get(configuration.getWorkDir()).resolve(name);
                        Files.createDirectories(targetPath.getParent());
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                        LOGGER.trace(AbstractNativeBuildCachingConfiguration.getLogMessage("Extracted file " + name));
                        break;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage(e.toString()));
            throw new RuntimeException(e);
        }
    }

    private void transformBundle(AbstractNativeBuildCachingConfiguration configuration) {
        LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Transform native image bundle"));

        String nativeImageBundleDir = configuration.getWorkDir();
        if(new File(nativeImageBundleDir).exists()) {
            jsonNormalizer.normalize(nativeImageBundleDir + "input/stage/build.json");
            jsonNormalizer.normalize(nativeImageBundleDir + "input/stage/environment.json");
            jsonNormalizer.normalize(nativeImageBundleDir + "input/stage/path_canonicalizations.json");
            jsonNormalizer.normalize(nativeImageBundleDir + "input/stage/path_substitutions.json");
            propertiesNormalizer.normalize(nativeImageBundleDir + "META-INF/nibundle.properties");
        } else {
            LOGGER.info(AbstractNativeBuildCachingConfiguration.getLogMessage("Extracted native image bundle not found"));
        }
    }

    // The prepare-cache execution is cacheable only if fast mode is enabled (isNativeBuildCachingFastModeEnabled)
    // prepare-cache inputs are environment-specific, reason why the user is added as input
    // This optimization is a trade-off between speed and consistency. A change in the environment could be undetected and lead to a wrong cache hit.
    private void configurePrepareCacheInputs(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        configureCommonInputs(context, configuration);

        context.inputs(inputs -> {
            inputs.property("userName", System.getProperty("user.name"));
            inputs.property("osName", System.getProperty("os.name"))
                    .property("osVersion", System.getProperty("os.version"))
                    .property("osArch", System.getProperty("os.arch"));
            inputs.property("javaVersion", System.getProperty("java.version"));
        });
    }

    private void configureCompileInputs(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        context.inputs(inputs -> {
            inputs.fileSet("nibManifest", new File(configuration.getWorkDir() + "META-INF/MANIFEST.MF"), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
            inputs.fileSet("nibProperties", new File(configuration.getWorkDir() + "META-INF/nibundle.properties" + AbstractNormalizer.TRANSFORMED_SUFFIX), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
            inputs.fileSet("nibAuxiliary", new File(configuration.getWorkDir() + "input/auxiliary"), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));
            inputs.fileSet("nibClasses", new File(configuration.getWorkDir() + "input/classes"), fileSet -> fileSet.exclude("**/" + context.getProject().getBuild().getFinalName() + "-runner.jar").normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
            inputs.fileSet("nibStage", new File(configuration.getWorkDir() + "input/stage"), fileSet -> fileSet.exclude("**/*.json").normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));
            inputs.fileSet("nibStageRun", new File(configuration.getWorkDir() + "input/stage/run.json"), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));
        });

        configureCommonInputs(context, configuration);
    }

    private void configureCommonInputs(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        context.inputs(inputs -> {
            try {
                List<String> compileClasspathElements = context.getProject().getCompileClasspathElements();
                inputs.fileSet("nativeBuildCachingCompileClasspath", compileClasspathElements, fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
            } catch (DependencyResolutionRequiredException e) {
                throw new IllegalStateException("Classpath can't be resolved");
            }

            configureMojoInputs(inputs, configuration);
        });
    }

    private void configurePrepareCacheOutputs(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        context.outputs(outputs -> {
            outputs.cacheable("this plugin has CPU-bound goals with well-defined inputs and outputs");
            configurePrepareCacheOutputs(outputs, configuration);
        });
    }

    private void configureCompileOutputs(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        context.outputs(outputs -> {
            outputs.cacheable("this plugin has CPU-bound goals with well-defined inputs and outputs");

            configuration.getExtraOutputDirs().forEach(extraOutput -> {
                if (!extraOutput.isEmpty()) {
                    LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Adding extra output dir " + extraOutput));
                    outputs.directory(extraOutput, configuration.getBuildDir() + extraOutput);
                }
            });

            configuration.getExtraOutputFiles().forEach(extraOutput -> {
                if (!extraOutput.isEmpty()) {
                    LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Adding extra output file " + extraOutput));
                    outputs.file(extraOutput, configuration.getBuildDir() + extraOutput);
                }
            });

            configureCompileOutputs(outputs, configuration);
        });
    }

}
