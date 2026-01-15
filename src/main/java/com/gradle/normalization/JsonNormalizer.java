package com.gradle.normalization;

import java.nio.file.Paths;

/**
 * Json normalizer
 */
public class JsonNormalizer extends AbstractNormalizer {

    boolean isLineIgnored(String line) {
        return false;
    }

    String getSplitLineRegex() {
        return "}?\\s?,\\s?";
    }

    String transform(String token) {
        return relativize(token);
    }

    private String relativize(String path) {
        return path.replace(Paths.get("").toAbsolutePath().toString(), "");
    }

}
