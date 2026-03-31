package com.gradle.configuration;

import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public abstract class AbstractNativeBuildCachingConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNativeBuildCachingConfiguration.class);

    private static final String NATIVE_BUILD_CACHING_WORK_DIR = "native-build-caching-extension";

    private static final String LOG_PREFIX = "[" + NATIVE_BUILD_CACHING_WORK_DIR + "] ";

    // Build directory
    private static final String DEVELOCITY_NATIVE_KEY_BUILD_DIR = "DEVELOCITY_NATIVE_BUILD_DIR";

    // Feature toggle key
    private static final String DEVELOCITY_NATIVE_KEY_CACHE_ENABLED = "DEVELOCITY_NATIVE_CACHE_ENABLED";

    // Fast mode to cache the prepare cache execution
    private static final String DEVELOCITY_NATIVE_KEY_CACHE_FAST_MODE_ENABLED = "DEVELOCITY_NATIVE_CACHE_FAST_MODE_ENABLED";

    // Configuration file location key
    private static final String DEVELOCITY_NATIVE_KEY_CONFIG_FILE = "DEVELOCITY_NATIVE_CONFIG_FILE";

    // Native bundle file location key
    private static final String DEVELOCITY_NATIVE_KEY_BUNDLE_FILE = "DEVELOCITY_NATIVE_BUNDLE_FILE";

    // Extra output dirs key
    private static final String DEVELOCITY_NATIVE_KEY_EXTRA_OUTPUT_DIRS = "DEVELOCITY_NATIVE_EXTRA_OUTPUT_DIRS";

    // Extra output files key
    private static final String DEVELOCITY_NATIVE_KEY_EXTRA_OUTPUT_FILES = "DEVELOCITY_NATIVE_EXTRA_OUTPUT_FILES";

    // Default native image bundle name
    private static final String DEVELOCITY_NATIVE_DEFAULT_NIB = "bundle.nib";

    protected final Properties configuration = new Properties();

    protected abstract void initPluginSpecificDefaults(MavenProject project);

    protected AbstractNativeBuildCachingConfiguration(MavenProject project) {
        // loading default properties
        initWithDefaults(project);

        // loading plugin specific default properties
        initPluginSpecificDefaults(project);

        // override from environment
        overrideFromEnvironment();

        // override from Maven properties
        overrideFromMaven(project);

        // override from configuration file
        overrideFromConfigurationFile(project);
    }

    protected void initWithDefaults(MavenProject project) {
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_BUILD_DIR, project.getBuild().getDirectory());
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_CACHE_ENABLED, Boolean.TRUE.toString());
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_BUNDLE_FILE, DEVELOCITY_NATIVE_DEFAULT_NIB);
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_CONFIG_FILE, "");
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_CACHE_FAST_MODE_ENABLED, Boolean.FALSE.toString());
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_EXTRA_OUTPUT_DIRS, "");
        configuration.setProperty(DEVELOCITY_NATIVE_KEY_EXTRA_OUTPUT_FILES, "");
    }

    private void overrideFromEnvironment() {
        configuration.stringPropertyNames().forEach((key) -> {
            String envValue = System.getenv(key);
            if (envValue != null && !envValue.isEmpty()) {
                configuration.setProperty(key, envValue);
            }
        });
    }

    private void overrideFromMaven(MavenProject project) {
        configuration.stringPropertyNames().forEach((key) -> {
            String propertyKey = key.toLowerCase().replace("_", ".");
            // Check project properties first, then system properties (-D flags)
            String mavenProperty = project.getProperties().getProperty(propertyKey, "");
            if (mavenProperty == null || mavenProperty.isEmpty()) {
                mavenProperty = System.getProperty(propertyKey, "");
            }
            if (mavenProperty != null && !mavenProperty.isEmpty()) {
                configuration.setProperty(key, mavenProperty);
            }
        });
    }

    private void overrideFromConfigurationFile(MavenProject project) {
        String configurationFile = configuration.getProperty(DEVELOCITY_NATIVE_KEY_CONFIG_FILE);
        if(!configurationFile.isEmpty()) {
            configuration.putAll(loadProperties(project.getBasedir().getAbsolutePath(), configurationFile));
        }
    }

    public static String getLogMessage(String msg) {
        return LOG_PREFIX + msg;
    }

    /**
     * @return whether native build caching is enabled or not
     */
    public boolean isNativeBuildCachingEnabled() {
        return !Boolean.FALSE.toString().equals(configuration.get(DEVELOCITY_NATIVE_KEY_CACHE_ENABLED));
    }

    /**
     * @return whether native build caching fast mode is enabled or not
     */
    public boolean isNativeBuildCachingFastModeEnabled() {
        return Boolean.TRUE.toString().equals(configuration.get(DEVELOCITY_NATIVE_KEY_CACHE_FAST_MODE_ENABLED));
    }

    /**
     * @return build dir
     */
    public String getBuildDir() {
        return addTrailingSlashIfMissing(configuration.getProperty(DEVELOCITY_NATIVE_KEY_BUILD_DIR));
    }

    /**
     * @return work dir
     */
    public String getWorkDir() {
        return addTrailingSlashIfMissing(getBuildDir() + "/" + NATIVE_BUILD_CACHING_WORK_DIR);
    }

    private String addTrailingSlashIfMissing(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    /**
     * @return extra goal output directories to cache
     */
    public String getBundleFile() {
        return configuration.getProperty(DEVELOCITY_NATIVE_KEY_BUNDLE_FILE);
    }

    /**
     * @return extra goal output directories to cache
     */
    public List<String> getExtraOutputDirs() {
        return Arrays.asList(configuration.getProperty(DEVELOCITY_NATIVE_KEY_EXTRA_OUTPUT_DIRS).split(","));
    }

    /**
     * @return extra goal output files to cache
     */
    public List<String> getExtraOutputFiles() {
        return Arrays.asList(configuration.getProperty(DEVELOCITY_NATIVE_KEY_EXTRA_OUTPUT_FILES).split(","));
    }

    private Properties loadProperties(String baseDir, String propertyFile) {
        Properties props = new Properties();
        File configFile = new File(baseDir, propertyFile);

        if (configFile.exists()) {
            try (InputStream input = Files.newInputStream(configFile.toPath())) {
                props.load(input);
            } catch (IOException e) {
                LOGGER.error(getLogMessage("Error while loading " + propertyFile), e);
            }
        } else {
            LOGGER.debug(getLogMessage(propertyFile + " not found"));
        }

        return props;
    }

    @Override
    public String toString() {
        return configuration.toString();
    }
}
