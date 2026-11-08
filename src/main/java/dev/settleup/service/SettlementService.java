package dev.settleup.service;

import dev.settleup.calc.Money;
import dev.settleup.config.CacheConfig;
import dev.settleup.calc.SettlementCalculator;
import dev.settleup.domain.BillGroup;
import dev.settleup.domain.Expense;
import dev.settleup.domain.ExpenseCategory;
import dev.settleup.domain.ExpenseShare;
import dev.settleup.domain.Participant;
import dev.settleup.domain.Payment;
import dev.settleup.repository.ExpenseRepository;
import dev.settleup.repository.PaymentRepository;
import dev.settleup.domain.dto.ApiMapper;
import dev.settleup.domain.dto.Responses;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-side: balances, the settlement plan and per-category summaries. Nothing here is stored in
 * the database; everything is derived from expenses and payments, so it can never drift out of
 * sync with the ledger. Results are cached in Redis per group and evicted on every write to that
 * group (see {@link EvictGroupCaches}).
 */
@Service
@Transactional(readOnly = true)
public class SettlementService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final GroupService groupService;
    private final ExpenseRepository expenses;
    private final PaymentRepository payments;
    private final ServiceChargePolicy serviceCharge;
    private final Clock clock;

    public SettlementService(GroupService groupService, ExpenseRepository expenses, PaymentRepository payments,
                             ServiceChargePolicy serviceCharge, Clock clock) {
        this.groupService = groupService;
        this.expenses = expenses;
        this.payments = payments;
        this.serviceCharge = serviceCharge;
        this.clock = clock;
    }

    @Cacheable(cacheNames = CacheConfig.SETTLEMENTS, key = "#groupId")
    public Responses.Settlement settlement(Long groupId) {
        BillGroup group = groupService.load(groupId);
        int scale = group.moneyScale();
        List<Expense> active = expenses.findByGroupIdAndVoidedAtIsNullOrderByIdAsc(groupId);
        List<Payment> recorded = payments.findByGroupIdOrderByIdAsc(groupId);

        Map<Long, Ledger> ledgers = new LinkedHashMap<>();
        group.getParticipants().forEach(p -> ledgers.put(p.getId(), new Ledger(p, scale)));

        BigDecimal total = Money.zero(scale);
        for (Expense e : active) {
            total = total.add(e.getAmount());
            ledgers.get(e.getPaidBy().getId()).paid = ledgers.get(e.getPaidBy().getId()).paid.add(e.getAmount());
            for (ExpenseShare s : e.getShares()) {
                Ledger l = ledgers.get(s.getParticipant().getId());
                l.share = l.share.add(s.getAmount());
            }
        }
        for (Payment p : recorded) {
            Ledger from = ledgers.get(p.getPayer().getId());
            Ledger to = ledgers.get(p.getPayee().getId());
            from.sent = from.sent.add(p.getAmount());
            to.received = to.received.add(p.getAmount());
        }

        Map<Long, BigDecimal> net = new LinkedHashMap<>();
        List<Responses.Balance> balances = new ArrayList<>();
        for (Ledger l : ledgers.values()) {
            BigDecimal netBalance = l.net().setScale(scale);
            net.put(l.participant.getId(), netBalance);
            balances.add(new Responses.Balance(l.participant.getId(), l.participant.getName(),
                    l.paid.setScale(scale), l.share.setScale(scale), l.sent.setScale(scale),
                    l.received.setScale(scale), netBalance));
        }

        Map<Long, Participant> byId = group.getParticipants().stream()
                .collect(Collectors.toMap(Participant::getId, Function.identity()));
        List<Responses.Transaction> transactions = SettlementCalculator.settle(net).stream()
                .map(t -> new Responses.Transaction(
                        ApiMapper.ref(byId.get(t.fromParticipantId())),
                        ApiMapper.ref(byId.get(t.toParticipantId())),
                        t.amount().setScale(scale)))
                .toList();

        total = total.setScale(scale);
        return new Responses.Settlement(group.getId(), group.getCurrency(), total,
                serviceCharge.percentage(), serviceCharge.amountFor(total, scale),
                transactions.isEmpty(), transactions.size(), balances, transactions, clock.instant());
    }

    @Cacheable(cacheNames = CacheConfig.CATEGORY_SUMMARIES, key = "#groupId")
    public Responses.CategorySummary categorySummary(Long groupId) {
        BillGroup group = groupService.load(groupId);
        int scale = group.moneyScale();
        List<Expense> active = expenses.findByGroupIdAndVoidedAtIsNullOrderByIdAsc(groupId);

        BigDecimal grandTotal = active.stream().map(Expense::getAmount)
                .reduce(Money.zero(scale), BigDecimal::add).setScale(scale);

        Map<ExpenseCategory, List<Expense>> byCategory = new EnumMap<>(ExpenseCategory.class);
        active.forEach(e -> byCategory.computeIfAbsent(e.getCategory(), c -> new ArrayList<>()).add(e));

        List<Responses.CategoryLine> lines = new ArrayList<>();
        byCategory.forEach((category, list) -> {
            BigDecimal catTotal = list.stream().map(Expense::getAmount)
                    .reduce(Money.zero(scale), BigDecimal::add).setScale(scale);
            Map<Long, BigDecimal> consumed = new LinkedHashMap<>();
            group.getParticipants().forEach(p -> consumed.put(p.getId(), Money.zero(scale)));
            list.forEach(e -> e.getShares().forEach(s ->
                    consumed.merge(s.getParticipant().getId(), s.getAmount(), BigDecimal::add)));
            BigDecimal pct = grandTotal.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : catTotal.multiply(HUNDRED).divide(grandTotal, 2, Money.ROUNDING);
            lines.add(new Responses.CategoryLine(category, catTotal, list.size(), pct,
                    group.getParticipants().stream()
                            .filter(p -> consumed.get(p.getId()).signum() != 0)
                            .map(p -> new Responses.ParticipantAmount(p.getId(), p.getName(),
                                    consumed.get(p.getId()).setScale(scale)))
                            .toList()));
        });
        return new Responses.CategorySummary(group.getId(), group.getCurrency(), grandTotal, lines);
    }

    private static final class Ledger {
        final Participant participant;
        BigDecimal paid;
        BigDecimal share;
        BigDecimal sent;
        BigDecimal received;

        Ledger(Participant participant, int scale) {
            this.participant = participant;
            this.paid = Money.zero(scale);
            this.share = Money.zero(scale);
            this.sent = Money.zero(scale);
            this.received = Money.zero(scale);
        }

        /** Positive: the group owes this person. Negative: this person owes the group. */
        BigDecimal net() {
            return paid.subtract(share).add(sent).subtract(received);
        }
    }
}
