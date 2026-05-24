package com.archdesk.client;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.archdesk.client.ClientDtos.ClientDetail;
import com.archdesk.client.ClientDtos.ClientRequest;
import com.archdesk.client.ClientDtos.ClientSummary;
import com.archdesk.payment.PaymentStatus;
import com.archdesk.project.ProjectStatus;
import com.archdesk.project.ProjectType;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clients")
public class ClientController {
    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @GetMapping
    List<ClientSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) ProjectType projectType,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer limit) {
        return service.list(search, status, projectType, paymentStatus, sort, limit);
    }

    @PostMapping
    ClientDetail create(@Valid @RequestBody ClientRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    ClientDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PutMapping("/{id}")
    ClientDetail update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
