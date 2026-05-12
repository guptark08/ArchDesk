package com.archdesk.payment;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_entries")
public class PaymentEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private PaymentLedger ledger;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMode mode;

    @Enumerated(EnumType.STRING)
    @Column
    private PaymentStage stage = PaymentStage.PROGRESS;

    @Column(columnDefinition = "text")
    private String notes;

    public Long getId() {
        return id;
    }

    public PaymentLedger getLedger() {
        return ledger;
    }

    public void setLedger(PaymentLedger ledger) {
        this.ledger = ledger;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public PaymentMode getMode() {
        return mode;
    }

    public void setMode(PaymentMode mode) {
        this.mode = mode;
    }

    public PaymentStage getStage() {
        return stage == null ? PaymentStage.PROGRESS : stage;
    }

    public void setStage(PaymentStage stage) {
        this.stage = stage == null ? PaymentStage.PROGRESS : stage;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
