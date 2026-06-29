package com.example.todo.aspect;

import com.example.todo.audit.Auditable;
import com.example.todo.mapper.TodoMapper;
import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import com.example.todo.service.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditAspect {
    private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogService auditLogService;
    private final TodoMapper todoMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AuditAspect(
            AuditLogService auditLogService,
            TodoMapper todoMapper,
            UserMapper userMapper,
            ObjectMapper objectMapper
    ) {
        this.auditLogService = auditLogService;
        this.todoMapper = todoMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.example.todo.audit.Auditable)")
    public Object auditAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Auditable auditable = signature.getMethod().getAnnotation(Auditable.class);
        Object[] args = joinPoint.getArgs();

        Long entityId = resolveEntityId(args, auditable.entityIdArgIndex());
        Object oldValue = resolveOldValue(args, auditable, entityId);

        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            error = ex;
            throw ex;
        } finally {
            saveAuditLog(auditable, args, result, error, entityId, oldValue);
        }
    }

    private void saveAuditLog(
            Auditable auditable,
            Object[] args,
            Object result,
            Throwable error,
            Long entityId,
            Object oldValue
    ) {
        try {
            Object newValue = resolveNewValue(args, result, auditable, entityId, error);
            AppUser currentUser = resolveCurrentUser();
            auditLogService.logAudit(
                    auditable.action(),
                    auditable.entityType(),
                    entityId,
                    currentUser == null ? null : currentUser.getId(),
                    toJson(oldValue),
                    toJson(newValue),
                    resolveIpAddress(),
                    error == null ? "SUCCESS" : "FAIL",
                    error == null ? "OK" : error.getMessage()
            );
        } catch (Exception ex) {
            logger.error("Failed to save audit log: action={}, entityType={}", auditable.action(), auditable.entityType(), ex);
        }
    }

    private Object resolveOldValue(Object[] args, Auditable auditable, Long entityId) {
        if (auditable.oldValueArgIndex() >= 0 && auditable.oldValueArgIndex() < args.length) {
            return args[auditable.oldValueArgIndex()];
        }
        if (entityId == null) {
            return null;
        }
        if ("TODO".equals(auditable.entityType())) {
            return todoMapper.findById(entityId);
        }
        if ("USER".equals(auditable.entityType())) {
            return userMapper.findById(entityId);
        }
        return null;
    }

    private Object resolveNewValue(Object[] args, Object result, Auditable auditable, Long entityId, Throwable error) {
        if (error != null) {
            return null;
        }
        if (auditable.newValueArgIndex() >= 0 && auditable.newValueArgIndex() < args.length) {
            return args[auditable.newValueArgIndex()];
        }
        if (auditable.includeReturnValue() && result != null) {
            return result;
        }
        if (auditable.entityType().equals("TODO") && entityId != null && !auditable.action().contains("DELETE")) {
            return todoMapper.findById(entityId);
        }
        if (auditable.entityType().equals("USER") && entityId != null && !auditable.action().contains("DELETE")) {
            return userMapper.findById(entityId);
        }
        if (args.length > 0 && !isPrimitiveLike(args[0])) {
            return args[0];
        }
        return null;
    }

    private Long resolveEntityId(Object[] args, int entityIdArgIndex) {
        if (entityIdArgIndex >= 0 && entityIdArgIndex < args.length) {
            return asLong(args[entityIdArgIndex]);
        }
        if (args.length > 0) {
            Object first = args[0];
            Long fromFirst = asLong(first);
            if (fromFirst != null) {
                return fromFirst;
            }
            try {
                Object idValue = first.getClass().getMethod("getId").invoke(first);
                return asLong(idValue);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"serializationError\":\"" + e.getMessage() + "\"}";
        }
    }

    private String resolveIpAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    private AppUser resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userMapper.findByUsername(authentication.getName());
    }

    private boolean isPrimitiveLike(Object value) {
        return value instanceof Number || value instanceof CharSequence || value instanceof Boolean;
    }
}
