package com.example.todo.controller;

import java.util.List;

import com.example.todo.model.AppUser;
import com.example.todo.model.AuditLog;
import com.example.todo.service.AdminUserService;
import com.example.todo.service.AuditLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/audit-logs")
public class AdminAuditLogController {
    private final AuditLogService auditLogService;
    private final AdminUserService adminUserService;

    public AdminAuditLogController(AuditLogService auditLogService, AdminUserService adminUserService) {
        this.auditLogService = auditLogService;
        this.adminUserService = adminUserService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public String list(
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "ipAddress", required = false) String ipAddress,
            Model model
    ) {
        String normalizedAction = normalize(action);
        String normalizedEntityType = normalize(entityType);
        String normalizedIpAddress = normalize(ipAddress);
        List<AuditLog> logs = auditLogService.findForAdmin(normalizedAction, normalizedEntityType, userId, normalizedIpAddress);
        List<AppUser> users = adminUserService.findAllUsers();

        model.addAttribute("logs", logs);
        model.addAttribute("users", users);
        model.addAttribute("action", normalizedAction);
        model.addAttribute("entityType", normalizedEntityType);
        model.addAttribute("userId", userId);
        model.addAttribute("ipAddress", normalizedIpAddress);
        return "admin/audit-logs";
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
