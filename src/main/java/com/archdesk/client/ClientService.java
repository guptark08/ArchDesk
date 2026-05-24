package com.archdesk.client;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archdesk.client.ClientDtos.ClientDetail;
import com.archdesk.client.ClientDtos.ClientRequest;
import com.archdesk.client.ClientDtos.ClientSummary;
import com.archdesk.common.NotFoundException;
import com.archdesk.payment.PaymentCalculator;
import com.archdesk.payment.PaymentStatus;
import com.archdesk.payment.PaymentStatusRollup;
import com.archdesk.project.ProjectStatus;
import com.archdesk.project.ProjectType;

@Service
public class ClientService {
    private final ClientRepository clients;

    public ClientService(ClientRepository clients) {
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public List<ClientSummary> list(String search, ProjectStatus status, ProjectType projectType, PaymentStatus paymentStatus, String sort, Integer limit) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        long maxResults = limit == null ? 100 : Math.max(1, Math.min(limit, 100));
        Comparator<Client> comparator = switch (sort == null ? "" : sort) {
            case "created_asc" -> Comparator.comparing(Client::getCreatedAt);
            case "name_asc" -> Comparator.comparing(Client::getFullName, String.CASE_INSENSITIVE_ORDER);
            case "name_desc" -> Comparator.comparing(Client::getFullName, String.CASE_INSENSITIVE_ORDER).reversed();
            case "updated_desc" -> Comparator.comparing(Client::getUpdatedAt).reversed();
            default -> Comparator.comparing(Client::getCreatedAt).reversed();
        };

        List<Client> source = normalizedSearch == null ? clients.findAll() : clients.search(normalizedSearch);

        return source.stream()
                .filter(client -> status == null || client.getProjectStatus() == status)
                .filter(client -> projectType == null || client.getDefaultProjectType() == projectType)
                .filter(client -> paymentStatus == null || clientPaymentStatus(client) == paymentStatus)
                .sorted(comparator)
                .limit(maxResults)
                .map(ClientDtos::summary)
                .toList();
    }

    @Transactional
    public ClientDetail create(ClientRequest request) {
        Client client = new Client();
        apply(client, request);
        return ClientDtos.detail(clients.save(client));
    }

    @Transactional(readOnly = true)
    public ClientDetail detail(Long id) {
        return ClientDtos.detail(findDetail(id));
    }

    @Transactional
    public ClientDetail update(Long id, ClientRequest request) {
        Client client = find(id);
        apply(client, request);
        return ClientDtos.detail(client);
    }

    @Transactional
    public void delete(Long id) {
        clients.delete(find(id));
    }

    private Client find(Long id) {
        return clients.findById(id).orElseThrow(() -> new NotFoundException("Client not found"));
    }

    Client findDetail(Long id) {
        return clients.findDetailById(id).orElseThrow(() -> new NotFoundException("Client not found"));
    }

    private void apply(Client client, ClientRequest request) {
        client.setFullName(request.fullName().trim());
        client.setPhoneNumber(request.phoneNumber().trim());
        client.setEmailAddress(blankToNull(request.emailAddress()));
        client.setAddressLocality(blankToNull(request.addressLocality()));
        client.setDefaultProjectType(request.defaultProjectType());
        client.setPlotSize(blankToNull(request.plotSize()));
        client.setApproximateBudget(request.approximateBudget());
        client.setProjectStatus(request.projectStatus());
        client.setGeneralNotes(blankToNull(request.generalNotes()));
    }

    private PaymentStatus clientPaymentStatus(Client client) {
        if (client.getProjects().isEmpty()) {
            return PaymentStatus.PENDING;
        }
        return client.getProjects().stream()
                .map(project -> project.getLedger())
                .filter(java.util.Objects::nonNull)
                .map(PaymentCalculator::status)
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), PaymentStatusRollup::worst));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
