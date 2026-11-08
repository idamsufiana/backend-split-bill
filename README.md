# SettleUp: Shared Expense API

A Spring Boot REST API for groups that share costs (a trip, a flat, a team lunch). Members log who paid for what and who it was for. The API works out the fewest payments needed to settle up and records those payments as they happen.

## Build and run

Requirements: Java 17+ and Maven 3.9+, or just Docker. Redis is used as a cache but isn't required (see below).

## API

All bodies are JSON in `snake_case`. **Money is returned as JSON strings** (`"33.34"`) so that clients which parse JSON numbers as floats (JavaScript, for example) can't introduce rounding errors. Requests accept a string or a number.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/groups` | Create a group with participants |
| `GET` | `/api/groups` | List groups |
| `GET` | `/api/groups/{id}` | Get a group |
| `POST` | `/api/groups/{id}/participants` | Add a participant later |
| `POST` | `/api/groups/{id}/expenses` | Add an expense |
| `GET` | `/api/groups/{id}/expenses?category=FOOD&include_voided=true` | List expenses |
| `GET` | `/api/groups/{id}/expenses/{expenseId}` | Get one expense with its resolved shares |
| `DELETE` | `/api/groups/{id}/expenses/{expenseId}` | Void an expense (soft delete, stays in the audit trail) |
| `POST` | `/api/groups/{id}/payments` | Record a settle-up payment |
| `GET` | `/api/groups/{id}/payments` | List payments |
| `GET` | `/api/groups/{id}/settlement` | Balances plus who pays whom, with the service charge |
| `GET` | `/api/groups/{id}/summary/categories` | Spend per category and who consumed it |

Errors are RFC 9457 `application/problem+json` responses:
- **400**: malformed input (missing fields, bad JSON, unknown enum value)
- **404**: the group or expense doesn't exist
- **409**: another change to the same group held the lock for more than 5 seconds. The response includes `Retry-After: 1`, and the request is safe to retry.
- **422**: the input is well-formed but breaks a domain rule (percentages don't sum to 100, sub-cent amount, paying yourself, and so on)

### Example session

The curl commands below follow one scenario: Alice, Bob and Carol go on a weekend trip. The IDs assume a fresh server.

**Create a group** (currency defaults to `USD`; any ISO-4217 code works, and `JPY` uses whole yen):
```bash
curl -s -X POST localhost:8080/api/groups -H 'Content-Type: application/json' -d '{
  "name": "Bandung weekend",
  "currency": "USD",
  "participants": ["Alice", "Bob", "Carol"]
}'
```
```json
{"id":1,"name":"Bandung weekend","currency":"USD","created_at":"2026-11-06T04:40:00Z",
 "participants":[{"id":1,"name":"Alice"},{"id":2,"name":"Bob"},{"id":3,"name":"Carol"}]}
```

**Get / list groups; add a participant:**
```bash
curl -s localhost:8080/api/groups/1
curl -s localhost:8080/api/groups
curl -s -X POST localhost:8080/api/groups/1/participants -H 'Content-Type: application/json' -d '{"name": "Dave"}'
```

**Add expenses.** There are four split strategies. `split_type` defaults to `EQUAL`, and `category` defaults to `OTHER`.

```bash
# EQUAL: omit allocations to split across everyone. 100.00 / 3 → 33.34, 33.33, 33.33
curl -s -X POST localhost:8080/api/groups/1/expenses -H 'Content-Type: application/json' -d '{
  "description": "Dinner", "amount": "100.00", "paid_by": 2, "category": "FOOD"
}'

# EQUAL among a subset
curl -s -X POST localhost:8080/api/groups/1/expenses -H 'Content-Type: application/json' -d '{
  "description": "Taxi", "amount": "24.00", "paid_by": 3, "category": "TRANSPORT",
  "allocations": [{"participant_id": 1}, {"participant_id": 3}]
}'

# SHARES: weights, e.g. nights stayed (Alice 1, Bob 2, Carol 2)
curl -s -X POST localhost:8080/api/groups/1/expenses -H 'Content-Type: application/json' -d '{
  "description": "Hotel", "amount": "300.00", "paid_by": 1, "category": "ACCOMMODATION",
  "split_type": "SHARES", "incurred_on": "2026-11-01",
  "allocations": [{"participant_id": 1, "value": 1}, {"participant_id": 2, "value": 2}, {"participant_id": 3, "value": 2}]
}'

# PERCENTAGE: values must sum to exactly 100
curl -s -X POST localhost:8080/api/groups/1/expenses -H 'Content-Type: application/json' -d '{
  "description": "Car rental", "amount": "90.00", "paid_by": 1, "category": "TRANSPORT",
  "split_type": "PERCENTAGE",
  "allocations": [{"participant_id": 1, "value": 50}, {"participant_id": 2, "value": 25}, {"participant_id": 3, "value": 25}]
}'

# EXACT: values must sum to exactly the expense amount
curl -s -X POST localhost:8080/api/groups/1/expenses -H 'Content-Type: application/json' -d '{
  "description": "Souvenirs", "amount": "45.50", "paid_by": 3, "category": "OTHER",
  "split_type": "EXACT",
  "allocations": [{"participant_id": 1, "value": "10.00"}, {"participant_id": 3, "value": "35.50"}]
}'
```

**List, filter, fetch, void:**
```bash
curl -s 'localhost:8080/api/groups/1/expenses?category=TRANSPORT'
curl -s localhost:8080/api/groups/1/expenses/1
curl -s -X DELETE localhost:8080/api/groups/1/expenses/5 -i          # 204, voided
curl -s 'localhost:8080/api/groups/1/expenses?include_voided=true'   # still visible, with voided_at
```

**Settlement:**
```bash
curl -s localhost:8080/api/groups/1/settlement
```
```json
{
  "group_id": 1, "currency": "USD",
  "total_expenses": "514.00",
  "service_charge_pct": 4,
  "service_charge_amount": "20.56",
  "settled": false, "transaction_count": 2,
  "balances": [
    {"participant_id": 1, "name": "Alice", "paid": "390.00", "share": "150.34",
     "payments_sent": "0.00", "payments_received": "0.00", "net_balance": "239.66"},
    {"participant_id": 2, "name": "Bob",   "paid": "100.00", "share": "175.83", "...": "...", "net_balance": "-75.83"},
    {"participant_id": 3, "name": "Carol", "paid": "24.00",  "share": "187.83", "...": "...", "net_balance": "-163.83"}
  ],
  "transactions": [
    {"from": {"id": 2, "name": "Bob"},   "to": {"id": 1, "name": "Alice"}, "amount": "75.83"},
    {"from": {"id": 3, "name": "Carol"}, "to": {"id": 1, "name": "Alice"}, "amount": "163.83"}
  ],
  "calculated_at": "2026-11-06T04:41:00Z"
}
```
(The figures are illustrative: they assume the souvenirs expense was voided and a 7% service charge. Yours will reflect your own username.)

**Record payments.** Partial payments are fine, and the next settlement is recomputed from the updated balances:
```bash
curl -s -X POST localhost:8080/api/groups/1/payments -H 'Content-Type: application/json' -d '{
  "from_participant_id": 3, "to_participant_id": 1, "amount": "163.83", "note": "bank transfer"
}'
curl -s localhost:8080/api/groups/1/payments
```

**Category summary:**
```bash
curl -s localhost:8080/api/groups/1/summary/categories
```
```json
{"group_id":1,"currency":"USD","total_expenses":"514.00","categories":[
  {"category":"FOOD","total":"100.00","expense_count":1,"percentage_of_total":"19.46",
   "consumed_by":[{"participant_id":1,"name":"Alice","amount":"33.34"}, "..."]},
  "..."]}
```

---

## Design notes

**Domain model.** `BillGroup` 1→n `Participant`, `Expense` and `Payment`, and `Expense` 1→n `ExpenseShare`. Participants belong to a single group, because there are no user accounts and "Alice" in two different groups has two unrelated ledgers. Each group has one currency.

**Shares are resolved when the expense is written.** The split request (percentages, weights, exact amounts) is turned into concrete `ExpenseShare` rows when the expense is created. The original requested value is kept next to each share for auditing. Reads never re-split or re-round anything, so historical numbers can't change if the rounding logic later does.

**Money handling.**
- Every amount is a `BigDecimal`, stored as `DECIMAL(19,4)` and normalised to the currency's minor unit (`Currency.getDefaultFractionDigits()`, so 2 for USD/IDR and 0 for JPY).
- Input with more precision than the currency allows (`10.005 USD`) is rejected with a 422 instead of being silently rounded.
- Splits use the largest-remainder method on minor units, so shares always sum to exactly the expense amount. 100.00 split three ways is 33.34/33.33/33.33, never 99.99. The odd cent goes to the participant with the largest fractional remainder, with ties going to the earliest-listed participant, so results are deterministic.
- Derived values (the service charge and category percentages) use `HALF_EVEN` rounding.
- Money is serialised as strings.

**Balances and settlement are derived on every request, never stored.** `net = paid − share + payments_sent − payments_received`. Because nothing is cached, there is no stored balance that can drift out of sync with the ledger.

**Settlement optimisation.** The minimum number of transfers is `n − k`, where `k` is the largest number of disjoint zero-sum subgroups the non-zero balances can be split into. That problem is NP-hard. For up to 16 non-zero balances, `SettlementCalculator` finds `k` exactly with a subset DP (2¹⁶ states, sub-millisecond). Each subgroup then settles with greedy largest-debtor → largest-creditor matching. Above 16 it falls back to greedy over the whole group, which never needs more than `n − 1` transfers.

**Concurrency: a pessimistic lock per group.** Every write to a group (adding an expense, voiding one, recording a payment, adding a participant) starts with `SELECT … FOR UPDATE` on that group's row (`BillGroupRepository.findByIdForUpdate`). Writes to the *same* group are therefore serialised, while different groups never block each other. This closes check-then-act races that validation alone can't prevent:
- two simultaneous requests both adding "Dave" would each pass the unique-name check;
- two simultaneous voids of the same expense would both see it as not yet voided.

I chose pessimistic over optimistic locking because writes to one group are rare and brief, and a short wait is better UX than returning an error the client has to retry. If the lock can't be acquired within 5 seconds, the API returns **409** with `Retry-After`. Participants are loaded *after* the lock is taken, not fetch-joined, so the check sees what the previous lock holder committed.

**Caching with Redis.** Settlement and category summaries are derived on every read, so they're cached in Redis per group (`settleup:settlements::{groupId}`, `settleup:category-summaries::{groupId}`):
- Any write to a group evicts that group's two entries (`@EvictGroupCaches`), and other groups stay cached.
- The cache is **transaction-aware**: evictions run *after* the commit. Evicting before the commit would let a concurrent reader re-cache the old numbers in between.
- Values are stored as typed JSON with no Java serialization, so they're readable in `redis-cli`.
- Entries also expire after a TTL as a safety net.
- **Redis is optional.** Cache errors are logged and the request falls through to the database, so an outage makes responses slower but never wrong or failing.
- Because H2 here is in-memory, the caches are cleared on startup, so a new "group 1" is never served a previous run's numbers. With a persistent database this step should be removed.

**Audit trail.** Expenses and payments carry `created_at`, and expenses keep an optional `incurred_on` date. Expenses are voided rather than deleted, so `?include_voided=true` shows the full history. Timestamps come from an injected `Clock`.

**Service charge.** It is reported for the group as a whole and is **not** added into the transfers. The brief defines it as a percentage of total expenses, but nobody in the group is the obvious recipient, so adding it to anyone's debt would make the settlement wrong.

### Testing

- `SettlementCalculatorTest` covers the core logic: hand-written scenarios, including one where naive greedy needs an extra transfer, plus 2,000 randomised groups. For every case it asserts that (1) executing the transfers brings every balance to exactly zero, (2) every transfer is positive and between two different people, and (3) the transfer count equals a **brute-force optimum**.
- `SplitCalculatorTest` covers all four strategies, the odd-cent rule and the validation rules.
- `ServiceChargeCalculatorTest` covers the worked example from the brief (`johndoe47` → 0), case-insensitivity and rounding.
- `ConcurrencyAndCachingTest` fires 8 threads at once and checks three things: only one "Dave" is created, only one void succeeds, and concurrent expenses plus payments are all counted with the ledger still balancing to zero. It also checks that a write evicts only its own group's cached settlement.
- `SettleUpApiIntegrationTest` runs a full trip through HTTP, JSON and H2: expenses, settlement, partial payments to fully settled, voiding, the category summary, and the 400/404/422 paths.

I tested the calculation logic most heavily because a mistake there means real people pay the wrong amounts. The controllers are thin and are covered by the integration test.

### Possible next steps

- Real persistence (PostgreSQL plus Flyway migrations)
- Multi-currency groups with FX snapshots
- Authentication with groups owned by users
- Pagination on the list endpoints

---

## Submission question

> *What was the hardest design decision you made while building this, and what trade-off did you accept?*

The hardest decision was what "the fairest way to settle up" should mean: keep the real debts (Carol pays Bob back for the dinner Bob bought), or net everything into the fewest possible transfers. I chose the fewest transfers, because that is what the person organising the settle-up actually feels, and I accepted that a suggested payment can now connect two people who never shared an expense. To make up for that, every settlement also returns each person's full breakdown (paid, share, payments sent and received), so anyone can see why they owe what they owe. The choice brought a second trade-off: finding the true minimum is NP-hard, so I compute it exactly with a subset DP for up to 16 people and fall back to a greedy heuristic above that. That is more complex than greedy-only, so the tests check the result against a brute-force optimum.
