package dev.settleup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** How much of one expense one participant is responsible for. */
@Entity
@Table(name = "expense_share")
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    /** The resolved amount owed, at the currency's scale. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** What the caller asked for (a percentage, weight or exact amount), kept for auditability. */
    @Column(precision = 19, scale = 6)
    private BigDecimal requestedValue;

    protected ExpenseShare() {
    }

    ExpenseShare(Expense expense, Participant participant, BigDecimal amount, BigDecimal requestedValue) {
        this.expense = expense;
        this.participant = participant;
        this.amount = amount;
        this.requestedValue = requestedValue;
    }

    public Long getId() {
        return id;
    }

    public Expense getExpense() {
        return expense;
    }

    public Participant getParticipant() {
        return participant;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getRequestedValue() {
        return requestedValue;
    }
}
