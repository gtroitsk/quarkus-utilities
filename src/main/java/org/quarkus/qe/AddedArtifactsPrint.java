package org.quarkus.qe;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.grep4j.core.model.Profile;
import org.grep4j.core.model.ProfileBuilder;
import org.grep4j.core.result.GrepResult;
import org.grep4j.core.result.GrepResults;
import org.jboss.logging.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.grep4j.core.Grep4j.*;


public class AddedArtifactsPrint {

    private static MavenRepo repo;
    private final static Logger LOG = org.jboss.logging.Logger.getLogger(AddedArtifactsPrint.class);
    private static final String DEPENDENCY_INDEX_PATH = AddedArtifactsPrint.class.getProtectionDomain().getCodeSource().getLocation().getPath() + "quarkus-dependency-index.txt";
    private static final char DEPENDENCY_INDEX_DELIMITER = '\t';

    AddedArtifactsPrint() {
        String mavenRepoDirStr = Objects.requireNonNull(System.getProperty("quarkus.maven.dir"),"System property 'quarkus.maven.dir' expected");
        repo = MavenRepo.at(Paths.get(mavenRepoDirStr).resolve("."));
    }

    public void printToFile() throws IOException {
        String currentWorkingDir = System.getProperty("user.dir");

        buildDependencyIndex();
        Multimap<Coordinates, String> allCoordinates = HashMultimap.create();
        try (Stream<Artifact> artifacts = repo.artifacts()) {
            artifacts
                    .filter(Artifact::isPom)
                    .peek(artifact -> LOG.debug(String.valueOf(artifact)))
                    .map(artifact -> artifact.asPom().versionedCoordinates())
                    .forEach(coords -> allCoordinates.put(coords.withoutVersion(), coords.version()));
        }

        try (FileWriter fileWriter = new FileWriter(currentWorkingDir + "/ADDED_ARTIFACTS.txt")) {
            PrintWriter printWriter = new PrintWriter(fileWriter);
            allCoordinates.keySet()
                    .forEach(coords -> {
                        if (hasAddedArtifact(coords.toString())) {
                            String version = allCoordinates.get(coords).toString();
                            printWriter.printf("\nDependants for %s - %s :: ADDED \n(%s)\n", coords, version, getDependentsInfo(coords));
                            System.out.printf("\nDependants for %s - %s :: ADDED \n(%s)\n", coords, version, getDependentsInfo(coords));
                        }
                    });
        }
    }

    private void buildDependencyIndex() throws IOException {
        System.out.println(DEPENDENCY_INDEX_PATH);
        try (PrintWriter writer = new PrintWriter(DEPENDENCY_INDEX_PATH, StandardCharsets.UTF_8.name())) {
            repo.artifacts()
                    .filter(Artifact::isPom)
                    .map(Artifact::asPom)
                    .flatMap(this::getPomDependencyIndexEntries)
                    .forEach(writer::println);
        }
    }

    private Stream<String> getPomDependencyIndexEntries(Artifact.Pom pom) {
        final VersionedCoordinates versionedCoordinates = pom.versionedCoordinates();
        return pom.getDependenciesGav()
                .map(dependency -> String.format("%s%s%s", versionedCoordinates, DEPENDENCY_INDEX_DELIMITER, dependency));
    }

    private String getDependentsInfo(Coordinates coordinates) {
        final String dependents = grepDependencyIndex(String.format("%s:", coordinates));
        return dependents.isEmpty() ? "no dependents found" : String.format("dependents: %s", dependents);
    }

    private String grepDependencyIndex(String pattern) {
        final Profile indexFile = ProfileBuilder.newBuilder()
                .name("POM file")
                .filePath(DEPENDENCY_INDEX_PATH)
                .onLocalhost()
                .build();

        final GrepResults grepResults = grep(constantExpression(DEPENDENCY_INDEX_DELIMITER + pattern), indexFile);

        return grepResults.stream()
                .map(GrepResult::getText)
                .flatMap(Pattern.compile("\\R")::splitAsStream)
                .map(line -> line.replace(String.valueOf(DEPENDENCY_INDEX_DELIMITER), " <- "))
                .collect(Collectors.joining(", \n"));
    }

    private boolean hasAddedArtifact(String artifact) {
        String currentWorkingDir = System.getProperty("user.dir");

        final Profile indexFile = ProfileBuilder.newBuilder()
                .name("Added artifacts")
                .filePath(currentWorkingDir + "/added_artifacts.txt")
                .onLocalhost()
                .build();

        String pattern = String.format("^%s$", artifact);
        final GrepResults grepResults = grep(regularExpression(pattern), indexFile);

        return grepResults.totalLines() > 0;
    }
}
