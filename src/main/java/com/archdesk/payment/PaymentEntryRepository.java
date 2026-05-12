package com.archdesk.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEntryRepository extends JpaRepository<PaymentEntry, Long> {
}
