package com.power.fx.core.curve;

import com.power.fx.api.model.CurrencyPair;
import com.power.fx.core.snapshot.PinnedState;

/** Internal port (S5.4): lazily-built, snapshot-scoped forward curves (S6.9). */
public interface ForwardCurveCache {

    ForwardCurve curve(CurrencyPair pair, PinnedState state);
}
