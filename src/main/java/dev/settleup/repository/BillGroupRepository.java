package dev.settleup.repository;

import dev.settleup.domain.BillGroup;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BillGroupRepository extends JpaRepository<BillGroup, Long> {

    @EntityGraph(attributePaths = "participants")
    Optional<BillGroup> findWithParticipantsById(Long id);

    @EntityGraph(attributePaths = "participants")
    List<BillGroup> findAllByOrderByIdAsc();

    /**
     * {@code SELECT ... FOR UPDATE} on the group row. Every write to a group takes this lock
     * first, so writes to the <em>same</em> group run one at a time while different groups never
     * block each other. Participants are deliberately not fetch-joined here: they are loaded
     * lazily <em>after</em> the lock is held, so the duplicate-name check sees rows committed
     * by the previous lock holder. (Some databases, PostgreSQL among them, also refuse
     * {@code FOR UPDATE} on the nullable side of an outer join.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select g from BillGroup g where g.id = :id")
    Optional<BillGroup> findByIdForUpdate(@Param("id") Long id);
}
