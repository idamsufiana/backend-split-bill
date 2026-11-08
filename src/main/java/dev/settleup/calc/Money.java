package dev.settleup.calc;

import dev.settleup.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Monetary rules shared by the whole application.
 *
 * <p>Amounts are always {@link BigDecimal}. Each group has an ISO-4217 currency, and every
 * stored amount is normalised to that currency's minor-unit scale (2 for USD/EUR/IDR, 0 for JPY).
 * Input carrying more precision than the currency supports (e.g. {@code 10.005 USD}) is
 * <em>rejected</em> rather than silently rounded: a caller sending sub-cent values almost
 * certainly has a bug, and guessing would hide it.
 */
public final class Money {

    /** Banker's rounding: unbiased when many derived values (e.g. service charges) are rounded. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private Money() {
    }

    public static int scaleOf(Currency currency) {
        int digits = currency.getDefaultFractionDigits();
        return Math.max(digits, 0);
    }

    public static Currency currency(String code) {
        try {
            return Currency.getInstance(code.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException("Unknown ISO-4217 currency code: " + code);
        }
    }

    /**
     * Validates a strictly positive amount expressed in the given currency and returns it at
     * the currency's scale.
     */
    public static BigDecimal requirePositive(BigDecimal value, int scale, String field) {
        BigDecimal normalised = requireNonNegative(value, scale, field);
        if (normalised.signum() == 0) {
            throw new BusinessRuleException(field + " must be greater than zero");
        }
        return normalised;
    }

    public static BigDecimal requireNonNegative(BigDecimal value, int scale, String field) {
        if (value == null) {
            throw new BusinessRuleException(field + " is required");
        }
        if (value.signum() < 0) {
            throw new BusinessRuleException(field + " must not be negative");
        }
        if (value.stripTrailingZeros().scale() > scale) {
            throw new BusinessRuleException(
                    field + " has more than " + scale + " decimal place(s), which this currency does not support: "
                            + value.toPlainString());
        }
        return value.setScale(scale, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal zero(int scale) {
        return BigDecimal.ZERO.setScale(scale, RoundingMode.UNNECESSARY);
    }
}
