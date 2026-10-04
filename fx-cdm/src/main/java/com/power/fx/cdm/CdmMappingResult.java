package com.power.fx.cdm;

import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.IngestRejection;

import java.util.Objects;

/**
 * The outcome of {@link CdmFxEventMapper#map(CdmFxEvent)}: either a
 * successfully mapped {@link FxIngestRecord}, or a rejection. Modelled as
 * a sealed closed-variant hierarchy (consistent with {@code fx-api}'s own
 * {@code FxRequest}/{@code FxResult} convention) specifically so that
 * malformed CDM input can be reported <em>without ever throwing</em>
 * (Task 3a.4's acceptance criterion).
 *
 * @see "Implementation plan Phase 3a Task 3a.4"
 */
public sealed interface CdmMappingResult permits CdmMappingResult.Mapped, CdmMappingResult.Rejected {

    record Mapped(FxIngestRecord record) implements CdmMappingResult {
        public Mapped {
            Objects.requireNonNull(record, "record must not be null");
        }
    }

    record Rejected(IngestRejection rejection) implements CdmMappingResult {
        public Rejected {
            Objects.requireNonNull(rejection, "rejection must not be null");
        }
    }
}
