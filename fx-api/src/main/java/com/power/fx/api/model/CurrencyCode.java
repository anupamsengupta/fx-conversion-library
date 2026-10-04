package com.power.fx.api.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An ISO 4217 currency code or a market minor code (e.g. {@code GBp},
 * {@code USc}, {@code ZAc}, {@code ILA}). Case-sensitive: minor codes are
 * distinguished from majors by case.
 *
 * @see "Tech spec S4.3"
 */
public record CurrencyCode(String value) implements Comparable<CurrencyCode> {

    private static final Pattern VALID = Pattern.compile("[A-Za-z]{3}");

    public CurrencyCode {
        Objects.requireNonNull(value, "value must not be null");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "CurrencyCode must match [A-Za-z]{3}, got: " + value);
        }
    }

    @Override
    public int compareTo(CurrencyCode other) {
        return this.value.compareTo(other.value);
    }
}
