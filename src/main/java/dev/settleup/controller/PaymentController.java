package dev.settleup.controller;

import dev.settleup.service.PaymentService;
import dev.settleup.domain.dto.Requests;
import dev.settleup.domain.dto.Responses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.Payment record(@PathVariable Long groupId,
                                    @Valid @RequestBody Requests.RecordPayment request) {
        return payments.record(groupId, request);
    }

    @GetMapping
    public List<Responses.Payment> list(@PathVariable Long groupId) {
        return payments.list(groupId);
    }
}
