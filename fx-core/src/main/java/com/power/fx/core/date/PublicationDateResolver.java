package com.power.fx.core.date;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.NonPublicationDayHandling;
import com.power.fx.core.FxErrors;

import java.time.LocalDate;

/**
 * Resolves one raw fixing date against a publication {@link CalendarIndex}
 * per {@code nonPublicationDayHandling} (FS S7.1 step 3, tech spec S6.5
 * point 3). Vectors C01-C03.
 */
public final class PublicationDateResolver {

    public record Result(LocalDate resolvedDate, boolean adjusted, boolean skipped, FxReason reason) {
    }

    public Result resolve(LocalDate raw, CalendarIndex calendar, NonPublicationDayHandling handling) {
        if (calendar.isOpen(raw)) {
            return new Result(raw, false, false, null);
        }
        return switch (handling) {
            case USE_PREVIOUS -> new Result(calendar.strictlyBefore(raw), true, false, FxReason.DATE_RULE_ADJUSTED);
            case USE_NEXT -> new Result(calendar.strictlyAfter(raw), true, false, FxReason.DATE_RULE_ADJUSTED);
            case SKIP_OBSERVATION -> new Result(raw, false, true, FxReason.SKIPPED_OBSERVATION);
            case FAIL -> throw FxErrors.of(FxErrorCode.FX_E_NON_PUBLICATION_DATE,
                    "raw date " + raw + " is not a publication day and nonPublicationDayHandling = FAIL",
                    "rawDate", raw.toString());
        };
    }
}
