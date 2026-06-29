package com.example.todo.mapper;

import java.util.List;

import com.example.todo.model.AuditLog;
import org.apache.ibatis.annotations.Param;

public interface AuditLogMapper {
    int insert(AuditLog auditLog);

    List<AuditLog> findForAdmin(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("userId") Long userId,
            @Param("ipAddress") String ipAddress
    );
}
