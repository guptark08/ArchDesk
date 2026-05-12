package com.archdesk.payment;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.archdesk.client.ClientDtos.FeeRequest;
import com.archdesk.client.ClientDtos.LedgerResponse;
import com.archdesk.client.ClientDtos.PaymentEntryRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projectId}/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    LedgerResponse ledger(@PathVariable Long projectId) {
        return service.ledger(projectId);
    }

    @PutMapping("/fee")
    LedgerResponse updateFee(@PathVariable Long projectId, @Valid @RequestBody FeeRequest request) {
        return service.updateFee(projectId, request);
    }

    @PostMapping("/entries")
    LedgerResponse addEntry(@PathVariable Long projectId, @Valid @RequestBody PaymentEntryRequest request) {
        return service.addEntry(projectId, request);
    }

    @PutMapping("/entries/{entryId}")
    LedgerResponse updateEntry(@PathVariable Long projectId, @PathVariable Long entryId, @Valid @RequestBody PaymentEntryRequest request) {
        return service.updateEntry(projectId, entryId, request);
    }

    @DeleteMapping("/entries/{entryId}")
    LedgerResponse deleteEntry(@PathVariable Long projectId, @PathVariable Long entryId) {
        return service.deleteEntry(projectId, entryId);
    }
}
