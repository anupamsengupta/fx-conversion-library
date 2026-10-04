package com.power.fx.core.date;

import com.power.fx.api.model.FixingSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Computes a fixing's nominal publication instant for lineage display and
 * {@code loadFixings} windows <strong>only</strong>. Never feeds rate
 * selection, which compares {@code recordedAt <= knowledgeCut} (both UTC
 * instants) and never compares a clock to a cut-off (D-01, S6.8, S10c.2).
 *
 * <p>On the spring-forward day a cut-off nominally inside the missing hour
 * has no instant; {@code ZonedDateTime.of(...)} shifts it forward by the
 * gap length. On the fall-back day a cut-off inside the repeated hour has
 * two instants; {@code ZonedDateTime.of(...)} selects the earlier
 * (summer-time) offset. Both are the JDK's own documented, deterministic
 * behaviour, accepted as-is (S10c.2).
 */
public final class CutoffInstantResolver {

    public Instant instantOf(LocalDate fixingDate, FixingSource source) {
        return ZonedDateTime.of(fixingDate, source.cutoffTime(), source.cutoffZone()).toInstant();
    }
}
