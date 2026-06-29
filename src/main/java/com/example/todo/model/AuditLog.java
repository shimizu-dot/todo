package com.example.todo.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    private Long id;
    private String action;
    private String entityType;
    private Long entityId;
    private Long userId;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String status;
    private String message;
    private LocalDateTime createdAt;
}
