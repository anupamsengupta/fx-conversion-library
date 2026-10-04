package com.power.fx.core.lineage;

import com.power.fx.api.FxVersion;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.result.Lineage;
import com.power.fx.api.result.ReplayableRequest;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.core.ResolvedContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Builds {@link Lineage} with a <strong>lazy</strong> canonical-form
 * supplier (A-13): a {@code convert()} call that never calls {@code
 * .inputsHash()} triggers zero canonicalisation/SHA-256 work, because the
 * {@link Supplier} passed to {@link Lineage}'s constructor is not invoked
 * until {@code Lineage.inputsHash()} itself is first called (Task 1.10's
 * seam, closed here per the plan's own framing).
 *
 * @see "Tech spec S6.13"
 */
public final class DefaultLineageBuilder implements LineageBuilder {

    private static final String API_SCHEMA_VERSION = "1.0";

    @Override
    public Lineage build(ResolvedContext ctx, CurrencyCode fromCcy, CurrencyCode toCcy, java.math.BigDecimal fromAmount,
            boolean isUnitPrice) {
        FxRequestContext rc = ctx.requestContext();
        SortedMap<String, String> calendarVersions = new TreeMap<>();

        String inlinePolicyDigest = ctx.policy().inline() ? sha256Hex(CanonicalJson.canonicalize(ctx.policy().policy())) : null;

        ReplayableRequest replayKey = new ReplayableRequest(rc.purpose(), rc.runMode(), rc.valuationDate(),
                rc.amountType(), rc.settlementAmountState(), rc.itemType(), rc.accountingUnitId(), fromCcy, toCcy,
                fromAmount, isUnitPrice, rc.policy(), rc.tradeDates(), rc.events(), rc.accountingDates(),
                rc.pricingSet(), rc.prices());

        com.power.fx.api.model.PdrRef pdrRef = rc.pricingSet() != null ? rc.pricingSet().ref() : null;

        return new Lineage(
                ctx.state().tenantId(),
                ctx.state().snapshot().marketSnapshotId(),
                ctx.state().knowledgeCut(),
                ctx.state().refGeneration(),
                ctx.state().fixingGeneration(),
                ctx.state().snapshot().signOffStatus(),
                ctx.policy().policyId(),
                ctx.policy().policyVersion(),
                inlinePolicyDigest,
                FxVersion.VALUE,
                API_SCHEMA_VERSION,
                pdrRef,
                calendarVersions,
                replayKey,
                () -> InputsHasher.canonicalForm(API_SCHEMA_VERSION, FxVersion.VALUE, ctx.state().tenantId(),
                        ctx.state().snapshot().marketSnapshotId(), ctx.state().knowledgeCut(), ctx.state().refGeneration(),
                        ctx.state().fixingGeneration(), ctx.policy(), replayKey, pdrRef));
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
