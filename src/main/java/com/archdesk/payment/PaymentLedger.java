package com.archdesk.payment;

import java.util.ArrayList;
import java.util.List;

import com.archdesk.project.Project;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_ledgers")
public class PaymentLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, unique = true)
    private Project project;

    @Column(nullable = false)
    private Long totalAgreedFee = 0L;

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentEntry> entries = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Long getTotalAgreedFee() {
        return totalAgreedFee;
    }

    public void setTotalAgreedFee(Long totalAgreedFee) {
        this.totalAgreedFee = totalAgreedFee == null ? 0L : totalAgreedFee;
    }

    public List<PaymentEntry> getEntries() {
        return entries;
    }
}
