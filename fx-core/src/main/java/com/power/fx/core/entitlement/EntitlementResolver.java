package com.power.fx.core.entitlement;

import com.power.fx.core.snapshot.PinnedState;

import java.time.LocalDate;
import java.util.List;

/** Internal port (S5.4): stage 8 of the pipeline (D-10, S6.6). */
public interface EntitlementResolver {

    FilteredSources filter(List<String> priority, LocalDate fxDate, PinnedState state);
}
