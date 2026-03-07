package com.ticketsystem.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.auth.entity.AuditLog;
import com.ticketsystem.auth.enums.ActorType;
import com.ticketsystem.auth.enums.AuditAction;
import com.ticketsystem.auth.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void log(Long actorId, ActorType actorType, AuditAction action,
                    String ipAddress, Object detail) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setActorId(actorId);
            auditLog.setActorType(actorType);
            auditLog.setAction(action);
            auditLog.setIpAddress(ipAddress);

            if (detail != null) {
                auditLog.setDetail(objectMapper.writeValueAsString(detail));
            }

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for actor {} action {}: {}", actorId, action, e.getMessage());
        }
    }
}
