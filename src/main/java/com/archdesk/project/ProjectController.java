package com.archdesk.project;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.archdesk.client.ClientDtos.ProjectRequest;
import com.archdesk.client.ClientDtos.ProjectResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clients/{clientId}/projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @PostMapping
    ProjectResponse create(@PathVariable Long clientId, @Valid @RequestBody ProjectRequest request) {
        return service.create(clientId, request);
    }

    @GetMapping("/{projectId}")
    ProjectResponse detail(@PathVariable Long clientId, @PathVariable Long projectId) {
        return service.detail(clientId, projectId);
    }

    @PutMapping("/{projectId}")
    ProjectResponse update(@PathVariable Long clientId, @PathVariable Long projectId, @Valid @RequestBody ProjectRequest request) {
        return service.update(clientId, projectId, request);
    }

    @DeleteMapping("/{projectId}")
    void delete(@PathVariable Long clientId, @PathVariable Long projectId) {
        service.delete(clientId, projectId);
    }
}
