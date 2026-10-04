package com.power.fx.api.result;

import com.power.fx.api.model.PdrRef;
import com.power.fx.api.model.SignOffStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Per-leg lineage: the identity of the pinned state, policy and library
 * identity, inputs, and a lazily computed replay/audit hash.
 *
 * <p>A final class, not a record (A-13): {@link #inputsHash()} is a
 * {@code SHA-256} hex digest over the RFC 8785 canonical JSON form of a
 * projection of the request (S6.13), and must be computed lazily so a
 * hot {@code convert()} call that never asks for lineage pays no
 * canonicalisation or hashing cost (S10a.1, Risk R2).
 *
 * <p><strong>Design seam (new gap #1, implementation plan Section 9):</strong>
 * the components that actually build the canonical form
 * ({@code CanonicalJson}/{@code InputsHasher}) are {@code fx-core}
 * components (S6.1, package {@code core.lineage}), but {@code fx-api}
 * must never depend on {@code fx-core} (Appendix A: the dependency
 * direction is strictly {@code fx-core -> fx-api}). This class therefore
 * accepts a {@link Supplier} of the pre-built canonical-form string from
 * its caller ({@code fx-core}'s {@code LineageBuilder}); {@code Lineage}
 * itself owns only the final {@code MessageDigest.getInstance("SHA-256")}
 * step, which is JDK-only and legal in {@code fx-api} under the same
 * AR-02 allowlist reasoning that permits it in {@code fx-core} (A-12).
 * Using a {@code Supplier<String>} rather than a pre-built {@code String}
 * is deliberate: a plain string argument would force the caller to
 * canonicalise eagerly, before {@code Lineage} even exists, defeating the
 * end-to-end laziness A-13 requires.
 *
 * @see "Tech spec S4.9, A-13"
 */
public final class Lineage {

    private final String tenantId;
    private final String marketSnapshotId;
    private final Instant fixingKnowledgeCut;
    private final long referenceGeneration;
    private final long fixingGeneration;
    private final SignOffStatus snapshotSignOff;

    private final String policyId;
    private final int policyVersion;
    private final String inlinePolicyDigest;

    private final String libraryVersion;
    private final String apiSchemaVersion;

    private final PdrRef pdrRef;
    private final SortedMap<String, String> calendarVersions;
    private final ReplayableRequest replayKey;

    private final Supplier<String> canonicalForm;

    /**
     * Lazily computed, memoised SHA-256 hex digest. Single-check idiom
     * (Effective Java Item 83): deliberately not {@code volatile} --
     * recomputation under a benign data race is idempotent and cheap,
     * and this field is {@code null} immediately after construction by
     * design.
     */
    private String inputsHash;

    public Lineage(
            String tenantId,
            String marketSnapshotId,
            Instant fixingKnowledgeCut,
            long referenceGeneration,
            long fixingGeneration,
            SignOffStatus snapshotSignOff,
            String policyId,
            int policyVersion,
            String inlinePolicyDigest,
            String libraryVersion,
            String apiSchemaVersion,
            PdrRef pdrRef,
            SortedMap<String, String> calendarVersions,
            ReplayableRequest replayKey,
            Supplier<String> canonicalForm) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.marketSnapshotId = Objects.requireNonNull(marketSnapshotId, "marketSnapshotId must not be null");
        this.fixingKnowledgeCut = Objects.requireNonNull(fixingKnowledgeCut, "fixingKnowledgeCut must not be null");
        this.referenceGeneration = referenceGeneration;
        this.fixingGeneration = fixingGeneration;
        this.snapshotSignOff = Objects.requireNonNull(snapshotSignOff, "snapshotSignOff must not be null");
        this.policyId = Objects.requireNonNull(policyId, "policyId must not be null");
        this.policyVersion = policyVersion;
        this.inlinePolicyDigest = inlinePolicyDigest;
        this.libraryVersion = Objects.requireNonNull(libraryVersion, "libraryVersion must not be null");
        this.apiSchemaVersion = Objects.requireNonNull(apiSchemaVersion, "apiSchemaVersion must not be null");
        this.pdrRef = pdrRef;
        this.calendarVersions = calendarVersions == null
                ? Collections.unmodifiableSortedMap(new TreeMap<>())
                : Collections.unmodifiableSortedMap(new TreeMap<>(calendarVersions));
        this.replayKey = Objects.requireNonNull(replayKey, "replayKey must not be null");
        this.canonicalForm = Objects.requireNonNull(canonicalForm, "canonicalForm must not be null");
    }

    public String tenantId() {
        return tenantId;
    }

    public String marketSnapshotId() {
        return marketSnapshotId;
    }

    public Instant fixingKnowledgeCut() {
        return fixingKnowledgeCut;
    }

    public long referenceGeneration() {
        return referenceGeneration;
    }

    public long fixingGeneration() {
        return fixingGeneration;
    }

    public SignOffStatus snapshotSignOff() {
        return snapshotSignOff;
    }

    public String policyId() {
        return policyId;
    }

    public int policyVersion() {
        return policyVersion;
    }

    /** Nullable: only set when the policy was supplied inline. */
    public String inlinePolicyDigest() {
        return inlinePolicyDigest;
    }

    public String libraryVersion() {
        return libraryVersion;
    }

    public String apiSchemaVersion() {
        return apiSchemaVersion;
    }

    public Optional<PdrRef> pdrRef() {
        return Optional.ofNullable(pdrRef);
    }

    public SortedMap<String, String> calendarVersions() {
        return calendarVersions;
    }

    public ReplayableRequest replayKey() {
        return replayKey;
    }

    /**
     * SHA-256 hex digest over the RFC 8785 canonical form, computed
     * lazily on first access and memoised thereafter (A-13).
     */
    public String inputsHash() {
        String computed = inputsHash;
        if (computed == null) {
            computed = sha256Hex(canonicalForm.get());
            inputsHash = computed;
        }
        return computed;
    }

    private static String sha256Hex(String canonical) {
        Objects.requireNonNull(canonical, "canonical form supplier must not return null");
        try {
            // A-12: SHA-256 is a JDK-mandated algorithm, bit-deterministic, not I/O.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
