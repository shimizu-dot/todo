package com.example.todo.service;

import java.util.List;
import java.util.stream.Collectors;

import com.example.todo.audit.Auditable;
import com.example.todo.mapper.TodoMapper;
import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final UserMapper userMapper;
    private final TodoMapper todoMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserSettingsFilePersistenceService userSettingsFilePersistenceService;

    public AdminUserService(
            UserMapper userMapper,
            TodoMapper todoMapper,
            PasswordEncoder passwordEncoder,
            UserSettingsFilePersistenceService userSettingsFilePersistenceService
    ) {
        this.userMapper = userMapper;
        this.todoMapper = todoMapper;
        this.passwordEncoder = passwordEncoder;
        this.userSettingsFilePersistenceService = userSettingsFilePersistenceService;
    }

    public List<AppUser> findAllUsers() {
        return userMapper.findAllUsers().stream()
                .peek(user -> {
                    if (user.getEnabled() == null) {
                        user.setEnabled(Boolean.TRUE);
                    }
                })
                .collect(Collectors.toList());
    }

    public AppUser findById(Long id) {
        return userMapper.findById(id);
    }

    @Auditable(action = "USER_CREATE", entityType = "USER", newValueArgIndex = 0, includeReturnValue = false)
    public void createUser(String username, String rawPassword, String role) {
        validateRole(role);
        String normalizedUsername = normalizeRequired(username, "ユーザー名");
        String normalizedPassword = normalizeRequired(rawPassword, "パスワード");
        if (userMapper.findByUsername(normalizedUsername) != null) {
            throw new IllegalArgumentException("同じユーザー名が既に存在します");
        }
        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(normalizedPassword));
        user.setRole(role);
        user.setEnabled(true);
        userMapper.insert(user);
        userSettingsFilePersistenceService.exportUsers();
    }

    @Auditable(action = "USER_UPDATE", entityType = "USER", entityIdArgIndex = 0)
    public void updateUser(Long id, String role, boolean enabled, String newPassword) {
        validateRole(role);
        AppUser existing = userMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("対象ユーザーが見つかりません");
        }
        String encodedPassword = null;
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            encodedPassword = passwordEncoder.encode(newPassword.trim());
        }
        userMapper.updateForAdmin(id, role, enabled, encodedPassword);
        userSettingsFilePersistenceService.exportUsers();
    }

    @Transactional
    @Auditable(action = "USER_DELETE", entityType = "USER", entityIdArgIndex = 0)
    public void deleteUserWithTodos(Long userId) {
        todoMapper.deleteByUserId(userId);
        int deleted = userMapper.deleteById(userId);
        if (deleted == 0) {
            throw new IllegalArgumentException("対象ユーザーが見つかりません");
        }
        userSettingsFilePersistenceService.exportToFiles();
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "は必須です");
        }
        return normalized;
    }

    private void validateRole(String role) {
        if (!"ADMIN".equals(role) && !"USER".equals(role)) {
            throw new IllegalArgumentException("不正なロールです");
        }
    }
}
