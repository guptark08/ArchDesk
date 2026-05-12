package com.archdesk.project;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archdesk.client.ClientDtos;
import com.archdesk.client.ClientDtos.ProjectRequest;
import com.archdesk.client.ClientDtos.ProjectResponse;
import com.archdesk.client.ClientRepository;
import com.archdesk.common.NotFoundException;
import com.archdesk.payment.PaymentLedger;

@Service
public class ProjectService {
    private final ClientRepository clients;
    private final ProjectRepository projects;

    public ProjectService(ClientRepository clients, ProjectRepository projects) {
        this.clients = clients;
        this.projects = projects;
    }

    @Transactional
    public ProjectResponse create(Long clientId, ProjectRequest request) {
        var client = clients.findById(clientId).orElseThrow(() -> new NotFoundException("Client not found"));
        Project project = new Project();
        project.setClient(client);
        apply(project, request);
        PaymentLedger ledger = new PaymentLedger();
        ledger.setProject(project);
        project.setLedger(ledger);
        Project saved = projects.save(project);
        return ClientDtos.project(saved);
    }

    @Transactional(readOnly = true)
    public ProjectResponse detail(Long clientId, Long projectId) {
        return ClientDtos.project(findOwned(clientId, projectId));
    }

    @Transactional
    public ProjectResponse update(Long clientId, Long projectId, ProjectRequest request) {
        Project project = findOwned(clientId, projectId);
        apply(project, request);
        return ClientDtos.project(project);
    }

    @Transactional
    public void delete(Long clientId, Long projectId) {
        projects.delete(findOwned(clientId, projectId));
    }

    public Project findWithLedger(Long id) {
        return projects.findWithLedgerById(id).orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private Project findOwned(Long clientId, Long projectId) {
        Project project = findWithLedger(projectId);
        if (!project.getClient().getId().equals(clientId)) {
            throw new NotFoundException("Project not found for client");
        }
        return project;
    }

    private void apply(Project project, ProjectRequest request) {
        project.setName(request.name().trim());
        project.setProjectType(request.projectType());
        project.setStatus(request.status());
        project.setPlotSize(blankToNull(request.plotSize()));
        project.setApproximateBudget(request.approximateBudget());
        project.setRequirements(blankToNull(request.requirements()));
        project.setNotes(blankToNull(request.notes()));
        project.setStartDate(request.startDate());
        project.setExpectedCompletion(request.expectedCompletion());
        project.setActualCompletion(request.actualCompletion());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
