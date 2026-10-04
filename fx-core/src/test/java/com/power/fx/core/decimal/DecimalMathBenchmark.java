package com.power.fx.core.decimal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * Task 2.1 performance gate (implementation plan Section 4, Task 2.1
 * acceptance "performance" items 1-2).
 *
 * <p><strong>JMH was judged impractical to wire up in this environment</strong>
 * (no network access to pull the JMH annotation-processor/bytecode-gen
 * toolchain into a from-scratch reactor within this task's time budget).
 * Per the task's own explicit fallback instruction, this is a documented
 * {@code System.nanoTime()}-based micro-benchmark harness instead: JIT
 * warm-up iterations followed by a measured batch, reporting median and
 * p99 wall-clock latency. This is run from a {@code @Test} (not a
 * {@code @Disabled}-by-default micro-benchmark) so it executes as part of
 * the normal {@code mvn test} run and its numbers appear in this task's
 * own console output / surefire report; thresholds are asserted loosely
 * (generous multiples of the target) specifically so that a slow CI
 * container does not flake the build, while still catching a gross
 * regression. The actual measured numbers belong in the Phase 2 report,
 * not just a pass/fail boolean.
 *
 * @see "Tech spec Appendix D.4; implementation plan Task 2.1"
 */
class DecimalMathBenchmark {

    private static final MathContext WORKING = new MathContext(60, RoundingMode.HALF_EVEN);
    private static final int WARMUP = 2_000;
    private static final int MEASURED = 5_000;

    @Test
    void singleLnLatency() {
        BigDecimal[] args = sampleArgsForLn();
        long[] samples = new long[MEASURED];

        for (int i = 0; i < WARMUP; i++) {
            DecimalLn.ln(args[i % args.length], WORKING);
        }
        for (int i = 0; i < MEASURED; i++) {
            BigDecimal x = args[i % args.length];
            long t0 = System.nanoTime();
            DecimalLn.ln(x, WORKING);
            samples[i] = System.nanoTime() - t0;
        }
        report("ln()", samples);
    }

    @Test
    void singleExpLatency() {
        BigDecimal[] args = sampleArgsForExp();
        long[] samples = new long[MEASURED];

        for (int i = 0; i < WARMUP; i++) {
            DecimalExp.exp(args[i % args.length], WORKING);
        }
        for (int i = 0; i < MEASURED; i++) {
            BigDecimal x = args[i % args.length];
            long t0 = System.nanoTime();
            DecimalExp.exp(x, WORKING);
            samples[i] = System.nanoTime() - t0;
        }
        report("exp()", samples);
    }

    /**
     * Synthetic 20-pillar-curve-equivalent workload: 20 sequential,
     * non-memoised {@code ln()} calls at distinct arguments, run
     * back-to-back with no caching, rehearsing the Task 2.11 forward-curve
     * build cost before that task starts (plan Task 2.1 acceptance item 2).
     */
    @Test
    void twentyPillarCurveEquivalentWorkloadP99() {
        BigDecimal[] args = sampleArgsForLn(); // 20 distinct carry-like arguments
        int workloadRuns = 500;
        long[] samples = new long[workloadRuns];

        // Warm-up.
        for (int w = 0; w < 50; w++) {
            runWorkload(args);
        }
        for (int i = 0; i < workloadRuns; i++) {
            long t0 = System.nanoTime();
            runWorkload(args);
            samples[i] = System.nanoTime() - t0;
        }

        Arrays.sort(samples);
        long p99 = samples[(int) (workloadRuns * 0.99)];
        long median = samples[workloadRuns / 2];
        System.out.println("[Task 2.1 benchmark] 20-ln workload: median=" + (median / 1000.0) + "us, p99="
                + (p99 / 1000.0) + "us (target <= 1000us)");

        // Generous margin (3x target) so a loaded CI box does not flake this
        // build; the real number is what matters for the report, not this
        // boolean. See Task 2.11 for the real ForwardCurveBuilder re-check.
        long generousBudgetNanos = 3_000_000L;
        if (p99 > generousBudgetNanos) {
            throw new AssertionError("20-ln workload p99 " + (p99 / 1000.0)
                    + "us grossly exceeds even a 3x-generous budget; decimal levers must be applied"
                    + " before Task 2.11 starts (plan Task 2.1 acceptance item 3)");
        }
    }

    private static void runWorkload(BigDecimal[] args) {
        for (BigDecimal arg : args) {
            DecimalLn.ln(arg, WORKING);
        }
    }

    private static BigDecimal[] sampleArgsForLn() {
        // 20 distinct values resembling forward-curve carry ratios (F/S), i.e.
        // close to 1, which is the realistic lnCarry() workload (Appendix D.3
        // "a 10% carry over 2 years is |ln| < 0.2" -- so arguments near 1, not
        // across the full [1e-30,1e30] domain).
        BigDecimal[] out = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            out[i] = BigDecimal.ONE.add(BigDecimal.valueOf(i + 1).movePointLeft(4), WORKING); // 1.0001..1.0020
        }
        return out;
    }

    private static BigDecimal[] sampleArgsForExp() {
        BigDecimal[] out = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            out[i] = BigDecimal.valueOf(i + 1).movePointLeft(3); // 0.001..0.020
        }
        return out;
    }

    private static void report(String label, long[] samples) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        long median = sorted[sorted.length / 2];
        long p99 = sorted[(int) (sorted.length * 0.99)];
        System.out.println("[Task 2.1 benchmark] " + label + ": median=" + (median / 1000.0)
                + "us, p99=" + (p99 / 1000.0) + "us");
    }
}
