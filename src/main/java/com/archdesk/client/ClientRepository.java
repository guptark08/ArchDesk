package com.archdesk.client;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, Long> {
    @Query("select distinct c from Client c where c.id = :id")
    java.util.Optional<Client> findDetailById(@Param("id") Long id);

    @Query("""
            select distinct c from Client c
            left join c.projects p
            left join c.meetingNotes n
            where :search is null
               or lower(c.fullName) like lower(concat('%', :search, '%'))
               or lower(c.phoneNumber) like lower(concat('%', :search, '%'))
               or lower(coalesce(c.addressLocality, '')) like lower(concat('%', :search, '%'))
               or lower(str(c.defaultProjectType)) like lower(concat('%', :search, '%'))
               or lower(coalesce(c.generalNotes, '')) like lower(concat('%', :search, '%'))
               or lower(coalesce(p.name, '')) like lower(concat('%', :search, '%'))
               or lower(coalesce(p.notes, '')) like lower(concat('%', :search, '%'))
               or lower(coalesce(p.requirements, '')) like lower(concat('%', :search, '%'))
               or lower(coalesce(n.content, '')) like lower(concat('%', :search, '%'))
            """)
    List<Client> search(@Param("search") String search);
}
