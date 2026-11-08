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

/**
 * A member of exactly one group. Participants are scoped to a group rather than being global
 * users: this API has no accounts, and "Alice" on a ski trip and "Alice" in a flat-share are
 * unrelated ledgers.
 */
@Entity
@Table(name = "participant")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private BillGroup group;

    @Column(nullable = false, length = 80)
    private String name;

    protected Participant() {
    }

    Participant(BillGroup group, String name) {
        this.group = group;
        this.name = name.trim();
    }

    public Long getId() {
        return id;
    }

    public BillGroup getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }
}
