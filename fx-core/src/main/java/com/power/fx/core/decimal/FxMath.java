package com.power.fx.core.decimal;

import com.power.fx.api.FxConfig;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Owns the only two {@link MathContext} instances used anywhere in
 * {@code fx-core} (S6.10):
 * <ul>
 *   <li>{@link #DECIMAL128} -- 34 digits, {@code HALF_EVEN}, for every
 *       published decimal; a {@code static final} constant because it
 *       never varies with host configuration.
 *   <li>{@code working()} -- {@link FxConfig#decimalWorkingPrecision()}
 *       digits (default 60), {@code HALF_EVEN}, for transcendental
 *       intermediates; an instance method because its precision is a
 *       host-supplied configuration value (Appendix D.1).
 * </ul>
 *
 * <p>No {@code BigDecimal} arithmetic method is ever called anywhere in
 * {@code fx-core} without an explicit {@code MathContext} (AR-07); this
 * class is the single place the two legal contexts are constructed.
 *
 * <p>Implements {@link DecimalTranscendentals}, the internal port bound to
 * this class in {@code fx-guice}'s {@code FxModule} (S9.1).
 *
 * @see "Tech spec S6.10, Appendix D.1"
 */
public final class FxMath implements DecimalTranscendentals {

    /** 34 significant digits, {@code HALF_EVEN} -- every published decimal (Appendix D.1). */
    public static final MathContext DECIMAL128 = new MathContext(34, RoundingMode.HALF_EVEN);

    /** Default working precision per Appendix D.1 / {@code FxConfig} (60 digits). */
    public static final int DEFAULT_WORKING_PRECISION = 60;

    private final MathContext working;

    @Inject
    public FxMath(FxConfig config) {
        this(config == null ? DEFAULT_WORKING_PRECISION : config.decimalWorkingPrecision());
    }

    public FxMath() {
        this(DEFAULT_WORKING_PRECISION);
    }

    public FxMath(int workingPrecision) {
        if (workingPrecision <= 0) {
            throw new IllegalArgumentException("workingPrecision must be positive: " + workingPrecision);
        }
        this.working = new MathContext(workingPrecision, RoundingMode.HALF_EVEN);
    }

    /** The working-precision {@link MathContext} for transcendental intermediates. */
    public MathContext working() {
        return working;
    }

    /**
     * {@code ln(x)} at working precision -- the precision callers such as
     * {@code ForwardCurveBuilder}'s {@code lnCarry} array need, so that a
     * later subtraction of two nearby carries (D.6) does not cancel into
     * the DECIMAL128 noise floor before the final {@code exp} and round.
     */
    @Override
    public BigDecimal ln(BigDecimal x) {
        Objects.requireNonNull(x, "x must not be null");
        return DecimalLn.ln(x, working);
    }

    /** {@code exp(x)} at working precision. See {@link #ln(BigDecimal)}. */
    @Override
    public BigDecimal exp(BigDecimal x) {
        Objects.requireNonNull(x, "x must not be null");
        return DecimalExp.exp(x, working);
    }

    /**
     * {@code ln(x)}, computed at working precision per Appendix D.2's error
     * budget and rounded to {@link #DECIMAL128} as the single, final step
     * (D.2 step 6) -- for callers that publish immediately. Deliberately
     * <strong>not</strong> {@code DecimalLn.ln(x, DECIMAL128)}: running the
     * whole algorithm at only 34 digits of intermediate precision loses the
     * error-budget margin Appendix D.2 relies on (the {@code 2^r}
     * amplification in the recombination step costs noticeably more than
     * one part in {@code 10^33} once every intermediate op is itself only
     * 34-digit accurate).
     */
    public BigDecimal lnPublished(BigDecimal x) {
        return DecimalLn.ln(x, working).round(DECIMAL128);
    }

    /** {@code exp(x)}, working-precision then single final round. See {@link #lnPublished}. */
    public BigDecimal expPublished(BigDecimal x) {
        return DecimalExp.exp(x, working).round(DECIMAL128);
    }
}
