package dev.settleup.controller;

import dev.settleup.service.SettlementService;
import dev.settleup.domain.dto.Responses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}")
public class SettlementController {

    private final SettlementService settlements;

    public SettlementController(SettlementService settlements) {
        this.settlements = settlements;
    }

    @GetMapping("/settlement")
    public Responses.Settlement settlement(@PathVariable Long groupId) {
        return settlements.settlement(groupId);
    }

    @GetMapping("/summary/categories")
    public Responses.CategorySummary categories(@PathVariable Long groupId) {
        return settlements.categorySummary(groupId);
    }
}
