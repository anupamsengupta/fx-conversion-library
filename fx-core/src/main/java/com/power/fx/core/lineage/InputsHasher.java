package com.power.fx.core.lineage;

import com.power.fx.api.model.PdrRef;
import com.power.fx.api.result.ReplayableRequest;
import com.power.fx.core.ResolvedPolicy;

import java.time.Instant;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Builds the canonical-form string over exactly the S6.13 member table:
 * {@code apiSchemaVersion}, {@code libraryVersion}, {@code tenantId},
 * {@code marketSnapshotId}, {@code fixingKnowledgeCut},
 * {@code referenceGeneration}, {@code fixingGeneration}, {@code policy}
 * (the full {@code ResolvedPolicy}), {@code request} (the {@code
 * ReplayableRequest} projection), {@code pdrRef}. {@code requestId} is
 * excluded (correlation only).
 *
 * @see "Tech spec S6.13"
 */
public final class InputsHasher {

    private InputsHasher() {
    }

    public static String canonicalForm(String apiSchemaVersion, String libraryVersion, String tenantId,
            String marketSnapshotId, Instant fixingKnowledgeCut, long referenceGeneration, long fixingGeneration,
            ResolvedPolicy policy, ReplayableRequest request, PdrRef pdrRef) {
        SortedMap<String, Object> members = new TreeMap<>();
        members.put("apiSchemaVersion", apiSchemaVersion);
        members.put("libraryVersion", libraryVersion);
        members.put("tenantId", tenantId);
        members.put("marketSnapshotId", marketSnapshotId);
        members.put("fixingKnowledgeCut", fixingKnowledgeCut);
        members.put("referenceGeneration", String.valueOf(referenceGeneration));
        members.put("fixingGeneration", String.valueOf(fixingGeneration));
        members.put("policy", policy.policy());
        members.put("request", request);
        if (pdrRef != null) {
            members.put("pdrRef", pdrRef);
        }
        return CanonicalJson.canonicalizeMap(members);
    }
}
