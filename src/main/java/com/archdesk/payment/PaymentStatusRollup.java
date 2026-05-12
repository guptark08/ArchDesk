package com.archdesk.payment;

import java.util.Collection;
import java.util.Comparator;

public final class PaymentStatusRollup {
    private PaymentStatusRollup() {
    }

    public static PaymentStatus worst(Collection<PaymentStatus> statuses) {
        return statuses.stream()
                .min(Comparator.comparingInt(PaymentStatusRollup::rank))
                .orElse(PaymentStatus.PENDING);
    }

    private static int rank(PaymentStatus status) {
        return switch (status) {
            case OVERDUE -> 0;
            case PARTIAL -> 1;
            case PENDING -> 2;
            case FULLY_PAID -> 3;
        };
    }
}
