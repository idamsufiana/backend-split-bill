package dev.settleup;

import dev.settleup.config.CacheConfig;
import dev.settleup.exception.BusinessRuleException;
import dev.settleup.service.ExpenseService;
import dev.settleup.service.GroupService;
import dev.settleup.service.PaymentService;
import dev.settleup.service.SettlementService;
import dev.settleup.domain.dto.Requests;
import dev.settleup.domain.dto.Responses;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency guarantees from the pessimistic group lock, and cache eviction wiring.
 * Uses Spring's in-memory cache so the build needs no Redis; the eviction annotations and
 * cache names are exactly the ones used against Redis in production.
 */
@SpringBootTest(properties = {"app.github-username=a", "spring.cache.type=simple"})
class ConcurrencyAndCachingTest {

    private static final int THREADS = 8;

    @Autowired
    GroupService groups;
    @Autowired
    ExpenseService expenses;
    @Autowired
    PaymentService payments;
    @Autowired
    SettlementService settlements;
    @Autowired
    CacheManager cacheManager;

    @Test
    void concurrentDuplicateNamesOnlyOneWins() throws Exception {
        long groupId = newGroup("Alice", "Bob");

        List<Boolean> outcomes = runConcurrently(() -> {
            try {
                groups.addParticipant(groupId, new Requests.AddParticipant("Dave"));
                return true;
            } catch (BusinessRuleException duplicate) {
                return false;
            }
        });

        assertThat(outcomes).containsOnlyOnce(true);
        assertThat(groups.get(groupId).participants())
                .extracting(Responses.ParticipantRef::name)
                .containsExactly("Alice", "Bob", "Dave");
    }

    @Test
    void concurrentVoidsOfTheSameExpenseOnlyOneWins() throws Exception {
        long groupId = newGroup("Alice", "Bob");
        long alice = groups.get(groupId).participants().get(0).id();
        long expenseId = expenses.add(groupId, expense("Taxi", "20.00", alice)).id();

        List<Boolean> outcomes = runConcurrently(() -> {
            try {
                expenses.voidExpense(groupId, expenseId);
                return true;
            } catch (BusinessRuleException alreadyVoided) {
                return false;
            }
        });

        assertThat(outcomes).containsOnlyOnce(true);
    }

    @Test
    void concurrentExpensesAndPaymentsAreAllCountedAndTheLedgerStaysBalanced() throws Exception {
        long groupId = newGroup("Alice", "Bob");
        List<Responses.ParticipantRef> people = groups.get(groupId).participants();
        long alice = people.get(0).id();
        long bob = people.get(1).id();

        runConcurrently(() -> {
            expenses.add(groupId, expense("Snack", "3.00", alice));
            payments.record(groupId, new Requests.RecordPayment(bob, alice, new BigDecimal("0.50"), null));
            return true;
        });

        Responses.Settlement s = settlements.settlement(groupId);
        assertThat(s.totalExpenses()).isEqualByComparingTo("24.00");          // 8 × 3.00
        // Bob's share 8 × 1.50 = 12.00, he paid back 8 × 0.50 = 4.00 → still owes 8.00
        assertThat(s.transactions()).singleElement()
                .satisfies(t -> assertThat(t.amount()).isEqualByComparingTo("8.00"));
        assertThat(s.balances().stream().map(Responses.Balance::netBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0");
    }

    @Test
    void writesEvictTheGroupsCachedSettlementButNotOtherGroups() {
        long groupId = newGroup("Alice", "Bob");
        long otherGroupId = newGroup("Carol", "Dan");
        long alice = groups.get(groupId).participants().get(0).id();
        Cache cache = cacheManager.getCache(CacheConfig.SETTLEMENTS);

        settlements.settlement(groupId);
        settlements.settlement(otherGroupId);
        assertThat(cache.get(groupId)).as("cached after first read").isNotNull();

        expenses.add(groupId, expense("Lunch", "10.00", alice));

        assertThat(cache.get(groupId)).as("evicted by the write").isNull();
        assertThat(cache.get(otherGroupId)).as("other groups untouched").isNotNull();
        assertThat(settlements.settlement(groupId).totalExpenses()).isEqualByComparingTo("10.00");
    }

    // ---------------------------------------------------------------- helpers

    private long newGroup(String... names) {
        return groups.create(new Requests.CreateGroup("Test", "USD", List.of(names))).id();
    }

    private static Requests.CreateExpense expense(String description, String amount, long paidBy) {
        return new Requests.CreateExpense(description, new BigDecimal(amount), paidBy, null, null, null, null);
    }

    /** Starts THREADS copies of the task at the same instant and collects their results. */
    private static <T> List<T> runConcurrently(Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
