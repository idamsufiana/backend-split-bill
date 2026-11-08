package dev.settleup.repository;

import dev.settleup.domain.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"payer", "payee"})
    List<Payment> findByGroupIdOrderByIdAsc(Long groupId);
}
