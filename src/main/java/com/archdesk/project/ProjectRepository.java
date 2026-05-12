package com.archdesk.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @EntityGraph(attributePaths = {"ledger", "ledger.entries", "client"})
    Optional<Project> findWithLedgerById(Long id);

    @EntityGraph(attributePaths = {"ledger", "ledger.entries", "client"})
    @Query("select distinct p from Project p")
    List<Project> findAllWithLedger();
}
