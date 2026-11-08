package dev.settleup.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Turns net balances into the list of transfers that settles everyone.
 *
 * <p><b>Sign convention:</b> a positive balance means the participant is owed money (they paid
 * more than their share); negative means they owe. Balances must sum to exactly zero.
 *
 * <p><b>Optimality:</b> the fewest transfers needed is {@code n − k}, where {@code n} is the number
 * of non-zero balances and {@code k} the largest number of disjoint zero-sum subgroups they can be
 * partitioned into (each subgroup of size s settles internally in s − 1 transfers, and no
 * subgroup can do better). Finding {@code k} is NP-hard in general, so:
 * <ul>
 *   <li>for up to {@value #EXACT_LIMIT} non-zero balances we find {@code k} exactly with a
 *       subset DP (2^n states — 65,536 at the limit, well under a millisecond);</li>
 *   <li>above that we fall back to the greedy "largest debtor pays largest creditor" heuristic,
 *       which is never worse than {@code n − 1} transfers.</li>
 * </ul>
 * Within each subgroup the greedy pairing is used, which is optimal there by construction.
 */
public final class SettlementCalculator {

    static final int EXACT_LIMIT = 16;

    /** A single payment that should be made to settle up. */
    public record Transfer(Long fromParticipantId, Long toParticipantId, BigDecimal amount) {
    }

    private record Party(Long id, long units) {
    }

    private SettlementCalculator() {
    }

    public static List<Transfer> settle(Map<Long, BigDecimal> balances) {
        int scale = balances.values().stream().mapToInt(BigDecimal::scale).max().orElse(0);
        scale = Math.max(scale, 0);

        List<Party> nonZero = new ArrayList<>();
        long total = 0;
        for (Map.Entry<Long, BigDecimal> e : balances.entrySet()) {
            long units = e.getValue().setScale(scale, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
            total = Math.addExact(total, units);
            if (units != 0) {
                nonZero.add(new Party(e.getKey(), units));
            }
        }
        if (total != 0) {
            throw new IllegalStateException("Balances must sum to zero but sum to "
                    + BigDecimal.valueOf(total, scale).toPlainString());
        }
        nonZero.sort(Comparator.comparing(Party::id));

        List<List<Party>> subgroups = nonZero.size() <= EXACT_LIMIT
                ? partitionIntoMaxZeroSumGroups(nonZero)
                : List.of(nonZero);

        List<Transfer> transfers = new ArrayList<>();
        for (List<Party> group : subgroups) {
            settleGreedily(group, scale, transfers);
        }
        transfers.sort(Comparator.comparing(Transfer::fromParticipantId)
                .thenComparing(Transfer::toParticipantId));
        return transfers;
    }

    /**
     * dp[mask] = the maximum number of zero-sum groups that the members of {@code mask} can be
     * split into, counting {@code mask} itself as closing a group when its sum is zero.
     * Walking back from the full mask, every zero-sum mask on the path is a group boundary.
     */
    private static List<List<Party>> partitionIntoMaxZeroSumGroups(List<Party> parties) {
        int n = parties.size();
        if (n == 0) {
            return List.of();
        }
        int full = (1 << n) - 1;
        long[] sum = new long[full + 1];
        int[] dp = new int[full + 1];

        for (int mask = 1; mask <= full; mask++) {
            int low = Integer.numberOfTrailingZeros(mask);
            sum[mask] = sum[mask & (mask - 1)] + parties.get(low).units();
            int best = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    best = Math.max(best, dp[mask ^ (1 << i)]);
                }
            }
            dp[mask] = best + (sum[mask] == 0 ? 1 : 0);
        }

        List<Integer> boundaries = new ArrayList<>();
        int mask = full;
        while (mask != 0) {
            int bonus = sum[mask] == 0 ? 1 : 0;
            if (bonus == 1) {
                boundaries.add(mask);
            }
            for (int i = 0; i < n; i++) {
                int bit = 1 << i;
                if ((mask & bit) != 0 && dp[mask ^ bit] == dp[mask] - bonus) {
                    mask ^= bit;
                    break;
                }
            }
        }
        boundaries.add(0);

        List<List<Party>> groups = new ArrayList<>();
        for (int g = 0; g + 1 < boundaries.size(); g++) {
            int members = boundaries.get(g) ^ boundaries.get(g + 1);
            List<Party> group = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((members & (1 << i)) != 0) {
                    group.add(parties.get(i));
                }
            }
            groups.add(group);
        }
        return groups;
    }

    private static void settleGreedily(List<Party> group, int scale, List<Transfer> out) {
        Comparator<long[]> largestFirst = Comparator.<long[]>comparingLong(p -> -p[1])
                .thenComparingLong(p -> p[0]);
        PriorityQueue<long[]> creditors = new PriorityQueue<>(largestFirst);
        PriorityQueue<long[]> debtors = new PriorityQueue<>(largestFirst);
        for (Party p : group) {
            if (p.units() > 0) {
                creditors.add(new long[]{p.id(), p.units()});
            } else {
                debtors.add(new long[]{p.id(), -p.units()});
            }
        }
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            long[] creditor = creditors.poll();
            long[] debtor = debtors.poll();
            long amount = Math.min(creditor[1], debtor[1]);
            out.add(new Transfer(debtor[0], creditor[0], BigDecimal.valueOf(amount, scale)));
            creditor[1] -= amount;
            debtor[1] -= amount;
            if (creditor[1] > 0) {
                creditors.add(creditor);
            }
            if (debtor[1] > 0) {
                debtors.add(debtor);
            }
        }
    }
}
