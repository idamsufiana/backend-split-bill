package dev.settleup.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceChargeCalculatorTest {

    @Test
    void matchesTheWorkedExampleFromTheBrief() {
        // 106+111+104+110+100+111+101+52+55 = 850 → 850 % 10 = 0
        assertThat(ServiceChargeCalculator.percentageFor("johndoe47")).isZero();
    }

    @Test
    void isCaseInsensitive() {
        assertThat(ServiceChargeCalculator.percentageFor("JohnDoe47"))
                .isEqualTo(ServiceChargeCalculator.percentageFor("johndoe47"));
    }

    @Test
    void computesFromCodePoints() {
        // 'a' = 97 → 7 ; "ab" = 97 + 98 = 195 → 5
        assertThat(ServiceChargeCalculator.percentageFor("a")).isEqualTo(7);
        assertThat(ServiceChargeCalculator.percentageFor("ab")).isEqualTo(5);
    }

    @Test
    void amountIsRoundedToTheCurrencyMinorUnitWithBankersRounding() {
        // 123.45 × 7% = 8.6415 → 8.64
        assertThat(ServiceChargeCalculator.amount(new BigDecimal("123.45"), 7, 2))
                .isEqualTo(new BigDecimal("8.64"));
        // 0.50 × 5% = 0.025 → half-even → 0.02
        assertThat(ServiceChargeCalculator.amount(new BigDecimal("0.50"), 5, 2))
                .isEqualTo(new BigDecimal("0.02"));
        assertThat(ServiceChargeCalculator.amount(new BigDecimal("1000"), 3, 0))
                .isEqualTo(new BigDecimal("30"));
    }
}
