package dev.settleup.service;

import dev.settleup.calc.Money;
import dev.settleup.calc.SplitCalculator;
import dev.settleup.calc.SplitCalculator.SplitInput;
import dev.settleup.calc.SplitType;
import dev.settleup.domain.BillGroup;
import dev.settleup.domain.Expense;
import dev.settleup.domain.ExpenseCategory;
import dev.settleup.domain.Participant;
import dev.settleup.exception.BusinessRuleException;
import dev.settleup.exception.NotFoundException;
import dev.settleup.repository.ExpenseRepository;
import dev.settleup.domain.dto.ApiMapper;
import dev.settleup.domain.dto.Requests;
import dev.settleup.domain.dto.Responses;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExpenseService {

    private final GroupService groupService;
    private final ExpenseRepository expenses;
    private final Clock clock;

    public ExpenseService(GroupService groupService, ExpenseRepository expenses, Clock clock) {
        this.groupService = groupService;
        this.expenses = expenses;
        this.clock = clock;
    }

    @EvictGroupCaches
    public Responses.Expense add(Long groupId, Requests.CreateExpense request) {
        BillGroup group = groupService.loadForUpdate(groupId);
        int scale = group.moneyScale();

        BigDecimal amount = Money.requirePositive(request.amount(), scale, "amount");
        Participant payer = GroupService.requireMember(group, request.paidBy(), "paid_by");
        SplitType splitType = request.splitType() == null ? SplitType.EQUAL : request.splitType();
        ExpenseCategory category = request.category() == null ? ExpenseCategory.OTHER : request.category();

        List<SplitInput> inputs = request.allocations() == null ? List.of()
                : request.allocations().stream()
                .map(a -> new SplitInput(a.participantId(), a.value()))
                .toList();

        Map<Long, Participant> members = group.getParticipants().stream()
                .collect(Collectors.toMap(Participant::getId, Function.identity()));
        Map<Long, BigDecimal> owed = SplitCalculator.calculate(splitType, amount, scale, members.keySet(), inputs);
        Map<Long, BigDecimal> requested = inputs.stream()
                .filter(i -> i.value() != null && splitType != SplitType.EQUAL)
                .collect(Collectors.toMap(SplitInput::participantId, SplitInput::value));

        Expense expense = new Expense(group, request.description().trim(), amount, payer, category,
                splitType, request.incurredOn(), clock.instant());
        owed.forEach((participantId, share) ->
                expense.addShare(members.get(participantId), share, requested.get(participantId)));

        return ApiMapper.expense(expenses.save(expense));
    }

    @Transactional(readOnly = true)
    public List<Responses.Expense> list(Long groupId, ExpenseCategory category, boolean includeVoided) {
        groupService.load(groupId);
        List<Expense> found = includeVoided
                ? expenses.findByGroupIdOrderByIdAsc(groupId)
                : expenses.findByGroupIdAndVoidedAtIsNullOrderByIdAsc(groupId);
        return found.stream()
                .filter(e -> category == null || e.getCategory() == category)
                .map(ApiMapper::expense)
                .toList();
    }

    @Transactional(readOnly = true)
    public Responses.Expense get(Long groupId, Long expenseId) {
        return ApiMapper.expense(load(groupId, expenseId));
    }

    /** Soft delete: the expense stays visible with {@code include_voided=true} for auditing. */
    @EvictGroupCaches
    public void voidExpense(Long groupId, Long expenseId) {
        groupService.loadForUpdate(groupId);
        Expense expense = load(groupId, expenseId);
        if (expense.isVoided()) {
            throw new BusinessRuleException("Expense " + expenseId + " is already voided");
        }
        expense.voidAt(clock.instant());
    }

    private Expense load(Long groupId, Long expenseId) {
        return expenses.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new NotFoundException("Expense", expenseId));
    }
}
