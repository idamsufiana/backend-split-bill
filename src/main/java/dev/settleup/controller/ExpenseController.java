package dev.settleup.controller;

import dev.settleup.domain.ExpenseCategory;
import dev.settleup.service.ExpenseService;
import dev.settleup.domain.dto.Requests;
import dev.settleup.domain.dto.Responses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
public class ExpenseController {

    private final ExpenseService expenses;

    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @PostMapping
    public ResponseEntity<Responses.Expense> add(@PathVariable Long groupId,
                                                 @Valid @RequestBody Requests.CreateExpense request) {
        Responses.Expense created = expenses.add(groupId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<Responses.Expense> list(@PathVariable Long groupId,
                                        @RequestParam(required = false) ExpenseCategory category,
                                        @RequestParam(name = "include_voided", defaultValue = "false")
                                        boolean includeVoided) {
        return expenses.list(groupId, category, includeVoided);
    }

    @GetMapping("/{expenseId}")
    public Responses.Expense get(@PathVariable Long groupId, @PathVariable Long expenseId) {
        return expenses.get(groupId, expenseId);
    }

    /** Voids (soft-deletes) the expense; it no longer affects balances but stays in the audit trail. */
    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> voidExpense(@PathVariable Long groupId, @PathVariable Long expenseId) {
        expenses.voidExpense(groupId, expenseId);
        return ResponseEntity.noContent().build();
    }
}
