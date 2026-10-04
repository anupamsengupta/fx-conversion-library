package com.power.fx.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The library version constant, stamped into {@code Lineage.libraryVersion}
 * and into {@code inputsHash} (A-06).
 *
 * <p><strong>Provisional mechanism (TI-05, unresolved):</strong> the tech
 * spec leaves the choice between Maven resource filtering and a
 * template-generated class open. This implementation uses Maven resource
 * filtering: {@code src/main/resources/fx-version.properties} contains
 * {@code fx.version=${project.version}}, substituted by the
 * maven-resources-plugin at build time and read here, once, at class
 * initialisation, via {@link Class#getResourceAsStream(String)}.
 *
 * <p>This satisfies both of TI-05's stated requirements for the value
 * this constant carries:
 * <ol>
 *   <li>it differs between released artifacts, because each release bumps
 *       {@code project.version} in the reactor POM; and
 *   <li>it is stable between a local build and a CI build of the same
 *       commit, because the POM version at a given commit is identical
 *       regardless of which machine runs the build -- unlike, say, a
 *       build timestamp, which this implementation deliberately does
 *       not use.
 * </ol>
 *
 * <p>Reading the resource happens exactly once, at class-initialisation
 * time, not per conversion -- this is not I/O on the resolution path
 * (D-01 is about the per-request resolution path, not JVM class loading).
 *
 * <p>This mechanism is explicitly provisional pending TI-05's final
 * resolution; a future change to a generated-class mechanism would be a
 * drop-in replacement for this class's single {@link #VALUE} constant
 * and would not require any caller change.
 *
 * @see "Tech spec A-06, TI-05"
 */
public final class FxVersion {

    /** The library version string. See class Javadoc for the TI-05 caveat. */
    public static final String VALUE = load();

    private FxVersion() {
    }

    private static String load() {
        try (InputStream in = FxVersion.class.getResourceAsStream("/fx-version.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "fx-version.properties not found on the classpath; "
                                + "check fx-api's Maven resource filtering configuration");
            }
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("fx.version");
            if (version == null || version.isBlank() || version.startsWith("${")) {
                throw new IllegalStateException(
                        "fx-version.properties did not contain a filtered fx.version value: " + version);
            }
            return version;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read fx-version.properties", e);
        }
    }
}
