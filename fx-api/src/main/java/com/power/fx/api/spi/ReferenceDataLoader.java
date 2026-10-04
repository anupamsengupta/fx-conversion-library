package com.power.fx.api.spi;

import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.model.FxEntityType;

import java.time.Instant;
import java.util.List;

/**
 * Host-implemented reference-data loader, shaped per UOM FS v2.0 and
 * re-declared here rather than shared, to keep {@code fx-api} JDK-only
 * (A-08 rationale).
 *
 * @see "Tech spec S5.2"
 */
public interface ReferenceDataLoader {

    List<FxIngestRecord> loadGlobal();

    List<FxIngestRecord> loadTenant(String tenantId);

    List<FxIngestRecord> loadChangesSince(String tenantId, Instant watermark);

    List<FxIngestRecord> loadKey(String tenantId, FxEntityType entityType, String naturalKey);
}
