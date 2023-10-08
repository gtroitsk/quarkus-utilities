package org.quarkus.qe;

import io.restassured.path.xml.XmlPath;
import org.apache.commons.lang3.StringUtils;
import org.grep4j.core.model.Profile;
import org.grep4j.core.model.ProfileBuilder;
import org.grep4j.core.result.GrepResult;
import org.grep4j.core.result.GrepResults;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.grep4j.core.Grep4j.constantExpression;
import static org.grep4j.core.Grep4j.grep;
import static org.grep4j.core.fluent.Dictionary.option;
import static org.grep4j.core.fluent.Dictionary.with;
import static org.grep4j.core.options.Option.extraLinesAfter;

public final class Artifact {
    private final Path file;
    private final String baseFileName;
    private final Path rootDirectory; // only for toString

    public Artifact(Path file, Path rootDirectory) {
        this.file = file;
        this.baseFileName = file.getFileName().toString();
        this.rootDirectory = rootDirectory;
    }

    public Path file() {
        return file;
    }

    public Path directory() {
        return file.getParent();
    }

    public String baseFileName() {
        return baseFileName;
    }

    public Path relativePath() {
        return rootDirectory.relativize(file);
    }

    public String artifactIdInParentDirName() {
        return file.getParent().getParent().getFileName().toString();
    }

    public String versionInParentDirName() {
        return file.getParent().getFileName().toString();
    }

    // ---

    public boolean isPom() {
        return baseFileName.endsWith(".pom");
    }

    public boolean isAnyJar() {
        return baseFileName.endsWith(".jar");
    }

    /**
     * Returns {@code true} if and only if {@link #baseFileName} is equal to {@code artifactId + "-" + version + ".jar"}
     * where {@code version} is the parent directory name of {@link #file} and {@code artifactId} is the parent's parent
     * directory name of {@link #file}. Note that this works only if {@link #file} is located inside of a standard Maven
     * repository.
     */
    public boolean isPlainJar() {
        final String version = file.getParent().getFileName().toString();
        final String artifactId = file.getParent().getParent().getFileName().toString();
        final String plainJarName = artifactId + "-" + version + ".jar";
        return baseFileName.equals(plainJarName);
    }

    public boolean isSourcesJar() {
        return baseFileName.endsWith("-sources.jar");
    }

    public boolean isJavadocJar() {
        return baseFileName.endsWith("-javadoc.jar");
    }

    public boolean isMd5() {
        return baseFileName.endsWith(".md5");
    }

    public boolean isSha1() {
        return baseFileName.endsWith(".sha1");
    }

    public boolean isXml() {
        return baseFileName.endsWith(".xml");
    }

    public AnyJar asAnyJar() {
        return new AnyJar();
    }

    public PlainJar asPlainJar() {
        return new PlainJar();
    }

    public Pom asPom() {
        return new Pom();
    }

    public Md5 asMd5() {
        return new Md5();
    }

    public Sha1 asSha1() {
        return new Sha1();
    }

    // ---

    public Path expectedMd5() {
        return directory().resolve(baseFileName + ".md5");
    }

    public Path expectedSha1() {
        return directory().resolve(baseFileName + ".sha1");
    }

    // ---

    @Override
    public String toString() {
        return "artifact " + rootDirectory.relativize(file);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Artifact)) return false;
        Artifact artifact = (Artifact) o;
        return Objects.equals(file, artifact.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file);
    }

    public final class AnyJar {
        private AnyJar() {
            if (!isAnyJar()) {
                throw new IllegalStateException("Not a JAR: " + Artifact.this);
            }
        }

        public boolean hasClasses() {
            try (ZipFile zip = new ZipFile(file.toFile())) {
                return zip.stream()
                        .anyMatch(entry -> entry.getName().endsWith(".class"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public boolean hasManifest() {
            try (JarFile jar = new JarFile(file.toFile(), false)) {
                Manifest manifest = jar.getManifest();
                return manifest != null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** Just checks if the JAR appears to be signed, doesn't perform any signature verification. */
        public boolean isSigned() {
            try (JarFile jar = new JarFile(file.toFile(), false)) {
                Manifest manifest = jar.getManifest();
                if (manifest == null) {
                    return false;
                }
                boolean signatureRelatedEntriesInManifest = manifest.getEntries()
                        .values()
                        .stream()
                        .flatMap(attributes -> attributes.values().stream())
                        .map(attributeKey -> attributeKey.toString().toLowerCase(Locale.ENGLISH))
                        .anyMatch(attributeKey -> attributeKey.contains("digest"));

                boolean signatureRelatedFilesInMetaInf = jar.stream()
                        .anyMatch(entry -> isSigningRelatedFileInMetaInf(entry.getName()));

                return signatureRelatedEntriesInManifest || signatureRelatedFilesInMetaInf;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private boolean isSigningRelatedFileInMetaInf(String fileName) {
            fileName = fileName.toUpperCase(Locale.ENGLISH);
            if (fileName.startsWith("META-INF/") || fileName.startsWith("/META-INF/")) {
                return fileName.endsWith(".SF") // signature file
                        || fileName.endsWith(".DSA") // signature block file
                        || fileName.endsWith(".RSA") // signature block file
                        || fileName.endsWith(".EC") // signature block file
                        ;
            }

            return false;
        }

        public Optional<String> getSpecificationVersionFromManifest() {
            return getMainAttributeFromManifest("Specification-Version");
        }

        public Optional<String> getImplementationVersionFromManifest() {
            return getMainAttributeFromManifest("Implementation-Version");
        }

        private Optional<String> getMainAttributeFromManifest(String attributeName) {
            try (JarFile jar = new JarFile(file.toFile(), false)) {
                Manifest manifest = jar.getManifest();
                if (manifest == null) {
                    return Optional.empty();
                }
                return Optional.ofNullable(manifest.getMainAttributes().getValue(attributeName));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public final class PlainJar {
        private PlainJar() {
            if (!isPlainJar()) {
                throw new IllegalStateException("Not a plain JAR: " + Artifact.this);
            }
        }

        public Path expectedSourceJar() {
            return directory().resolve(baseFileName().replace(".jar", "-sources.jar"));
        }

        public Path expectedPom() {
            return directory().resolve(baseFileName().replace(".jar", ".pom"));
        }

        public Artifact expectedPomAsArtifact() {
            return new Artifact(expectedPom(), rootDirectory);
        }

        public boolean hasClasses() {
            return asAnyJar().hasClasses();
        }

        /** @see AnyJar#isSigned() */
        public boolean isSigned() {
            return asAnyJar().isSigned();
        }

        /**
         * Looks for jar composed from 2 or more different jars.
         */
        public boolean isComposed(){
            Pattern manifestPomPattern = Pattern.compile("^META-INF\\/maven\\/.*pom\\.xml$");
            try(JarFile jar = new JarFile(file.toFile(), false)){
                long pomCount = jar.stream()
                        .map(jarEntry -> jarEntry.toString())
                        .filter(manifestPomPattern.asPredicate())
                        .count();
                return pomCount > 1;
            }catch(IOException e){
                throw new UncheckedIOException(e);
            }
        }
    }

    public final class Pom {
        private static final String DEPENDENCY_TAG = "<dependency>";
        private static final String DEPENDENCY_CLOSING_TAG = "</dependency>";
        private static final String GROUP_ID_TAG = "<groupId>";
        private static final String ARTIFACT_ID_TAG = "<artifactId>";
        private static final String VERSION_TAG = "<version>";
        private static final String CLOSING_TAG_START = "</";
        private static final String EXCLUSIONS_TAG = "<exclusions>";

        private Pom() {
            if (!isPom()) {
                throw new IllegalStateException("Not a POM: " + Artifact.this);
            }
        }

        public Path expectedPlainJar() {
            return directory().resolve(baseFileName().replace(".pom", ".jar"));
        }

        public String readText() {
            try {
                return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public VersionedCoordinates versionedCoordinates() {
            XmlPath xml = XmlPath.from(file.toFile());
            String artifactId = xml.getString("project.artifactId");
            String groupId = xml.getString("project.groupId");
            if (groupId == null || groupId.isEmpty()) {
                groupId = xml.getString("project.parent.groupId");
            }
            String version = xml.getString("project.version");
            if (version == null || version.isEmpty()) {
                version = xml.getString("project.parent.version");
            }

            return new VersionedCoordinates(groupId, artifactId, version);
        }

        public Coordinates coordinates() {
            return versionedCoordinates().withoutVersion();
        }

        public VersionedCoordinates parentVersionedCoordinates() {
            XmlPath xml = XmlPath.from(file.toFile());
            String artifactId = xml.getString("project.parent.artifactId");
            String groupId = xml.getString("project.parent.groupId");
            String version = xml.getString("project.parent.version");

            return new VersionedCoordinates(groupId, artifactId, version);
        }

        public Coordinates parentCoordinates() {
            return parentVersionedCoordinates().withoutVersion();
        }

        public Stream<String> getDependenciesGav() {
            final Profile pomFile = ProfileBuilder.newBuilder()
                    .name("POM file")
                    .filePath(file.toString())
                    .onLocalhost()
                    .build();
            final GrepResults grepResults = grep(
                    constantExpression("<dependency>"),
                    pomFile,
                    with(option(extraLinesAfter(6))));
            return grepResults.stream()
                    .map(GrepResult::getText)
                    .flatMap(this::extractDependenciesGav);
        }

        private Stream<String> extractDependenciesGav(String grepResult) {
            final String[] dependencies = StringUtils.substringsBetween(grepResult, DEPENDENCY_TAG, DEPENDENCY_CLOSING_TAG);
            if (dependencies == null) {
                return Stream.empty();
            }
            return Arrays.stream(dependencies)
                    .map(dep -> StringUtils.substringBefore(dep, EXCLUSIONS_TAG))
                    .map(dep -> {
                        final String groupId = StringUtils.substringBetween(dep, GROUP_ID_TAG, CLOSING_TAG_START);
                        final String artifactId = StringUtils.substringBetween(dep, ARTIFACT_ID_TAG, CLOSING_TAG_START);
                        final String version = StringUtils.substringBetween(dep, VERSION_TAG, CLOSING_TAG_START);
                        return String.format("%s:%s:%s", groupId, artifactId, version == null ? "" : version);
                    });
        }
    }

    public final class Md5 {
        private Md5() {
            if (!isMd5()) {
                throw new IllegalStateException("Not an MD5 file: " + Artifact.this);
            }
        }

        public Path expectedFile() {
            return directory().resolve(baseFileName().replace(".md5", ""));
        }
    }

    public final class Sha1 {
        private Sha1() {
            if (!isSha1()) {
                throw new IllegalStateException("Not an SHA1 file: " + Artifact.this);
            }
        }

        public Path expectedFile() {
            return directory().resolve(baseFileName().replace(".sha1", ""));
        }
    }
}
