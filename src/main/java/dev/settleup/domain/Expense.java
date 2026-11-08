package dev.settleup.domain;

import dev.settleup.calc.SplitType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Something one participant paid for on behalf of some participants.
 *
 * <p>The split is resolved into concrete {@link ExpenseShare} rows at creation time, so later
 * reads never recompute (or re-round) anything. Expenses are never hard-deleted; voiding keeps
 * them in the audit trail while excluding them from balances.
 */
@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private BillGroup group;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by_id")
    private Participant paidBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SplitType splitType;

    private LocalDate incurredOn;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant voidedAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ExpenseShare> shares = new ArrayList<>();

    protected Expense() {
    }

    public Expense(BillGroup group, String description, BigDecimal amount, Participant paidBy,
                   ExpenseCategory category, SplitType splitType, LocalDate incurredOn, Instant createdAt) {
        this.group = group;
        this.description = description;
        this.amount = amount;
        this.paidBy = paidBy;
        this.category = category;
        this.splitType = splitType;
        this.incurredOn = incurredOn;
        this.createdAt = createdAt;
    }

    public void addShare(Participant participant, BigDecimal shareAmount, BigDecimal requestedValue) {
        shares.add(new ExpenseShare(this, participant, shareAmount, requestedValue));
    }

    public void voidAt(Instant when) {
        this.voidedAt = when;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    public Long getId() {
        return id;
    }

    public BillGroup getGroup() {
        return group;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Participant getPaidBy() {
        return paidBy;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public LocalDate getIncurredOn() {
        return incurredOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public List<ExpenseShare> getShares() {
        return shares;
    }
}
