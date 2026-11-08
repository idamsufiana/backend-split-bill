package dev.settleup.domain.dto;

import dev.settleup.calc.SplitType;
import dev.settleup.domain.ExpenseCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request bodies. JSON uses snake_case (configured globally), so {@code paidBy} is sent as
 * {@code paid_by}. Structural validation lives here (Bean Validation → 400); domain rules such as
 * "percentages must sum to 100" live in the services (→ 422).
 */
public final class Requests {

    private Requests() {
    }

    public record CreateGroup(
            @NotBlank @Size(max = 120) String name,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO-4217 code") String currency,
            @NotEmpty List<@NotBlank @Size(max = 80) String> participants) {
    }

    public record AddParticipant(@NotBlank @Size(max = 80) String name) {
    }

    /**
     * @param splitType   defaults to EQUAL
     * @param allocations who the expense was for; for EQUAL may be omitted to mean "everyone"
     */
    public record CreateExpense(
            @NotBlank @Size(max = 200) String description,
            @NotNull BigDecimal amount,
            @NotNull Long paidBy,
            ExpenseCategory category,
            SplitType splitType,
            List<@Valid Allocation> allocations,
            LocalDate incurredOn) {
    }

    /** {@code value} is a percentage, weight or exact amount depending on the split type. */
    public record Allocation(@NotNull Long participantId, BigDecimal value) {
    }

    public record RecordPayment(
            @NotNull Long fromParticipantId,
            @NotNull Long toParticipantId,
            @NotNull BigDecimal amount,
            @Size(max = 200) String note) {
    }
}
