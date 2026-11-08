package dev.settleup.repository;

import dev.settleup.domain.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = {"paidBy", "shares", "shares.participant"})
    List<Expense> findByGroupIdOrderByIdAsc(Long groupId);

    @EntityGraph(attributePaths = {"paidBy", "shares", "shares.participant"})
    List<Expense> findByGroupIdAndVoidedAtIsNullOrderByIdAsc(Long groupId);

    @EntityGraph(attributePaths = {"paidBy", "shares", "shares.participant"})
    Optional<Expense> findByIdAndGroupId(Long id, Long groupId);
}
