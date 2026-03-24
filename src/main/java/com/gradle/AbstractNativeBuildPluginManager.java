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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Abstract plugin manager
 */
public abstract class AbstractNativeBuildPluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNativeBuildPluginManager.class);

    private static final List<String> NATIVE_IMAGE_BUNDLE_DIRS_TO_COPY = Arrays.asList(
            "/input/",
            "/META-INF/"
    );

    private static final AbstractNormalizer jsonNormalizer = new JsonNormalizer();
    private static final AbstractNormalizer propertiesNormalizer = new PropertiesNormalizer();

    // Tracks mojo executions that have already been processed to avoid duplicate bundle extraction and logging
    private final Map<String, Boolean> processedExecutions = Collections.synchronizedMap(new HashMap<>());

    protected abstract String getPluginName();

    protected abstract List<String> getCacheableGoals();

    protected abstract AbstractNativeBuildCachingConfiguration getConfiguration(MavenProject project, String localMavenRepoDir);

    protected abstract void configureExtraPlugins(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration);

    protected abstract void configureMojoInputs(MojoMetadataProvider.Context.Inputs inputs, AbstractNativeBuildCachingConfiguration configuration);

    protected abstract void configureMojoOutputs(MojoMetadataProvider.Context.Outputs outputs, AbstractNativeBuildCachingConfiguration configuration);

    protected void configureBuildCache(BuildCacheApi buildCache, MojoMetadataProvider.Context context) {
        AbstractNativeBuildCachingConfiguration configuration = getConfiguration(context.getProject(), context.getSession().getLocalRepository().getBasedir());

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
            boolean bundleFound = processedExecutions.computeIfAbsent(executionId, key -> {
                LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage(configuration.toString()));
                return extractAndTransformBundle(configuration);
            });

            // Configure inputs/outputs when a bundle is found (required on every invocation by the Develocity API)
            if (bundleFound) {
                configureInputs(context, configuration);
                configureOutputs(context, configuration);
            }
        }
    }

    private boolean extractAndTransformBundle(AbstractNativeBuildCachingConfiguration configuration) {
        String targetDir = configuration.getBuildDir();

        AtomicBoolean isBundleFound = new AtomicBoolean(false);
        try (Stream<Path> walkStream = Files.walk(Paths.get(targetDir))) {
            walkStream.filter(p -> p.toFile().isFile()).forEach(f -> {
                if (f.toString().endsWith(configuration.getBundleFile())) {
                    LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Found native image bundle"));
                    isBundleFound.set(true);

                    try (FileSystem zipFs = FileSystems.newFileSystem(f, (ClassLoader) null)) {
                        if (zipFs != null) {
                            for (Path rootDir : zipFs.getRootDirectories()) {
                                Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
                                    @Override
                                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                        Path relativePath = rootDir.relativize(file);
                                        for (String nativeImageBundleDir : NATIVE_IMAGE_BUNDLE_DIRS_TO_COPY) {
                                            if (file.startsWith(nativeImageBundleDir)) {
                                                Path targetPath = Paths.get(configuration.getWorkDir()).resolve(relativePath.toString());
                                                Files.createDirectories(targetPath.getParent());
                                                Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                                LOGGER.trace(AbstractNativeBuildCachingConfiguration.getLogMessage("Extracted file " + file));
                                            }
                                        }
                                        return FileVisitResult.CONTINUE;
                                    }
                                });
                            }

                            transformNativeImageBundleInputs(configuration.getWorkDir());
                            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Native build cache configured"));
                        } else {
                            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Native image bundle is empty"));
                        }
                    } catch (IOException e) {
                        LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage(e.toString()));
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (IOException e) {
            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage(e.toString()));
            throw new RuntimeException(e);
        }

        if (!isBundleFound.get()) {
            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage("Native image bundle not found"));
        }

        return isBundleFound.get();
    }

    private void transformNativeImageBundleInputs(String nativeImageBundleDir) {
        jsonNormalizer.normalize(nativeImageBundleDir + "input/stage/build.json");
        jsonNormalizer.normalize(nativeImageBundleDir + "input/stage/environment.json");
        jsonNormalizer.normalize(nativeImageBundleDir + "input/stage/path_canonicalizations.json");
        jsonNormalizer.normalize(nativeImageBundleDir + "input/stage/path_substitutions.json");
        propertiesNormalizer.normalize(nativeImageBundleDir + "META-INF/nibundle.properties");
    }

    private void configureInputs(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
        context.inputs(inputs -> {
            inputs.fileSet("nibManifest", new File(configuration.getWorkDir() + "META-INF/MANIFEST.MF"), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
            inputs.fileSet("nibProperties", new File(configuration.getWorkDir() + "META-INF/nibundle.properties" + AbstractNormalizer.TRANSFORMED_SUFFIX), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
            inputs.fileSet("nibAuxiliary", new File(configuration.getWorkDir() + "input/auxiliary"), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));
            inputs.fileSet("nibClasses", new File(configuration.getWorkDir() + "input/classes"), fileSet -> fileSet.exclude("**/" + context.getProject().getBuild().getFinalName() + "-runner.jar").normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
            inputs.fileSet("nibStage", new File(configuration.getWorkDir() + "input/stage"), fileSet -> fileSet.exclude("**/*.json").normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));
            inputs.fileSet("nibStageRun", new File(configuration.getWorkDir() + "input/stage/run.json"), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));

            try {
                List<String> compileClasspathElements = context.getProject().getCompileClasspathElements();
                inputs.fileSet("nativeBuildCachingCompileClasspath", compileClasspathElements, fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
            } catch (DependencyResolutionRequiredException e) {
                throw new IllegalStateException("Classpath can't be resolved");
            }

            configureMojoInputs(inputs, configuration);
        });
    }

    private void configureOutputs(MojoMetadataProvider.Context context, AbstractNativeBuildCachingConfiguration configuration) {
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

            configureMojoOutputs(outputs, configuration);
        });
    }

}
