package com.power.fx.core.cache;

import java.time.Instant;

/**
 * Internal port (S5.4, Pattern #21 Repository, in-memory) over the
 * reference-data generations for one tenant.
 *
 * @see "Tech spec S5.4"
 */
public interface ReferenceStore {

    /** The latest generation for {@code tenantId}, or {@code null} if never bootstrapped. */
    ReferenceCatalogue catalogue(String tenantId);

    /**
     * The catalogue to use for a pin at {@code knowledgeCut}. In this
     * implementation this is the same instance as {@link #catalogue},
     * because {@link ReferenceCatalogue} is itself bitemporal -- every
     * {@code resolve(date, cut)} call already filters out versions with
     * {@code recordedAt > cut} (Timeline resolution semantics). The
     * physical replay guarantee (A-15) comes from the caller pinning
     * *this specific generation instance* (which a later ingest cannot
     * mutate -- it produces a new instance), not from a separately trimmed
     * view.
     */
    ReferenceCatalogue cataloguePinned(String tenantId, Instant knowledgeCut);

    long generation(String tenantId);

    /** Atomic compare-and-set style swap: succeeds iff the current generation reference is unchanged. */
    boolean swap(String tenantId, ReferenceCatalogue next);

    ReferenceCatalogue global();

    boolean swapGlobal(ReferenceCatalogue next);
}
