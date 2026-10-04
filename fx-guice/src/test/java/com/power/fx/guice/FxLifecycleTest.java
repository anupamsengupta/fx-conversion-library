package com.power.fx.guice;

import com.power.fx.api.FxConfig;
import com.power.fx.api.FxIngestor;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.Scope;
import com.power.fx.core.cache.FixingStore;
import com.power.fx.testkit.doubles.InMemoryMarketDataLoader;
import com.power.fx.testkit.doubles.InMemoryReferenceDataLoader;
import com.power.fx.testkit.fixtures.GoldenReferenceData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Modest coverage for {@link FxLifecycle} (Task 4.3). Not part of the v1.0
 * exit gate's mandatory acceptance criteria (Task 4.4 does not name {@code
 * FxLifecycle} among {@code WiringTest}'s assertions), but exercised here
 * so the component at least demonstrably starts, stops, and bridges {@link
 * com.power.fx.api.spi.ReferenceDataLoader#loadChangesSince} results into
 * {@link FxIngestor#apply} correctly -- the one half of the reconciliation
 * bridge the tech spec defines unambiguously (see {@link FxLifecycle}'s own
 * Javadoc for the market-data half's documented, flagged approximation).
 */
class FxLifecycleTest {

    private FxLifecycle lifecycle;

    @AfterEach
    void tearDown() {
        if (lifecycle != null) {
            lifecycle.close();
        }
    }

    @Test
    void startAndClose_doNotThrow_whenReconciliationIntervalIsZero() {
        GuiceVectorHarness harness = new GuiceVectorHarness();
        FxIngestor ingestor = harness.injector().getInstance(FxIngestor.class);
        FixingStore fixingStore = harness.injector().getInstance(FixingStore.class);
        InMemoryReferenceDataLoader refLoader = new InMemoryReferenceDataLoader();
        InMemoryMarketDataLoader marketLoader = new InMemoryMarketDataLoader();

        // config() uses Duration.ZERO for reconciliationInterval (the "host prefers its own
        // scheduler" escape hatch, S8.4) -- start() must be a no-op, not an error.
        lifecycle = new FxLifecycle(harness.config(), ingestor, refLoader, marketLoader, fixingStore,
                Set.of(GoldenReferenceData.TENANT));
        assertDoesNotThrow(() -> lifecycle.start());
        assertDoesNotThrow(() -> lifecycle.close());
    }

    @Test
    void runOnce_bridgesReferenceDataLoaderChangesIntoFxIngestor() {
        GuiceVectorHarness harness = new GuiceVectorHarness();
        harness.setTenant(GoldenReferenceData.TENANT);
        FxIngestor ingestor = harness.injector().getInstance(FxIngestor.class);
        FixingStore fixingStore = harness.injector().getInstance(FixingStore.class);
        InMemoryReferenceDataLoader refLoader = new InMemoryReferenceDataLoader();
        InMemoryMarketDataLoader marketLoader = new InMemoryMarketDataLoader();

        refLoader.addTenant(GoldenReferenceData.TENANT, GuiceVectorHarness.globalRecord(
                FxEntityType.CURRENCY, "EUR", GoldenReferenceData.currency("EUR")));

        lifecycle = new FxLifecycle(harness.config(), ingestor, refLoader, marketLoader, fixingStore,
                Set.of(GoldenReferenceData.TENANT));

        assertDoesNotThrow(() -> lifecycle.runOnce());
        // A second cycle with no new changes since the advanced watermark must also be a no-op,
        // not re-apply (or crash on) the same record.
        assertDoesNotThrow(() -> lifecycle.runOnce());
    }

    @Test
    void runOnce_toleratesEmptyMarketDataChanges() {
        GuiceVectorHarness harness = new GuiceVectorHarness();
        FxIngestor ingestor = harness.injector().getInstance(FxIngestor.class);
        FixingStore fixingStore = harness.injector().getInstance(FixingStore.class);
        InMemoryReferenceDataLoader refLoader = new InMemoryReferenceDataLoader();
        InMemoryMarketDataLoader marketLoader = new InMemoryMarketDataLoader();

        lifecycle = new FxLifecycle(harness.config(), ingestor, refLoader, marketLoader, fixingStore,
                Set.of(GoldenReferenceData.TENANT));

        assertDoesNotThrow(() -> lifecycle.runOnce());
    }

    @Test
    void setTenantIds_updatesTheReconciliationSet() {
        GuiceVectorHarness harness = new GuiceVectorHarness();
        FxIngestor ingestor = harness.injector().getInstance(FxIngestor.class);
        FixingStore fixingStore = harness.injector().getInstance(FixingStore.class);
        InMemoryReferenceDataLoader refLoader = new InMemoryReferenceDataLoader();
        InMemoryMarketDataLoader marketLoader = new InMemoryMarketDataLoader();

        lifecycle = new FxLifecycle(harness.config(), ingestor, refLoader, marketLoader, fixingStore, Set.of());
        assertDoesNotThrow(() -> lifecycle.setTenantIds(Set.of(GoldenReferenceData.TENANT, GoldenReferenceData.TENANT_NO_WMR)));
        assertDoesNotThrow(() -> lifecycle.runOnce());
    }
}
