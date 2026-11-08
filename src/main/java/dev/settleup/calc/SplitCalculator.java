package dev.settleup.calc;

import dev.settleup.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a split request into concrete per-participant amounts that sum exactly to the expense.
 * Pure function: no persistence, no framework.
 */
public final class SplitCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** One line of a split request. {@code value} meaning depends on the {@link SplitType}. */
    public record SplitInput(Long participantId, BigDecimal value) {
    }

    private SplitCalculator() {
    }

    /**
     * @param groupParticipantIds every participant in the group (used to validate ids, and as the
     *                            default beneficiaries of an EQUAL split with no inputs)
     * @return participant id → owed amount, in input order, summing exactly to {@code amount}
     */
    public static Map<Long, BigDecimal> calculate(SplitType type,
                                                  BigDecimal amount,
                                                  int scale,
                                                  Collection<Long> groupParticipantIds,
                                                  List<SplitInput> inputs) {
        List<SplitInput> lines = inputs == null ? List.of() : inputs;

        if (type == SplitType.EQUAL && lines.isEmpty()) {
            lines = groupParticipantIds.stream().map(id -> new SplitInput(id, null)).toList();
        }
        if (lines.isEmpty()) {
            throw new BusinessRuleException(type + " split requires at least one allocation");
        }
        validateParticipants(lines, groupParticipantIds);

        List<Long> ids = lines.stream().map(SplitInput::participantId).toList();
        List<BigDecimal> amounts = switch (type) {
            case EQUAL -> MoneyAllocator.allocate(amount, lines.stream().map(l -> BigDecimal.ONE).toList(), scale);
            case SHARES -> MoneyAllocator.allocate(amount, positiveValues(lines, "weight"), scale);
            case PERCENTAGE -> {
                List<BigDecimal> pcts = positiveValues(lines, "percentage");
                BigDecimal sum = pcts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                if (sum.compareTo(HUNDRED) != 0) {
                    throw new BusinessRuleException("Percentages must sum to 100, got " + sum.toPlainString());
                }
                yield MoneyAllocator.allocate(amount, pcts, scale);
            }
            case EXACT -> {
                List<BigDecimal> exact = new ArrayList<>();
                for (SplitInput line : lines) {
                    exact.add(Money.requirePositive(line.value(), scale,
                            "amount for participant " + line.participantId()));
                }
                BigDecimal sum = exact.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                if (sum.compareTo(amount) != 0) {
                    throw new BusinessRuleException("Exact amounts sum to " + sum.toPlainString()
                            + " but the expense is " + amount.toPlainString()
                            + " (difference " + amount.subtract(sum).toPlainString() + ")");
                }
                yield exact;
            }
        };

        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            result.put(ids.get(i), amounts.get(i));
        }
        return result;
    }

    private static void validateParticipants(List<SplitInput> lines, Collection<Long> groupParticipantIds) {
        Set<Long> seen = new HashSet<>();
        for (SplitInput line : lines) {
            Long id = line.participantId();
            if (id == null) {
                throw new BusinessRuleException("participant_id is required on every allocation");
            }
            if (!groupParticipantIds.contains(id)) {
                throw new BusinessRuleException("Participant " + id + " is not a member of this group");
            }
            if (!seen.add(id)) {
                throw new BusinessRuleException("Participant " + id + " appears more than once in the split");
            }
        }
    }

    private static List<BigDecimal> positiveValues(List<SplitInput> lines, String label) {
        List<BigDecimal> values = new ArrayList<>();
        for (SplitInput line : lines) {
            BigDecimal v = line.value();
            if (v == null || v.signum() <= 0) {
                throw new BusinessRuleException(
                        "A positive " + label + " is required for participant " + line.participantId());
            }
            values.add(v);
        }
        return values;
    }
}
