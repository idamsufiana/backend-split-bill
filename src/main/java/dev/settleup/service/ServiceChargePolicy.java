package dev.settleup.service;

import dev.settleup.calc.ServiceChargeCalculator;
import dev.settleup.config.AppProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Applies the username-derived service charge. The percentage is computed once, at startup. */
@Component
public class ServiceChargePolicy {

    private final int percentage;

    public ServiceChargePolicy(AppProperties properties) {
        this.percentage = ServiceChargeCalculator.percentageFor(properties.githubUsername());
    }

    public int percentage() {
        return percentage;
    }

    public BigDecimal amountFor(BigDecimal totalExpenses, int scale) {
        return ServiceChargeCalculator.amount(totalExpenses, percentage, scale);
    }
}
