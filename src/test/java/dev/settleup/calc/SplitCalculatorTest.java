package dev.settleup.calc;

import dev.settleup.calc.SplitCalculator.SplitInput;
import dev.settleup.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitCalculatorTest {

    private static final List<Long> GROUP = List.of(1L, 2L, 3L);

    @Test
    void equalSplitDistributesTheOddCentDeterministically() {
        Map<Long, BigDecimal> shares = SplitCalculator.calculate(
                SplitType.EQUAL, new BigDecimal("100.00"), 2, GROUP, List.of());

        assertThat(shares).containsExactly(
                Map.entry(1L, new BigDecimal("33.34")),
                Map.entry(2L, new BigDecimal("33.33")),
                Map.entry(3L, new BigDecimal("33.33")));
        assertSumsTo(shares, "100.00");
    }

    @Test
    void equalSplitAmongSubset() {
        Map<Long, BigDecimal> shares = SplitCalculator.calculate(SplitType.EQUAL, new BigDecimal("50.00"), 2,
                GROUP, List.of(new SplitInput(2L, null), new SplitInput(3L, null)));

        assertThat(shares).containsOnlyKeys(2L, 3L).containsValues(new BigDecimal("25.00"));
    }

    @Test
    void percentageSplitStillSumsExactly() {
        Map<Long, BigDecimal> shares = SplitCalculator.calculate(SplitType.PERCENTAGE, new BigDecimal("10.00"), 2,
                GROUP, List.of(new SplitInput(1L, new BigDecimal("33.3")),
                        new SplitInput(2L, new BigDecimal("33.3")),
                        new SplitInput(3L, new BigDecimal("33.4"))));

        assertSumsTo(shares, "10.00");
    }

    @Test
    void percentagesMustSumTo100() {
        assertThatThrownBy(() -> SplitCalculator.calculate(SplitType.PERCENTAGE, new BigDecimal("10.00"), 2,
                GROUP, List.of(new SplitInput(1L, new BigDecimal("50")), new SplitInput(2L, new BigDecimal("40")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("100");
    }

    @Test
    void sharesSplitByWeight() {
        // Accommodation: Alice stayed 1 night, Bob 3 nights.
        Map<Long, BigDecimal> shares = SplitCalculator.calculate(SplitType.SHARES, new BigDecimal("400.00"), 2,
                GROUP, List.of(new SplitInput(1L, BigDecimal.ONE), new SplitInput(2L, new BigDecimal("3"))));

        assertThat(shares).containsEntry(1L, new BigDecimal("100.00")).containsEntry(2L, new BigDecimal("300.00"));
    }

    @Test
    void exactAmountsMustMatchTheTotal() {
        assertThatThrownBy(() -> SplitCalculator.calculate(SplitType.EXACT, new BigDecimal("30.00"), 2, GROUP,
                List.of(new SplitInput(1L, new BigDecimal("10.00")), new SplitInput(2L, new BigDecimal("19.99")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("difference 0.01");
    }

    @Test
    void exactAmountsRejectSubCentPrecision() {
        assertThatThrownBy(() -> SplitCalculator.calculate(SplitType.EXACT, new BigDecimal("30.00"), 2, GROUP,
                List.of(new SplitInput(1L, new BigDecimal("10.005")), new SplitInput(2L, new BigDecimal("19.995")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("decimal place");
    }

    @Test
    void rejectsNonMembersAndDuplicates() {
        assertThatThrownBy(() -> SplitCalculator.calculate(SplitType.EQUAL, BigDecimal.TEN, 2, GROUP,
                List.of(new SplitInput(99L, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not a member");
        assertThatThrownBy(() -> SplitCalculator.calculate(SplitType.EQUAL, BigDecimal.TEN, 2, GROUP,
                List.of(new SplitInput(1L, null), new SplitInput(1L, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("more than once");
    }

    private static void assertSumsTo(Map<Long, BigDecimal> shares, String expected) {
        assertThat(shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(expected);
    }
}
