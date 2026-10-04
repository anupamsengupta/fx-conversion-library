package com.power.fx.core.precision;

import com.power.fx.api.result.AllocationResidual;
import com.power.fx.core.decimal.FxMath;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Allocates a rounded total across {@code n} lines by largest remainder,
 * ties broken to earliest sequence -- deterministic and map-order
 * independent (FS S15). Property test: {@code sum(line.toAmountBooked) ==
 * totalBooked} exactly.
 */
public final class LargestRemainderAllocator {

    public record Result(List<BigDecimal> bookedLines, AllocationResidual residual) {
    }

    /**
     * @param unroundedLines line amounts in original (sequence) order
     * @param totalBooked    the already-rounded total to allocate exactly across the lines
     * @param scale          currency decimal scale
     */
    public Result allocate(List<BigDecimal> unroundedLines, BigDecimal totalBooked, int scale, BigDecimal independentTotal) {
        int n = unroundedLines.size();
        BigDecimal[] floors = new BigDecimal[n];
        BigDecimal[] remainders = new BigDecimal[n];
        BigDecimal floorSum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal floor = unroundedLines.get(i).setScale(scale, RoundingMode.FLOOR);
            floors[i] = floor;
            remainders[i] = unroundedLines.get(i).subtract(floor, FxMath.DECIMAL128);
            floorSum = floorSum.add(floor, FxMath.DECIMAL128);
        }

        BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
        BigDecimal residualAmount = totalBooked.subtract(floorSum, FxMath.DECIMAL128);
        long residualUnits = residualAmount.divide(unit, 0, RoundingMode.HALF_UP).longValueExact();

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        // Largest remainder first; ties broken to earliest sequence (stable sort preserves original
        // index order for equal remainders since Integer.compare on remainder is the only sort key).
        order.sort((a, b) -> remainders[b].compareTo(remainders[a]));

        BigDecimal[] booked = floors.clone();
        for (int i = 0; i < residualUnits && i < n; i++) {
            int idx = order.get(i);
            booked[idx] = booked[idx].add(unit, FxMath.DECIMAL128);
        }

        List<BigDecimal> bookedList = List.of(booked);
        BigDecimal independentlyConvertedTotal = independentTotal;
        BigDecimal residualVsIndependent = totalBooked.subtract(independentlyConvertedTotal, FxMath.DECIMAL128);
        BigDecimal tolerance = com.power.fx.api.result.ReconciliationTolerance.of(scale, n, totalBooked);

        return new Result(bookedList, new AllocationResidual(residualVsIndependent, n, scale, tolerance));
    }
}
