package com.power.fx.core.memo;

import com.power.fx.core.rate.RateQuote;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-pinned-snapshot memoisation of resolved rates (S6.15). Lives on
 * {@code PinnedFxSnapshot}, not the converter: invalidation is structural
 * (a new pin is a new, empty memo), and cross-tenant reuse is impossible
 * because the snapshot is tenant-bound.
 *
 * <p><strong>Documented simplification:</strong> the tech spec recommends
 * a "16-way striped-lock LRU" for maximum read concurrency. This
 * implementation uses one {@link java.util.concurrent.locks.ReentrantLock}
 * guarding a {@link LinkedHashMap}, which is correct (bounded,
 * LRU-evicting, never held across a computation) but not striped; under
 * heavy concurrent contention a 16-way striped implementation would have
 * higher throughput. Correctness never depends on the memo (a property
 * test asserts identical results memo-on and memo-off), so this trade-off
 * does not affect any observable result, only peak concurrent throughput.
 *
 * <p><strong>D-09 bytecode-scan note:</strong> {@code LinkedHashMap}'s
 * {@code (initialCapacity, loadFactor, accessOrder)} constructor -- the
 * one overload that supports true LRU (access-order) eviction -- requires
 * a {@code float} load-factor literal, which the D-09 bytecode scan
 * (AR-05) forbids even as a JDK API parameter. This implementation
 * therefore uses the plain no-arg (insertion-order) constructor and
 * simulates access-order manually: {@link #get} removes and re-inserts a
 * hit so it becomes the most-recently-used entry for {@code
 * removeEldestEntry}'s insertion-order-based eviction. Behaviourally
 * equivalent LRU semantics, zero float bytecode in this module.
 *
 * @see "Tech spec S6.15"
 */
public final class ResolutionMemo {

    private final int maxEntries;
    private final Object lock = new Object();
    private final LinkedHashMap<MemoKey, RateQuote> map;

    public ResolutionMemo(int maxEntries) {
        this.maxEntries = maxEntries;
        this.map = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<MemoKey, RateQuote> eldest) {
                return size() > ResolutionMemo.this.maxEntries;
            }
        };
    }

    public Optional<RateQuote> get(MemoKey key) {
        if (maxEntries <= 0) {
            return Optional.empty();
        }
        synchronized (lock) {
            RateQuote value = map.remove(key);
            if (value == null) {
                return Optional.empty();
            }
            map.put(key, value); // re-insert at the end: simulates access-order for LRU eviction
            return Optional.of(value);
        }
    }

    public void put(MemoKey key, RateQuote value) {
        if (maxEntries <= 0) {
            return;
        }
        synchronized (lock) {
            map.put(key, value);
        }
    }

    public int size() {
        synchronized (lock) {
            return map.size();
        }
    }
}
