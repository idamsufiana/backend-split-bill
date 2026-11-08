package dev.settleup.domain.dto;

import dev.settleup.calc.SplitType;
import dev.settleup.domain.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response bodies. Monetary values serialise as JSON strings (see JacksonConfig). */
public final class Responses {

    private Responses() {
    }

    public record ParticipantRef(Long id, String name) {
    }

    public record Group(Long id, String name, String currency, Instant createdAt,
                        List<ParticipantRef> participants) {
    }

    public record Share(Long participantId, String name, BigDecimal amount, BigDecimal requestedValue) {
    }

    public record Expense(Long id, String description, BigDecimal amount, String currency,
                          ParticipantRef paidBy, ExpenseCategory category, SplitType splitType,
                          LocalDate incurredOn, Instant createdAt, Instant voidedAt, List<Share> shares) {
    }

    public record Payment(Long id, ParticipantRef from, ParticipantRef to, BigDecimal amount,
                          String currency, String note, Instant createdAt) {
    }

    /**
     * Per-participant position. {@code net_balance > 0}: the group owes them;
     * {@code < 0}: they owe the group. {@code net_balance = paid − share + payments_sent − payments_received}.
     */
    public record Balance(Long participantId, String name, BigDecimal paid, BigDecimal share,
                          BigDecimal paymentsSent, BigDecimal paymentsReceived, BigDecimal netBalance) {
    }

    /** "from pays to this amount". Shaped so it can be posted straight back as a payment. */
    public record Transaction(ParticipantRef from, ParticipantRef to, BigDecimal amount) {
    }

    public record Settlement(Long groupId, String currency, BigDecimal totalExpenses,
                             int serviceChargePct, BigDecimal serviceChargeAmount,
                             boolean settled, int transactionCount,
                             List<Balance> balances, List<Transaction> transactions,
                             Instant calculatedAt) {
    }

    public record ParticipantAmount(Long participantId, String name, BigDecimal amount) {
    }

    public record CategoryLine(ExpenseCategory category, BigDecimal total, int expenseCount,
                               BigDecimal percentageOfTotal, List<ParticipantAmount> consumedBy) {
    }

    public record CategorySummary(Long groupId, String currency, BigDecimal totalExpenses,
                                  List<CategoryLine> categories) {
    }
}
