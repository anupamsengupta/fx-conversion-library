package com.power.fx.core;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.core.cache.ReferenceCatalogue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * {@code PolicyRef} resolved: {@code ById} through the catalogue at
 * {@code (valuationDate, knowledgeCut)}, or {@code Inline} verbatim
 * (S6.4). Immutable, and hashed <strong>in full</strong> into {@code
 * inputsHash} (via {@code Lineage.replayKey}/{@code
 * DefaultLineageBuilder}), so defaulting can never silently change a
 * replay.
 *
 * @see "Tech spec S6.4"
 */
public record ResolvedPolicy(FxPolicy policy, boolean inline, String policyId, int policyVersion) {

    public ResolvedPolicy {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");
    }

    public static ResolvedPolicy resolve(PolicyRef ref, ReferenceCatalogue tenant, LocalDate valuationDate, Instant knowledgeCut) {
        Objects.requireNonNull(ref, "ref must not be null");
        return switch (ref) {
            case PolicyRef.ById byId -> {
                FxPolicy p = tenant.fxPolicy(byId.policyId(), valuationDate, knowledgeCut)
                        .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                                "no policy found for policyId=" + byId.policyId() + " at " + valuationDate,
                                "policyId", byId.policyId()));
                yield new ResolvedPolicy(p, false, p.policyId(), p.version());
            }
            case PolicyRef.Inline inline -> new ResolvedPolicy(inline.policy(), true, inline.policy().policyId(),
                    inline.policy().version());
        };
    }

    /**
     * A copy of this resolved policy with {@code rateSourcePriority}
     * narrowed to {@code entitledSources} -- the design-seam resolution for
     * threading only entitled sources past stage 8 into {@code
     * PairResolver.route(...)} (S6.6 point 4, S5.4; see {@code
     * PairResolver}'s class Javadoc).
     */
    public ResolvedPolicy withSourcePriority(java.util.List<String> entitledSources) {
        FxPolicy p = policy;
        FxPolicy narrowed = new FxPolicy(p.envelope(), p.policyId(), p.version(), p.leg(), p.dateRule(), p.offset(),
                p.nonPublicationDayHandling(), p.rollConvention(), entitledSources, p.fixingVersionSelection(),
                p.futureDateTreatment(), p.spotAdjustment(), p.averaging(), p.fallbackChain(), p.allowMixedSources(),
                p.forceCrossVia(), p.rounding(), p.contractRate(), p.estimatedEventHandling(), p.allowOverrides());
        return new ResolvedPolicy(narrowed, inline, policyId, policyVersion);
    }
}
