package com.power.fx.core.snapshot;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.ForwardPillar;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.api.model.SpotQuote;
import com.power.fx.core.curve.DiscountCurve;
import com.power.fx.core.curve.ForwardCurve;
import com.power.fx.core.entitlement.RightsSet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * An immutable market snapshot (S7.1.4, S6.2, D-03). Never mutated after
 * publication: a correction is a new {@code marketSnapshotId}, never an
 * update. {@link #curves} is the one legitimate mutable field -- a bounded,
 * idempotent, {@code computeIfAbsent}-populated cache whose contents are a
 * pure function of the (immutable) pillars, so concurrent construction can
 * at worst duplicate work, never produce divergent values (S7.1.4,
 * {@code ConcurrencyTest}).
 */
public final class MarketSnapshot {

    private final String marketSnapshotId;
    private final Scope scope;
    private final String tenantId;
    private final SnapshotKind kind;
    private final LocalDate asOfDate;
    private final Instant fixingKnowledgeCut;
    private final SignOffStatus signOffStatus;
    private final Map<CurrencyPair, SpotQuote> spots;
    private final Map<CurrencyPair, List<ForwardPillar>> pillars;
    private final Map<CurrencyCode, DiscountCurve> discountCurves;
    private final Map<CurrencyPair, RightsSet> sourceRights;
    private final ConcurrentHashMap<CurrencyPair, ForwardCurve> curves = new ConcurrentHashMap<>();

    public MarketSnapshot(String marketSnapshotId, Scope scope, String tenantId, SnapshotKind kind,
            LocalDate asOfDate, Instant fixingKnowledgeCut, SignOffStatus signOffStatus,
            Map<CurrencyPair, SpotQuote> spots, Map<CurrencyPair, List<ForwardPillar>> pillars,
            Map<CurrencyCode, DiscountCurve> discountCurves, Map<CurrencyPair, RightsSet> sourceRights) {
        this.marketSnapshotId = marketSnapshotId;
        this.scope = scope;
        this.tenantId = tenantId;
        this.kind = kind;
        this.asOfDate = asOfDate;
        this.fixingKnowledgeCut = fixingKnowledgeCut;
        this.signOffStatus = signOffStatus;
        this.spots = Map.copyOf(spots);
        this.pillars = Map.copyOf(pillars);
        this.discountCurves = Map.copyOf(discountCurves);
        this.sourceRights = Map.copyOf(sourceRights);
    }

    public String marketSnapshotId() {
        return marketSnapshotId;
    }

    public Scope scope() {
        return scope;
    }

    public String tenantId() {
        return tenantId;
    }

    public SnapshotKind kind() {
        return kind;
    }

    public LocalDate asOfDate() {
        return asOfDate;
    }

    public Instant fixingKnowledgeCut() {
        return fixingKnowledgeCut;
    }

    public SignOffStatus signOffStatus() {
        return signOffStatus;
    }

    public Optional<SpotQuote> spot(CurrencyPair pair) {
        return Optional.ofNullable(spots.get(pair));
    }

    public Map<CurrencyPair, SpotQuote> spots() {
        return spots;
    }

    public List<ForwardPillar> pillars(CurrencyPair pair) {
        return pillars.getOrDefault(pair, List.of());
    }

    public Optional<DiscountCurve> discountCurve(CurrencyCode currency) {
        return Optional.ofNullable(discountCurves.get(currency));
    }

    public RightsSet sourceRights(CurrencyPair pair) {
        return sourceRights.getOrDefault(pair, RightsSet.unrestricted());
    }

    /** Lazy, idempotent curve construction (S6.9.3, S7.1.4): built at most once per pair, no invalidation. */
    public ForwardCurve curve(CurrencyPair pair, Function<CurrencyPair, ForwardCurve> builder) {
        return curves.computeIfAbsent(pair, builder);
    }

    public boolean hasCurve(CurrencyPair pair) {
        return curves.containsKey(pair);
    }
}
