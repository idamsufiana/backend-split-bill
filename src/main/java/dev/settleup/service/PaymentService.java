package dev.settleup.service;

import dev.settleup.calc.Money;
import dev.settleup.domain.BillGroup;
import dev.settleup.domain.Participant;
import dev.settleup.domain.Payment;
import dev.settleup.exception.BusinessRuleException;
import dev.settleup.repository.PaymentRepository;
import dev.settleup.domain.dto.ApiMapper;
import dev.settleup.domain.dto.Requests;
import dev.settleup.domain.dto.Responses;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Service
@Transactional
public class PaymentService {

    private final GroupService groupService;
    private final PaymentRepository payments;
    private final Clock clock;

    public PaymentService(GroupService groupService, PaymentRepository payments, Clock clock) {
        this.groupService = groupService;
        this.payments = payments;
        this.clock = clock;
    }

    /**
     * Records money that changed hands. Any positive amount is accepted, including partial
     * payments and payments not suggested by the settlement plan: people settle in the real world
     * in whatever amounts they like, and the ledger's job is to record that faithfully. The next
     * settlement is recomputed from the updated balances.
     */
    @EvictGroupCaches
    public Responses.Payment record(Long groupId, Requests.RecordPayment request) {
        BillGroup group = groupService.loadForUpdate(groupId);
        if (request.fromParticipantId().equals(request.toParticipantId())) {
            throw new BusinessRuleException("A participant cannot pay themselves");
        }
        Participant from = GroupService.requireMember(group, request.fromParticipantId(), "from_participant_id");
        Participant to = GroupService.requireMember(group, request.toParticipantId(), "to_participant_id");
        BigDecimal amount = Money.requirePositive(request.amount(), group.moneyScale(), "amount");

        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        Payment saved = payments.save(new Payment(group, from, to, amount, note, clock.instant()));
        return ApiMapper.payment(saved);
    }

    @Transactional(readOnly = true)
    public List<Responses.Payment> list(Long groupId) {
        groupService.load(groupId);
        return payments.findByGroupIdOrderByIdAsc(groupId).stream().map(ApiMapper::payment).toList();
    }
}
