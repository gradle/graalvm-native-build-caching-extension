package com.gradle.normalization;

import com.gradle.configuration.AbstractNativeBuildCachingConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Abstract input file normalizer
 */
public abstract class AbstractNormalizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNormalizer.class);

    public static final String TRANSFORMED_SUFFIX = ".transformed";

    // The features entry is a csv list that can vary in order, ignoring the key allows to have sorted entries
    private static final List<String> IGNORED_TOKENS = Collections.singletonList(
            "--features="
    );

    abstract boolean isLineIgnored(String line);
    abstract String getSplitLineRegex();
    abstract String transform(String token);

    public void normalize(String inputFile) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(inputFile + TRANSFORMED_SUFFIX)); Scanner in = new Scanner(new File(inputFile))) {
            Set<String> tokens = new TreeSet<>();

            while (in.hasNextLine()) {
                String line = in.nextLine();
                if(!isLineIgnored(line)) {
                    String[] items = line.split(getSplitLineRegex());
                    Arrays.asList(items).forEach(item -> tokens.add(tidyJson(item)));
                }
            }

            for (String token : tokens) {
                out.write(transform(token));
                out.newLine();
            }
        } catch (IOException e) {
            LOGGER.debug(AbstractNativeBuildCachingConfiguration.getLogMessage(e.toString()));
            throw new RuntimeException(e);
        }
    }

    private String tidyJson(String in) {
        String out = in
                .replaceFirst("^\\[","")
                .replaceFirst("]$","")
                .replaceFirst("^\\{","")
                .replaceFirst("}$","")
                .replaceFirst("^\"","")
                .replaceFirst("\"$","");

        for(String ignoredToken : IGNORED_TOKENS) {
            out = out.replace(ignoredToken, "");
        }

        return out;
    }

}
