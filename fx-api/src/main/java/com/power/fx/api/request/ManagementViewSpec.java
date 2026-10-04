package com.power.fx.api.request;

import com.power.fx.api.model.CurrencyCode;

import java.util.Objects;

/**
 * One requested management (on-read, non-persistable) view currency and
 * the policy to convert to it (D-16).
 *
 * @see "Tech spec S4.7"
 */
public record ManagementViewSpec(CurrencyCode reportCurrency, PolicyRef policy) {

    public ManagementViewSpec {
        Objects.requireNonNull(reportCurrency, "reportCurrency must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
    }
}
