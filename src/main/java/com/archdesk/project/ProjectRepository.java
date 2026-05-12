package com.archdesk.project;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @EntityGraph(attributePaths = {"ledger", "ledger.entries", "client", "sketches"})
    Optional<Project> findWithLedgerById(Long id);
}
