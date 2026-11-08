package dev.settleup.calc;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Service charge derived from a GitHub username:
 * {@code pct = (sum of Unicode code points of the lowercase username) % 10}, applied to the
 * group's total expenses and rounded to the currency's minor unit.
 */
public final class ServiceChargeCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ServiceChargeCalculator() {
    }

    public static int percentageFor(String githubUsername) {
        if (githubUsername == null || githubUsername.isBlank()) {
            throw new IllegalArgumentException("GitHub username must not be blank");
        }
        return githubUsername.trim().toLowerCase(Locale.ROOT).codePoints().sum() % 10;
    }

    public static BigDecimal amount(BigDecimal totalExpenses, int percentage, int scale) {
        return totalExpenses.multiply(BigDecimal.valueOf(percentage))
                .divide(HUNDRED, scale, Money.ROUNDING);
    }
}
