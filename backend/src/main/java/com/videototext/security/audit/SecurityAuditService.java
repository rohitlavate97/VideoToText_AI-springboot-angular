package com.videototext.security.audit;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j(topic = "SECURITY_AUDIT")
@Service
public class SecurityAuditService {

    private static final int MAX_RECENT_AUDIT_LOGS = 500;
    private final ConcurrentLinkedDeque<SecurityAuditEvent> recentAuditLogs = new ConcurrentLinkedDeque<>();

    @Data
    @Builder
    public static class SecurityAuditEvent {
        private UUID id;
        private String eventType;
        private String principal;
        private String resourceId;
        private String clientIp;
        private String details;
        private String status;
        private Instant timestamp;
    }

    public void logSecurityEvent(String eventType, String principal, String resourceId, String clientIp, String status, String details) {
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .id(UUID.randomUUID())
                .eventType(eventType)
                .principal(principal != null ? principal : "ANONYMOUS")
                .resourceId(resourceId != null ? resourceId : "N/A")
                .clientIp(clientIp != null ? clientIp : "UNKNOWN")
                .status(status)
                .details(details)
                .timestamp(Instant.now())
                .build();

        log.info("[AUDIT] type={} | user={} | resource={} | ip={} | status={} | details={}",
                event.getEventType(),
                event.getPrincipal(),
                event.getResourceId(),
                event.getClientIp(),
                event.getStatus(),
                event.getDetails());

        recentAuditLogs.addFirst(event);
        while (recentAuditLogs.size() > MAX_RECENT_AUDIT_LOGS) {
            recentAuditLogs.pollLast();
        }
    }

    public List<SecurityAuditEvent> getRecentAuditLogs() {
        return Collections.unmodifiableList(new ArrayList<>(recentAuditLogs));
    }
}
