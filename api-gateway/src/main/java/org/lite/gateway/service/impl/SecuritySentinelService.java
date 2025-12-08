package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.entity.SecurityIncident;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.IncidentSeverity;
import org.lite.gateway.enums.IncidentStatus;
import org.lite.gateway.repository.SecurityIncidentRepository;
import org.lite.gateway.service.AuditService;
import org.lite.gateway.service.NotificationService;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecuritySentinelService {

    private final AuditService auditService;
    private final SecurityIncidentRepository incidentRepository;
    private final org.lite.gateway.repository.UserRepository userRepository; // Auto-lock
    private final NotificationService notificationService;

    // Thresholds
    private static final int THRESHOLD_MASS_EXFILTRATION = 50; // docs per minute
    private static final int THRESHOLD_BRUTE_FORCE = 10; // failures per 5 minutes

    @PostConstruct
    public void startMonitoring() {
        log.info("Starting Security Sentinel monitoring...");
        monitorMassExfiltration();
        monitorBruteForce();
    }

    /**
     * Rule 1: Mass Data Exfiltration
     * > 50 Document Reads/Downloads in 1 minute by a single user
     */
    private void monitorMassExfiltration() {
        auditService.getAuditStream()
                .filter(log -> isReadOrDownload(log))
                .window(Duration.ofMinutes(1)) // 1-minute windows
                .flatMap(flux -> flux.collectList())
                .subscribe(logsInWindow -> {
                    if (logsInWindow.isEmpty())
                        return;

                    // Group by User
                    Map<String, List<AuditLog>> logsByUser = logsInWindow.stream()
                            .filter(l -> l.getUserId() != null)
                            .collect(Collectors.groupingBy(AuditLog::getUserId));

                    logsByUser.forEach((userId, logs) -> {
                        if (logs.size() >= THRESHOLD_MASS_EXFILTRATION) {
                            createIncident(
                                    "MASS_EXFILTRATION",
                                    IncidentSeverity.CRITICAL,
                                    "Mass Data Exfiltration Detected",
                                    String.format("User %s accessed %d documents in 1 minute.",
                                            logs.get(0).getUsername(), logs.size()),
                                    userId,
                                    logs.get(0).getUsername(),
                                    logs.get(0).getTeamId(),
                                    logs).subscribe();
                        }
                    });
                }, error -> log.error("Error in Mass Exfiltration monitor", error));
    }

    /**
     * Rule 2: Brute Force Attack
     * > 10 Login Failures in 5 minutes by a single IP
     */
    private void monitorBruteForce() {
        auditService.getAuditStream()
                .filter(log -> isLoginFailure(log))
                .window(Duration.ofMinutes(5)) // 5-minute windows
                .flatMap(flux -> flux.collectList())
                .subscribe(logsInWindow -> {
                    if (logsInWindow.isEmpty())
                        return;

                    // Group by IP Address
                    Map<String, List<AuditLog>> logsByIp = logsInWindow.stream()
                            .filter(l -> l.getIpAddress() != null)
                            .collect(Collectors.groupingBy(AuditLog::getIpAddress));

                    logsByIp.forEach((ip, logs) -> {
                        if (logs.size() >= THRESHOLD_BRUTE_FORCE) {
                            createIncident(
                                    "BRUTE_FORCE",
                                    IncidentSeverity.HIGH,
                                    "Potential Brute Force Attack",
                                    String.format("IP %s failed login %d times in 5 minutes.", ip, logs.size()),
                                    null, // User might not be known or multiple users targeted
                                    "Unknown User",
                                    null,
                                    logs).subscribe();
                        }
                    });
                }, error -> log.error("Error in Brute Force monitor", error));
    }

    private boolean isReadOrDownload(AuditLog log) {
        String action = log.getAction();
        return "READ".equals(action) || "DOWNLOAD".equals(action) ||
                AuditActionType.READ.name().equals(action) || AuditActionType.EXPORT.name().equals(action);
    }

    private boolean isLoginFailure(AuditLog log) {
        return AuditEventType.LOGIN_FAILED.equals(log.getEventType()) ||
                "FAILED".equals(log.getResult());
    }

    private Mono<SecurityIncident> createIncident(
            String ruleId,
            IncidentSeverity severity,
            String ruleName,
            String description,
            String userId,
            String username,
            String teamId,
            List<AuditLog> evidence) {

        List<String> evidenceIds = evidence.stream()
                .map(AuditLog::getEventId)
                .collect(Collectors.toList());

        SecurityIncident incident = SecurityIncident.builder()
                .referenceId("INC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .severity(severity)
                .status(IncidentStatus.OPEN)
                .ruleId(ruleId)
                .ruleName(ruleName)
                .description(description)
                .affectedUserId(userId)
                .affectedUsername(username)
                .affectedTeamId(teamId)
                .detectedAt(LocalDateTime.now())
                .evidenceAuditLogIds(evidenceIds)
                .accountLocked(false)
                .notified(false)
                .build();

        log.warn("SECURITY INCIDENT DETECTED: [{}] {}", ruleName, description);

        // Auto-Lock Logic for CRITICAL incidents
        if (severity == IncidentSeverity.CRITICAL && userId != null) {
            return userRepository.findById(userId)
                    .flatMap(user -> {
                        log.error("AUTO-LOCKING ACCOUNT: {} due to CRITICAL incident", username);
                        user.setActive(false); // Lock the account
                        return userRepository.save(user)
                                .doOnSuccess(u -> incident.setAccountLocked(true));
                    })
                    .then(saveAndNotify(incident));
        }

        return saveAndNotify(incident);
    }

    private Mono<SecurityIncident> saveAndNotify(SecurityIncident incident) {
        return incidentRepository.save(incident)
                .doOnSuccess(saved -> {
                    // Stage 3: Notification
                    sendNotification(saved);
                    log.info("Incident created: {}", saved.getReferenceId());
                });
    }

    private void sendNotification(SecurityIncident incident) {
        log.info("📧 Sending email alert for Security Incident: {} [{}]",
                incident.getReferenceId(), incident.getSeverity());

        String subject = String.format("SECURITY ALERT: [%s] %s", incident.getSeverity(), incident.getRuleName());
        String message = String.format("""
                <h2>Security Incident Detected</h2>
                <p><strong>ID:</strong> %s</p>
                <p><strong>Severity:</strong> <span style="color:%s">%s</span></p>
                <p><strong>Rule:</strong> %s</p>
                <p><strong>Description:</strong> %s</p>
                <p><strong>Affected User:</strong> %s (ID: %s)</p>
                <p><strong>Team ID:</strong> %s</p>
                <p><strong>Time:</strong> %s</p>
                <hr/>
                <p><em>Please investigate immediately.</em></p>
                """,
                incident.getReferenceId(),
                incident.getSeverity() == IncidentSeverity.CRITICAL ? "red" : "orange",
                incident.getSeverity(),
                incident.getRuleName(),
                incident.getDescription(),
                incident.getAffectedUsername(),
                incident.getAffectedUserId(),
                incident.getAffectedTeamId(),
                incident.getDetectedAt());

        notificationService.sendEmailAlert(subject, message);

        // Always mark as notified since we sent an email
        incident.setNotified(true);
        incidentRepository.save(incident).subscribe();

        if (incident.getSeverity() == IncidentSeverity.CRITICAL) {
            log.info("🚨 PAGING ON-CALL ENGINEER: CRITICAL INCIDENT DETECTED");
        }
    }
}
