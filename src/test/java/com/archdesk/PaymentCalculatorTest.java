package com.archdesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.archdesk.payment.PaymentCalculator;
import com.archdesk.payment.PaymentEntry;
import com.archdesk.payment.PaymentLedger;
import com.archdesk.payment.PaymentMode;
import com.archdesk.payment.PaymentStage;
import com.archdesk.payment.PaymentStatus;
import com.archdesk.payment.PaymentStatusRollup;
import com.archdesk.project.Project;
import com.archdesk.project.ProjectStatus;

class PaymentCalculatorTest {
    @Test
    void completedProjectWithBalanceIsOverdueEvenWithNoPayments() {
        PaymentLedger ledger = ledger(100_000, ProjectStatus.COMPLETED);

        assertThat(PaymentCalculator.status(ledger)).isEqualTo(PaymentStatus.OVERDUE);
    }

    @Test
    void partialPaymentCalculatesPaidAndBalance() {
        PaymentLedger ledger = ledger(100_000, ProjectStatus.ACTIVE);
        PaymentEntry entry = new PaymentEntry();
        entry.setAmount(25_000L);
        entry.setPaymentDate(LocalDate.now());
        entry.setMode(PaymentMode.UPI);
        entry.setStage(PaymentStage.TOKEN);
        ledger.getEntries().add(entry);

        assertThat(PaymentCalculator.totalPaid(ledger)).isEqualTo(25_000);
        assertThat(PaymentCalculator.balanceDue(ledger)).isEqualTo(75_000);
        assertThat(PaymentCalculator.status(ledger)).isEqualTo(PaymentStatus.PARTIAL);
    }

    private PaymentLedger ledger(long totalFee, ProjectStatus status) {
        Project project = new Project();
        project.setStatus(status);
        PaymentLedger ledger = new PaymentLedger();
        ledger.setProject(project);
        ledger.setTotalAgreedFee(totalFee);
        return ledger;
    }

    @Test
    void rollupReturnsFullyPaidWhenEveryProjectIsFullyPaid() {
        assertThat(PaymentStatusRollup.worst(java.util.List.of(PaymentStatus.FULLY_PAID, PaymentStatus.FULLY_PAID)))
                .isEqualTo(PaymentStatus.FULLY_PAID);
    }

    @Test
    void rollupUsesWorstStatus() {
        assertThat(PaymentStatusRollup.worst(java.util.List.of(PaymentStatus.FULLY_PAID, PaymentStatus.PARTIAL)))
                .isEqualTo(PaymentStatus.PARTIAL);
        assertThat(PaymentStatusRollup.worst(java.util.List.of(PaymentStatus.PENDING, PaymentStatus.OVERDUE)))
                .isEqualTo(PaymentStatus.OVERDUE);
    }

    @Test
    void rollupDefaultsToPendingWhenNoStatusesExist() {
        assertThat(PaymentStatusRollup.worst(java.util.List.of())).isEqualTo(PaymentStatus.PENDING);
    }
}
