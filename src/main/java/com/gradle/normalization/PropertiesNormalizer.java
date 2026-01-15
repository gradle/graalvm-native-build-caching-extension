package com.gradle.normalization;

import java.util.Arrays;
import java.util.List;

/**
 * Properties file normalizer
 */
public class PropertiesNormalizer extends AbstractNormalizer {

    private static final List<String> NATIVE_IMAGE_BUNDLE_IGNORED_PROPERTIES = Arrays.asList(
            "#",
            "BundleFileCreationTimestamp",
            "ImageBuildID"
    );

    boolean isLineIgnored(String line) {
        return NATIVE_IMAGE_BUNDLE_IGNORED_PROPERTIES.stream().anyMatch(line::startsWith);
    }

    String getSplitLineRegex() {
        return "@DONOTSPLIT@";
    }

    String transform(String token) {
        return token;
    }

}
