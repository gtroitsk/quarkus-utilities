package org.quarkus.qe;

import java.util.Objects;

public final class VersionedCoordinates {
    private final Coordinates coordinates;
    private final String version;

    public static VersionedCoordinates parse(String coords) {
        String[] parts = coords.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected 'groupId:artifactId:version': " + coords);
        }

        return new VersionedCoordinates(parts[0], parts[1], parts[2]);
    }

    public VersionedCoordinates(String groupId, String artifactId, String version) {
        this.coordinates = new Coordinates(groupId, artifactId);
        this.version = Objects.requireNonNull(version, "Version must be set for " + groupId + ":" + artifactId);
    }

    public Coordinates withoutVersion() {
        return coordinates;
    }

    public String groupId() {
        return coordinates.groupId();
    }

    public String artifactId() {
        return coordinates.artifactId();
    }

    public String version() {
        return version;
    }

    @Override
    public String toString() {
        return coordinates + ":" + version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionedCoordinates)) return false;
        VersionedCoordinates that = (VersionedCoordinates) o;
        return Objects.equals(coordinates, that.coordinates) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coordinates, version);
    }
}
