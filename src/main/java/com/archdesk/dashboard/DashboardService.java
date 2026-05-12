package com.archdesk.dashboard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archdesk.client.ClientDtos.DashboardStats;
import com.archdesk.client.ClientDtos.RecentProjectItem;
import com.archdesk.payment.PaymentCalculator;
import com.archdesk.payment.PaymentLedger;
import com.archdesk.payment.PaymentStatus;
import com.archdesk.project.Project;
import com.archdesk.project.ProjectRepository;
import com.archdesk.project.ProjectStatus;

@Service
public class DashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final ProjectRepository projects;

    public DashboardService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        var allProjects = projects.findAllWithLedger();
        YearMonth currentMonth = YearMonth.now(BUSINESS_ZONE);

        long activeProjectsCount = allProjects.stream()
                .filter(project -> project.getStatus() == ProjectStatus.ACTIVE)
                .count();
        long overdueProjectsCount = allProjects.stream()
                .filter(project -> status(project.getLedger()) == PaymentStatus.OVERDUE)
                .count();
        long completedThisMonthCount = allProjects.stream()
                .filter(project -> project.getStatus() == ProjectStatus.COMPLETED)
                .filter(project -> {
                    LocalDate completionDate = project.getActualCompletion() != null
                            ? project.getActualCompletion()
                            : project.getUpdatedAt().atZone(BUSINESS_ZONE).toLocalDate();
                    return YearMonth.from(completionDate).equals(currentMonth);
                })
                .count();
        long totalPendingAmountInr = allProjects.stream()
                .map(Project::getLedger)
                .filter(java.util.Objects::nonNull)
                .mapToLong(PaymentCalculator::balanceDue)
                .sum();

        var recentProjects = allProjects.stream()
                .sorted(Comparator.comparing(Project::getUpdatedAt).reversed())
                .limit(5)
                .map(project -> new RecentProjectItem(
                        project.getClient().getId(),
                        project.getId(),
                        project.getClient().getFullName(),
                        project.getName(),
                        project.getStatus(),
                        status(project.getLedger()),
                        project.getUpdatedAt()))
                .toList();

        return new DashboardStats(
                activeProjectsCount,
                overdueProjectsCount,
                completedThisMonthCount,
                totalPendingAmountInr,
                recentProjects);
    }

    private PaymentStatus status(PaymentLedger ledger) {
        return ledger == null ? PaymentStatus.PENDING : PaymentCalculator.status(ledger);
    }
}
