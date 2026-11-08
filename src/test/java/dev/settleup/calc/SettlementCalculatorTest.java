package dev.settleup.calc;

import dev.settleup.calc.SettlementCalculator.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The settlement calculation is the heart of the product: if it is wrong, real people pay the
 * wrong amounts. The properties that must always hold are tested explicitly:
 * <ol>
 *   <li>executing the transfers brings every balance to exactly zero;</li>
 *   <li>every transfer is a positive amount between two different people;</li>
 *   <li>the number of transfers is the true minimum (checked against brute force).</li>
 * </ol>
 */
class SettlementCalculatorTest {

    @Test
    @DisplayName("One person paid for everyone: each other person pays them back their share")
    void singlePayer() {
        // Alice paid 90.00 dinner for Alice, Bob, Carol → Alice +60, Bob −30, Carol −30
        Map<Long, BigDecimal> balances = balances(1L, "60.00", 2L, "-30.00", 3L, "-30.00");

        List<Transfer> transfers = SettlementCalculator.settle(balances);

        assertThat(transfers).containsExactly(
                new Transfer(2L, 1L, new BigDecimal("30.00")),
                new Transfer(3L, 1L, new BigDecimal("30.00")));
        assertSettlesExactly(balances, transfers);
    }

    @Test
    @DisplayName("Nobody owes anything: no transfers")
    void alreadySettled() {
        assertThat(SettlementCalculator.settle(balances(1L, "0.00", 2L, "0.00"))).isEmpty();
        assertThat(SettlementCalculator.settle(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("Finds the minimum where naive greedy matching would need an extra transfer")
    void beatsGreedyWhenZeroSumSubgroupsExist() {
        // Greedy largest-to-largest needs 4 transfers here. The optimum is 3:
        // {+4, −4} settle with one transfer, {+6, −3, −3} with two.
        Map<Long, BigDecimal> balances = balances(
                1L, "6.00", 2L, "4.00", 3L, "-3.00", 4L, "-3.00", 5L, "-4.00");

        List<Transfer> transfers = SettlementCalculator.settle(balances);

        assertThat(transfers).hasSize(3);
        assertThat(transfers).contains(new Transfer(5L, 2L, new BigDecimal("4.00")));
        assertSettlesExactly(balances, transfers);
    }

    @Test
    @DisplayName("Cent-level amounts are preserved exactly (no floating-point drift)")
    void preservesCents() {
        // 100.00 split three ways: shares 33.34 / 33.33 / 33.33, Alice paid.
        Map<Long, BigDecimal> balances = balances(1L, "66.66", 2L, "-33.33", 3L, "-33.33");

        List<Transfer> transfers = SettlementCalculator.settle(balances);

        assertThat(transfers).extracting(Transfer::amount)
                .containsExactly(new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertSettlesExactly(balances, transfers);
    }

    @Test
    @DisplayName("Works for zero-decimal currencies such as JPY")
    void zeroDecimalCurrency() {
        Map<Long, BigDecimal> balances = balances(1L, "667", 2L, "-333", 3L, "-334");

        List<Transfer> transfers = SettlementCalculator.settle(balances);

        assertThat(transfers).hasSize(2);
        assertSettlesExactly(balances, transfers);
    }

    @Test
    @DisplayName("Rejects balances that do not sum to zero, since that indicates a ledger bug")
    void rejectsUnbalancedInput() {
        assertThatThrownBy(() -> SettlementCalculator.settle(balances(1L, "10.00", 2L, "-9.99")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0.01");
    }

    @Test
    @DisplayName("Large groups fall back to greedy and still settle exactly in at most n−1 transfers")
    void largeGroupFallback() {
        Map<Long, BigDecimal> balances = randomBalances(new Random(7), 40);
        long nonZero = balances.values().stream().filter(b -> b.signum() != 0).count();

        List<Transfer> transfers = SettlementCalculator.settle(balances);

        assertThat(transfers.size()).isLessThanOrEqualTo((int) nonZero - 1);
        assertSettlesExactly(balances, transfers);
    }

    @ParameterizedTest(name = "random group seed {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    @DisplayName("Randomised: always exact and always the true minimum number of transfers")
    void randomisedAgainstBruteForce(int seed) {
        Random random = new Random(seed);
        for (int round = 0; round < 200; round++) {
            Map<Long, BigDecimal> balances = randomBalances(random, 2 + random.nextInt(7));

            List<Transfer> transfers = SettlementCalculator.settle(balances);

            assertSettlesExactly(balances, transfers);
            assertThat(transfers.size()).as("transfers for %s", balances)
                    .isEqualTo(bruteForceMinimumTransfers(balances));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void assertSettlesExactly(Map<Long, BigDecimal> balances, List<Transfer> transfers) {
        Map<Long, BigDecimal> remaining = new HashMap<>(balances);
        for (Transfer t : transfers) {
            assertThat(t.amount().signum()).as("transfer amount").isPositive();
            assertThat(t.fromParticipantId()).isNotEqualTo(t.toParticipantId());
            remaining.merge(t.fromParticipantId(), t.amount(), BigDecimal::add);
            remaining.merge(t.toParticipantId(), t.amount().negate(), BigDecimal::add);
        }
        remaining.forEach((id, left) ->
                assertThat(left.signum()).as("remaining balance of %s", id).isZero());
    }

    /** Minimum transfers = n − (max number of disjoint zero-sum subsets), by exhaustive search. */
    private static int bruteForceMinimumTransfers(Map<Long, BigDecimal> balances) {
        long[] v = balances.values().stream()
                .filter(b -> b.signum() != 0)
                .mapToLong(b -> b.movePointRight(2).longValueExact())
                .toArray();
        return v.length - maxZeroSumParts(v, (1 << v.length) - 1);
    }

    private static int maxZeroSumParts(long[] v, int mask) {
        if (mask == 0) {
            return 0;
        }
        int lowest = Integer.lowestOneBit(mask);
        int best = 0;
        for (int sub = mask; sub > 0; sub = (sub - 1) & mask) {
            if ((sub & lowest) == 0) {
                continue;
            }
            long sum = 0;
            for (int i = 0; i < v.length; i++) {
                if ((sub >> i & 1) == 1) {
                    sum += v[i];
                }
            }
            if (sum == 0) {
                best = Math.max(best, 1 + maxZeroSumParts(v, mask ^ sub));
            }
        }
        return best;
    }

    /** Small, repetitive amounts so that zero-sum subgroups occur often. */
    private static Map<Long, BigDecimal> randomBalances(Random random, int n) {
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        long sum = 0;
        for (int i = 1; i < n; i++) {
            long cents = (random.nextInt(9) - 4) * (random.nextBoolean() ? 500L : 1250L);
            sum += cents;
            balances.put((long) i, BigDecimal.valueOf(cents, 2));
        }
        balances.put((long) n, BigDecimal.valueOf(-sum, 2));
        return balances;
    }

    private static Map<Long, BigDecimal> balances(Object... idAmountPairs) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 0; i < idAmountPairs.length; i += 2) {
            map.put((Long) idAmountPairs[i], new BigDecimal((String) idAmountPairs[i + 1]));
        }
        return map;
    }
}
