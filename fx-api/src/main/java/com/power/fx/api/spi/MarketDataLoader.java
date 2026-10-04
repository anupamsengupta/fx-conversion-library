package com.power.fx.api.spi;

import com.power.fx.api.ingest.MarketDataChangeSet;
import com.power.fx.api.ingest.MarketWatermark;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.LocalDateRange;
import com.power.fx.api.model.MarketSnapshotPayload;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Host-implemented market-data loader (FS S6.3).
 *
 * @see "Tech spec S5.2"
 */
public interface MarketDataLoader {

    Optional<MarketSnapshotPayload> loadSnapshot(String marketSnapshotId);

    List<FixingVersion> loadFixings(
            Set<String> sourceCodes, Set<CurrencyPair> pairs, LocalDateRange dateRange, Instant knowledgeCut);

    MarketDataChangeSet loadChangesSince(MarketWatermark watermark);
}
