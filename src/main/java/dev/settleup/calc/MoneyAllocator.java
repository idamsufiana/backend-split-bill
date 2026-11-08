package dev.settleup.calc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Splits an amount into parts proportional to weights so that the parts <b>always sum to exactly
 * the original amount</b>.
 *
 * <p>Uses the largest-remainder method on minor units: every part first gets the floor of its
 * exact share; the few leftover cents go to the parts with the largest fractional remainders
 * (ties broken by position, so the result is deterministic). Splitting 100.00 three ways gives
 * 33.34 / 33.33 / 33.33 — never 33.33 × 3 = 99.99 with a cent lost.
 */
public final class MoneyAllocator {

    private static final int DIVISION_SCALE = 20;

    private MoneyAllocator() {
    }

    public static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights, int scale) {
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("At least one weight is required");
        }
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weights.stream().anyMatch(w -> w.signum() < 0) || weightSum.signum() <= 0) {
            throw new IllegalArgumentException("Weights must be non-negative with a positive sum");
        }

        BigInteger totalUnits = total.setScale(scale, RoundingMode.UNNECESSARY).unscaledValue();
        BigDecimal totalUnitsDec = new BigDecimal(totalUnits);

        int n = weights.size();
        BigInteger[] units = new BigInteger[n];
        BigDecimal[] remainders = new BigDecimal[n];
        BigInteger allocated = BigInteger.ZERO;

        for (int i = 0; i < n; i++) {
            BigDecimal exact = totalUnitsDec.multiply(weights.get(i))
                    .divide(weightSum, DIVISION_SCALE, RoundingMode.DOWN);
            BigDecimal floor = exact.setScale(0, RoundingMode.FLOOR);
            units[i] = floor.toBigIntegerExact();
            remainders[i] = exact.subtract(floor);
            allocated = allocated.add(units[i]);
        }

        int leftover = totalUnits.subtract(allocated).intValueExact();
        List<Integer> byRemainder = IntStream.range(0, n).boxed()
                .sorted(Comparator.<Integer, BigDecimal>comparing(i -> remainders[i]).reversed()
                        .thenComparing(i -> i))
                .toList();
        for (int k = 0; k < leftover; k++) {
            int idx = byRemainder.get(k % n);
            units[idx] = units[idx].add(BigInteger.ONE);
        }

        List<BigDecimal> result = new ArrayList<>(n);
        for (BigInteger u : units) {
            result.add(new BigDecimal(u, scale));
        }
        return result;
    }
}
