package com.example.todo.service;

import java.util.List;

import com.example.todo.mapper.AuditLogMapper;
import com.example.todo.model.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String status, String message) {
        logAudit(action, "SYSTEM", null, null, null, null, null, status, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAudit(
            String action,
            String entityType,
            Long entityId,
            Long userId,
            String oldValue,
            String newValue,
            String ipAddress,
            String status,
            String message
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setUserId(userId);
        auditLog.setOldValue(oldValue);
        auditLog.setNewValue(newValue);
        auditLog.setIpAddress(ipAddress);
        auditLog.setStatus(status);
        auditLog.setMessage(message);
        auditLogMapper.insert(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findForAdmin(String action, String entityType, Long userId, String ipAddress) {
        return auditLogMapper.findForAdmin(action, entityType, userId, ipAddress);
    }
}
