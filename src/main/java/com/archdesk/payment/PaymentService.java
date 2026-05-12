package com.archdesk.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archdesk.client.ClientDtos;
import com.archdesk.client.ClientDtos.FeeRequest;
import com.archdesk.client.ClientDtos.LedgerResponse;
import com.archdesk.client.ClientDtos.PaymentEntryRequest;
import com.archdesk.common.NotFoundException;
import com.archdesk.project.ProjectService;

@Service
public class PaymentService {
    private final PaymentLedgerRepository ledgers;
    private final PaymentEntryRepository entries;
    private final ProjectService projects;

    public PaymentService(PaymentLedgerRepository ledgers, PaymentEntryRepository entries, ProjectService projects) {
        this.ledgers = ledgers;
        this.entries = entries;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public LedgerResponse ledger(Long projectId) {
        return ClientDtos.ledger(findLedger(projectId));
    }

    @Transactional
    public LedgerResponse updateFee(Long projectId, FeeRequest request) {
        PaymentLedger ledger = findLedger(projectId);
        ledger.setTotalAgreedFee(request.totalAgreedFee());
        return ClientDtos.ledger(ledger);
    }

    @Transactional
    public LedgerResponse addEntry(Long projectId, PaymentEntryRequest request) {
        PaymentLedger ledger = findLedger(projectId);
        PaymentEntry entry = new PaymentEntry();
        entry.setLedger(ledger);
        apply(entry, request);
        entries.save(entry);
        ledger.getEntries().add(entry);
        return ClientDtos.ledger(ledger);
    }

    @Transactional
    public LedgerResponse updateEntry(Long projectId, Long entryId, PaymentEntryRequest request) {
        PaymentLedger ledger = findLedger(projectId);
        PaymentEntry entry = ledger.getEntries().stream()
                .filter(found -> found.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Payment entry not found"));
        apply(entry, request);
        return ClientDtos.ledger(ledger);
    }

    @Transactional
    public LedgerResponse deleteEntry(Long projectId, Long entryId) {
        PaymentLedger ledger = findLedger(projectId);
        PaymentEntry entry = ledger.getEntries().stream()
                .filter(found -> found.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Payment entry not found"));
        ledger.getEntries().remove(entry);
        entries.delete(entry);
        return ClientDtos.ledger(ledger);
    }

    private PaymentLedger findLedger(Long projectId) {
        projects.findWithLedger(projectId);
        return ledgers.findByProjectId(projectId).orElseThrow(() -> new NotFoundException("Payment ledger not found"));
    }

    private void apply(PaymentEntry entry, PaymentEntryRequest request) {
        entry.setAmount(request.amount());
        entry.setPaymentDate(request.paymentDate());
        entry.setMode(request.mode());
        entry.setStage(request.stage());
        entry.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
    }
}
