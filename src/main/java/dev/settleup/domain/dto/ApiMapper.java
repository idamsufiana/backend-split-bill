package dev.settleup.domain.dto;

import dev.settleup.domain.BillGroup;
import dev.settleup.domain.Expense;
import dev.settleup.domain.Participant;
import dev.settleup.domain.Payment;

/** Entity → response mapping. Must be called inside a transaction (lazy associations). */
public final class ApiMapper {

    private ApiMapper() {
    }

    public static Responses.ParticipantRef ref(Participant p) {
        return new Responses.ParticipantRef(p.getId(), p.getName());
    }

    public static Responses.Group group(BillGroup g) {
        return new Responses.Group(g.getId(), g.getName(), g.getCurrency(), g.getCreatedAt(),
                g.getParticipants().stream().map(ApiMapper::ref).toList());
    }

    public static Responses.Expense expense(Expense e) {
        int scale = e.getGroup().moneyScale();
        return new Responses.Expense(
                e.getId(), e.getDescription(), e.getAmount().setScale(scale), e.getGroup().getCurrency(),
                ref(e.getPaidBy()), e.getCategory(), e.getSplitType(), e.getIncurredOn(),
                e.getCreatedAt(), e.getVoidedAt(),
                e.getShares().stream().map(s -> new Responses.Share(
                        s.getParticipant().getId(), s.getParticipant().getName(),
                        s.getAmount().setScale(scale),
                        s.getRequestedValue() == null ? null : s.getRequestedValue().stripTrailingZeros()))
                        .toList());
    }

    public static Responses.Payment payment(Payment p) {
        int scale = p.getGroup().moneyScale();
        return new Responses.Payment(p.getId(), ref(p.getPayer()), ref(p.getPayee()),
                p.getAmount().setScale(scale), p.getGroup().getCurrency(), p.getNote(), p.getCreatedAt());
    }
}
