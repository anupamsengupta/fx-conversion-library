package com.power.fx.cdm;

import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.Scope;

import java.util.Map;

/**
 * Maps a {@link CdmFxEvent} carrying {@link com.power.fx.api.model.FxEntityType#FIXING}
 * into a {@link FixingVersion}.
 *
 * <p><strong>Scaffolded and blocked on TI-01</strong> (implementation
 * plan Phase 3a Task 3a.3): field names below are illustrative only, see
 * {@link CdmFxEvent}'s Javadoc.
 *
 * <p>Pure function over {@code Map<String, String>}; no transport, no
 * I/O, no dependency on {@code fx-core}.
 *
 * @see "Implementation plan Phase 3a Task 3a.3; tech spec S6.17; TI-01"
 */
final class CdmFixingMapper {

    private CdmFixingMapper() {
    }

    static FixingVersion map(CdmFxEvent event) {
        Map<String, String> f = event.fields();
        Scope scope = CdmFields.parseEnum(event.scope(), Scope.class, "scope");
        CurrencyPair pair = new CurrencyPair(CdmFields.requireCurrency(f, "base"), CdmFields.requireCurrency(f, "quote"));
        return new FixingVersion(
                scope,
                event.tenantId(),
                CdmFields.require(f, "sourceCode"),
                pair,
                CdmFields.requireDate(f, "fixingDate"),
                CdmFields.optional(f, "cutoff"),
                CdmFields.requireDecimal(f, "value"),
                CdmFields.requireEnum(f, "fixingStatus", FixingStatus.class),
                CdmFields.requireInstant(f, "recordedAt"),
                CdmFields.require(f, "versionId"),
                CdmFields.optional(f, "correctionOf"),
                CdmFields.optionalDate(f, "valueDate"));
    }
}
