package com.power.fx.api;

import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An immutable, pinned handle on one market snapshot generation: tenant,
 * reference generation, fixing-store generation, market snapshot and
 * fixing knowledge cut (FS S6.4, D-03). Pattern #1 Value Object layered
 * over Pattern #14 Facade -- every {@link FxConverter} method issued
 * through this handle resolves against exactly this pinned state.
 *
 * @see "Tech spec S5.1"
 */
public interface FxSnapshot extends FxConverter {

    String tenantId();

    String marketSnapshotId();

    SnapshotKind kind();

    LocalDate asOfDate();

    Instant fixingKnowledgeCut();

    SignOffStatus signOffStatus();

    long referenceGeneration();

    long fixingGeneration();
}
