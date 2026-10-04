package com.power.fx.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Host-supplied configuration for the library. See S4.12 for the full
 * field list and S10a.3 for the memory-budget analysis that corrects two
 * of S4.12's own stated literal defaults (see the field-level Javadoc
 * below).
 *
 * <p><strong>Note on {@code eagerCurveBuild}:</strong> S10a.2 and OQ-T08
 * both assert, in prose, that "a config option"/"a config flag exists"
 * for eager-vs-lazy forward-curve construction. No such field appears in
 * S4.12's own field list, and this record deliberately does not invent
 * one (implementation plan Section 7, new gap #3). The lazy
 * ({@code computeIfAbsent}) default of S6.9.3 applies unconditionally in
 * v1.0; a future spec amendment would add the field here if the
 * operational decision requires it.
 *
 * @see "Tech spec S4.12, S10a.3"
 */
public record FxConfig(
        BootstrapMode bootstrapMode,
        List<String> eagerTenantIds,
        Duration bootstrapTimeout,
        Duration reconciliationInterval,
        int fixingHotWindowYears,
        int retainedSnapshotsPerTenant,
        boolean memoEnabled,
        int memoMaxEntriesPerSnapshot,
        int forwardMemoMaxEntriesPerCurve,
        int decimalWorkingPrecision,
        boolean eagerInputsHash,
        boolean allowImplicitPin,
        boolean failOnStale,
        int maxFallbackStalenessDays) {

    /** Default per FS S6.3 / OQ-08. */
    public static final int DEFAULT_FIXING_HOT_WINDOW_YEARS = 3;

    /** Default per OQ-08. */
    public static final int DEFAULT_RETAINED_SNAPSHOTS_PER_TENANT = 8;

    /**
     * Default per S10a.3's memory-budget analysis, which supersedes the
     * stale {@code 50_000} literal that appears in S4.12's own field
     * table. S10a.3: "Resolution memo, 50,000 entries ... default
     * lowered to 20,000 with the same reasoning as above" (i.e. the same
     * per-snapshot {@code <= 50 MB} budget reasoning applied to {@link
     * #forwardMemoMaxEntriesPerCurve}). Do not "fix" this back to
     * {@code 50_000} -- S10a.3 is the corrective, binding analysis; S4.12's
     * literal is the document's own internal contradiction, not this
     * field's bug.
     */
    public static final int DEFAULT_MEMO_MAX_ENTRIES_PER_SNAPSHOT = 20_000;

    /**
     * Default per S10a.3's memory-budget analysis, which supersedes the
     * stale {@code 4_096} literal that appears in S4.12's own field
     * table. S10a.3: curve outright memos at the 4,096 default consume
     * ~65 MB per 200-pair snapshot, breaching the {@code <= 50 MB} FS S18
     * budget; "This spec therefore sets the default to 512" (~8 MB at
     * 200 pairs). Do not "fix" this back to {@code 4_096} -- S10a.3 is
     * the corrective, binding analysis; S4.12's literal is the
     * document's own internal contradiction, not this field's bug.
     */
    public static final int DEFAULT_FORWARD_MEMO_MAX_ENTRIES_PER_CURVE = 512;

    /** Default per Appendix D. */
    public static final int DEFAULT_DECIMAL_WORKING_PRECISION = 60;

    /** Default per A-13. */
    public static final boolean DEFAULT_EAGER_INPUTS_HASH = false;

    /** Default per S4.12 (never {@code true} for {@code RunMode.OFFICIAL}). */
    public static final boolean DEFAULT_FAIL_ON_STALE = false;

    /** Default per OQ-07 (placeholder; the library does not enforce it). */
    public static final int DEFAULT_MAX_FALLBACK_STALENESS_DAYS = 3;

    public FxConfig {
        Objects.requireNonNull(bootstrapMode, "bootstrapMode must not be null");
        Objects.requireNonNull(bootstrapTimeout, "bootstrapTimeout must not be null");
        Objects.requireNonNull(reconciliationInterval, "reconciliationInterval must not be null");
        eagerTenantIds = eagerTenantIds == null ? List.of() : List.copyOf(eagerTenantIds);
        if (fixingHotWindowYears <= 0) {
            throw new IllegalArgumentException("fixingHotWindowYears must be positive");
        }
        if (retainedSnapshotsPerTenant <= 0) {
            throw new IllegalArgumentException("retainedSnapshotsPerTenant must be positive");
        }
        if (memoMaxEntriesPerSnapshot < 0) {
            throw new IllegalArgumentException("memoMaxEntriesPerSnapshot must not be negative");
        }
        if (forwardMemoMaxEntriesPerCurve < 0) {
            throw new IllegalArgumentException("forwardMemoMaxEntriesPerCurve must not be negative");
        }
        if (decimalWorkingPrecision <= 0) {
            throw new IllegalArgumentException("decimalWorkingPrecision must be positive");
        }
        if (maxFallbackStalenessDays < 0) {
            throw new IllegalArgumentException("maxFallbackStalenessDays must not be negative");
        }
    }

    /** Whether tenants are bootstrapped eagerly at startup or lazily on first use. */
    public enum BootstrapMode {
        EAGER,
        LAZY
    }
}
