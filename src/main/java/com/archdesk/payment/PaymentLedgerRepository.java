package com.archdesk.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentLedgerRepository extends JpaRepository<PaymentLedger, Long> {
    @EntityGraph(attributePaths = {"entries", "project"})
    Optional<PaymentLedger> findByProjectId(Long projectId);
}
