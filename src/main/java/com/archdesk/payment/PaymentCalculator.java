package com.archdesk.payment;

import com.archdesk.project.ProjectStatus;

public final class PaymentCalculator {
    private PaymentCalculator() {
    }

    public static long totalPaid(PaymentLedger ledger) {
        return ledger.getEntries().stream().mapToLong(PaymentEntry::getAmount).sum();
    }

    public static long balanceDue(PaymentLedger ledger) {
        return Math.max(ledger.getTotalAgreedFee() - totalPaid(ledger), 0);
    }

    public static PaymentStatus status(PaymentLedger ledger) {
        long paid = totalPaid(ledger);
        long balance = balanceDue(ledger);
        if (balance == 0 && ledger.getTotalAgreedFee() > 0) {
            return PaymentStatus.FULLY_PAID;
        }
        if (ledger.getProject().getStatus() == ProjectStatus.COMPLETED && balance > 0) {
            return PaymentStatus.OVERDUE;
        }
        if (paid == 0) {
            return PaymentStatus.PENDING;
        }
        return PaymentStatus.PARTIAL;
    }
}
