package org.quarkus.qe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

public final class MavenRepo {
    private final Path rootDirectory;

    public static MavenRepo at(Path rootDirectory) {
        if (!Files.isDirectory(rootDirectory)) {
            throw new IllegalArgumentException("Directory expected: " + rootDirectory);
        }

        return new MavenRepo(rootDirectory.toAbsolutePath().normalize());
    }

    private MavenRepo(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public Path rootDirectory() {
        return rootDirectory;
    }

    public Stream<Artifact> artifacts() throws IOException {
        return Files.walk(rootDirectory)
                .filter(Files::isRegularFile)
                .map(path -> new Artifact(path, rootDirectory));
    }

    public Path expectedDirectoryFor(Coordinates coords) {
        Path groupDir = rootDirectory.resolve(
                coords.groupId().replace('.', File.separatorChar)
        );
        return groupDir.resolve(coords.artifactId());
    }

    public Path expectedDirectoryFor(VersionedCoordinates coords) {
        Path groupDir = rootDirectory.resolve(
                coords.groupId().replace('.', File.separatorChar)
        );
        Path artifactDir = groupDir.resolve(coords.artifactId());
        return artifactDir.resolve(coords.version());
    }

    // ---

    @Override
    public String toString() {
        return "Maven repository " + rootDirectory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MavenRepo)) return false;
        MavenRepo mavenRepo = (MavenRepo) o;
        return Objects.equals(rootDirectory, mavenRepo.rootDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootDirectory);
    }
}
