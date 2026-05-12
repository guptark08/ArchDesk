package com.archdesk.client;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.archdesk.note.MeetingNote;
import com.archdesk.payment.PaymentCalculator;
import com.archdesk.payment.PaymentMode;
import com.archdesk.payment.PaymentStage;
import com.archdesk.payment.PaymentStatus;
import com.archdesk.payment.PaymentStatusRollup;
import com.archdesk.project.Project;
import com.archdesk.project.ProjectStatus;
import com.archdesk.project.ProjectType;
import com.archdesk.sketch.ProjectSketch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public final class ClientDtos {
    private ClientDtos() {
    }

    public record ClientRequest(
            @NotBlank String fullName,
            @NotBlank String phoneNumber,
            String emailAddress,
            String addressLocality,
            @NotNull ProjectType defaultProjectType,
            String plotSize,
            @PositiveOrZero Long approximateBudget,
            @NotNull ProjectStatus projectStatus,
            String generalNotes) {
    }

    public record ClientSummary(
            Long id,
            String fullName,
            String phoneNumber,
            String addressLocality,
            ProjectType defaultProjectType,
            ProjectStatus projectStatus,
            PaymentStatus paymentStatus,
            long projectCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ClientDetail(
            Long id,
            String fullName,
            String phoneNumber,
            String emailAddress,
            String addressLocality,
            ProjectType defaultProjectType,
            String plotSize,
            Long approximateBudget,
            ProjectStatus projectStatus,
            String generalNotes,
            Instant createdAt,
            Instant updatedAt,
            List<ProjectResponse> projects,
            List<MeetingNoteResponse> meetingNotes) {
    }

    public record ProjectRequest(
            @NotBlank String name,
            @NotNull ProjectType projectType,
            @NotNull ProjectStatus status,
            String plotSize,
            @PositiveOrZero Long approximateBudget,
            String requirements,
            String notes) {
    }

    public record ProjectResponse(
            Long id,
            String name,
            ProjectType projectType,
            ProjectStatus status,
            String plotSize,
            Long approximateBudget,
            String requirements,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            LedgerResponse ledger,
            List<SketchResponse> sketches) {
    }

    public record SketchResponse(
            Long id,
            String fileName,
            String originalName,
            String caption,
            Long fileSize,
            String mimeType,
            Instant uploadedAt) {
    }

    public record MeetingNoteRequest(@NotNull LocalDate noteDate, @NotBlank String content) {
    }

    public record MeetingNoteResponse(Long id, LocalDate noteDate, String content, Instant createdAt, Instant updatedAt) {
    }

    public record FeeRequest(@PositiveOrZero Long totalAgreedFee) {
    }

    public record PaymentEntryRequest(
            @PositiveOrZero Long amount,
            @NotNull LocalDate paymentDate,
            @NotNull PaymentMode mode,
            PaymentStage stage,
            String notes) {
    }

    public record PaymentEntryResponse(Long id, Long amount, LocalDate paymentDate, PaymentMode mode, PaymentStage stage, String notes) {
    }

    public record LedgerResponse(
            Long id,
            Long totalAgreedFee,
            long totalPaid,
            long balanceDue,
            PaymentStatus paymentStatus,
            List<PaymentEntryResponse> entries) {
    }

    static ClientSummary summary(Client client) {
        if (client.getProjects().isEmpty()) {
            return new ClientSummary(
                    client.getId(),
                    client.getFullName(),
                    client.getPhoneNumber(),
                    client.getAddressLocality(),
                    client.getDefaultProjectType(),
                    client.getProjectStatus(),
                    PaymentStatus.PENDING,
                    0,
                    client.getCreatedAt(),
                    client.getUpdatedAt());
        }
        PaymentStatus status = client.getProjects().stream()
                .map(Project::getLedger)
                .filter(java.util.Objects::nonNull)
                .map(PaymentCalculator::status)
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), PaymentStatusRollup::worst));
        return new ClientSummary(
                client.getId(),
                client.getFullName(),
                client.getPhoneNumber(),
                client.getAddressLocality(),
                client.getDefaultProjectType(),
                client.getProjectStatus(),
                status,
                client.getProjects().size(),
                client.getCreatedAt(),
                client.getUpdatedAt());
    }

    static ClientDetail detail(Client client) {
        return new ClientDetail(
                client.getId(),
                client.getFullName(),
                client.getPhoneNumber(),
                client.getEmailAddress(),
                client.getAddressLocality(),
                client.getDefaultProjectType(),
                client.getPlotSize(),
                client.getApproximateBudget(),
                client.getProjectStatus(),
                client.getGeneralNotes(),
                client.getCreatedAt(),
                client.getUpdatedAt(),
                client.getProjects().stream().sorted(Comparator.comparing(Project::getCreatedAt).reversed()).map(ClientDtos::project).toList(),
                client.getMeetingNotes().stream().sorted(Comparator.comparing(MeetingNote::getNoteDate).reversed()).map(ClientDtos::note).toList());
    }

    public static ProjectResponse project(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getProjectType(),
                project.getStatus(),
                project.getPlotSize(),
                project.getApproximateBudget(),
                project.getRequirements(),
                project.getNotes(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                ledger(project.getLedger()),
                project.getSketches().stream().sorted(Comparator.comparing(ProjectSketch::getUploadedAt)).map(ClientDtos::sketch).toList());
    }

    public static SketchResponse sketch(ProjectSketch sketch) {
        return new SketchResponse(
                sketch.getId(),
                sketch.getFileName(),
                sketch.getOriginalName(),
                sketch.getCaption(),
                sketch.getFileSize(),
                sketch.getMimeType(),
                sketch.getUploadedAt());
    }

    public static MeetingNoteResponse note(MeetingNote note) {
        return new MeetingNoteResponse(note.getId(), note.getNoteDate(), note.getContent(), note.getCreatedAt(), note.getUpdatedAt());
    }

    public static LedgerResponse ledger(com.archdesk.payment.PaymentLedger ledger) {
        return new LedgerResponse(
                ledger.getId(),
                ledger.getTotalAgreedFee(),
                PaymentCalculator.totalPaid(ledger),
                PaymentCalculator.balanceDue(ledger),
                PaymentCalculator.status(ledger),
                ledger.getEntries().stream()
                        .sorted(Comparator.comparing(com.archdesk.payment.PaymentEntry::getPaymentDate))
                        .map(entry -> new PaymentEntryResponse(entry.getId(), entry.getAmount(), entry.getPaymentDate(), entry.getMode(), entry.getStage(), entry.getNotes()))
                        .toList());
    }
}
