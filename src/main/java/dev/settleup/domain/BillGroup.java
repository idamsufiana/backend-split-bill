package dev.settleup.domain;

import dev.settleup.calc.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/** A set of people sharing costs (a trip, a flat, a team lunch). All amounts use one currency. */
@Entity
@Table(name = "bill_group")
public class BillGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Participant> participants = new ArrayList<>();

    protected BillGroup() {
    }

    public BillGroup(String name, Currency currency, Instant createdAt) {
        this.name = name;
        this.currency = currency.getCurrencyCode();
        this.createdAt = createdAt;
    }

    public Participant addParticipant(String participantName) {
        Participant p = new Participant(this, participantName);
        participants.add(p);
        return p;
    }

    public boolean hasParticipantNamed(String candidate) {
        return participants.stream().anyMatch(p -> p.getName().equalsIgnoreCase(candidate.trim()));
    }

    public Currency currency() {
        return Currency.getInstance(currency);
    }

    public int moneyScale() {
        return Money.scaleOf(currency());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Participant> getParticipants() {
        return participants;
    }
}
