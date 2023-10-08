package org.quarkus.qe;

import java.util.Objects;

public final class Coordinates {
    private final String groupId;
    private final String artifactId;

    public static Coordinates parse(String coords) {
        String[] parts = coords.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected 'groupId:artifactId': " + coords);
        }

        return new Coordinates(parts[0], parts[1]);
    }

    public Coordinates(String groupId, String artifactId) {
        this.groupId = Objects.requireNonNull(groupId, "Group ID must be set");
        this.artifactId = Objects.requireNonNull(artifactId, "Artifact ID must be set");
    }

    public String groupId() {
        return groupId;
    }

    public String artifactId() {
        return artifactId;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coordinates)) return false;
        Coordinates that = (Coordinates) o;
        return Objects.equals(groupId, that.groupId) &&
                Objects.equals(artifactId, that.artifactId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId);
    }
}
