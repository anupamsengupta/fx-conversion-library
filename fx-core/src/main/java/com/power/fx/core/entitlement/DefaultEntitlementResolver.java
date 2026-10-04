package com.power.fx.core.entitlement;

import com.power.fx.api.error.FxWarning;
import com.power.fx.api.error.FxWarningCode;
import com.power.fx.api.model.SourceEntitlement;
import com.power.fx.api.model.SourceRight;
import com.power.fx.core.snapshot.PinnedState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stage 8 (D-10, S6.6): filters {@code rateSourcePriority} to sources with
 * {@code VALUATION} rights for the tenant at the FX date. TENANT
 * entitlements overlay GLOBAL at the same key via {@code
 * ReferenceCatalogue.entitlement} (D-13). A source with no entitlement
 * record at all is treated as not entitled (fail closed).
 *
 * @see "Tech spec S6.6"
 */
public final class DefaultEntitlementResolver implements EntitlementResolver {

    @Override
    public FilteredSources filter(List<String> priority, LocalDate fxDate, PinnedState state) {
        List<String> allowed = new ArrayList<>();
        List<FxWarning> warnings = new ArrayList<>();
        for (String sourceCode : priority) {
            Optional<SourceEntitlement> ent = state.tenant().entitlement(sourceCode, fxDate, state.knowledgeCut());
            boolean entitled = ent.isPresent() && ent.get().rights().contains(SourceRight.VALUATION);
            if (entitled) {
                allowed.add(sourceCode);
            } else {
                warnings.add(new FxWarning(FxWarningCode.FX_W_SOURCE_SKIPPED_NOT_ENTITLED,
                        "source " + sourceCode + " skipped: no VALUATION entitlement for this tenant",
                        Map.of("sourceCode", sourceCode)));
            }
        }
        return new FilteredSources(allowed, warnings);
    }
}
