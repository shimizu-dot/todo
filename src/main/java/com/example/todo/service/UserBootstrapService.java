package com.example.todo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.example.todo.mapper.TodoMapper;
import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import com.example.todo.model.Priority;
import com.example.todo.model.Todo;
import com.example.todo.model.TodoStatus;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserBootstrapService implements ApplicationRunner {
    private final UserMapper userMapper;
    private final TodoMapper todoMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserSettingsFilePersistenceService userSettingsFilePersistenceService;

    public UserBootstrapService(
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

    @Override
    public void run(ApplicationArguments args) {
        userSettingsFilePersistenceService.importFromFiles();

        AppUser adminUser;
        AppUser normalUser;

        if (userMapper.countUsers() == 0) {
            adminUser = new AppUser();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode("password"));
            adminUser.setRole("ADMIN");
            adminUser.setEnabled(true);
            userMapper.insert(adminUser);

            normalUser = new AppUser();
            normalUser.setUsername("user");
            normalUser.setPassword(passwordEncoder.encode("password"));
            normalUser.setRole("USER");
            normalUser.setEnabled(true);
            userMapper.insert(normalUser);
        } else {
            adminUser = userMapper.findByUsername("admin");
            normalUser = userMapper.findByUsername("user");
        }

        long todoCount = todoMapper.countByKeyword("", null, null);
        if (todoCount == 0 && adminUser != null && normalUser != null) {
            List<Todo> samples = new ArrayList<>();
            LocalDate base = LocalDate.now();

            samples.addAll(createSampleTodosForUser(adminUser.getId(), "管理者", "admin", 12, base));
            samples.addAll(createSampleTodosForUser(normalUser.getId(), "一般ユーザー", "user", 13, base.plusDays(1)));

            todoMapper.bulkInsert(samples);
        }

        userSettingsFilePersistenceService.exportToFiles();
    }

    private List<Todo> createSampleTodosForUser(
            Long userId,
            String author,
            String prefix,
            int count,
            LocalDate startDate
    ) {
        List<Todo> todos = new ArrayList<>();
        Priority[] priorities = {Priority.HIGH, Priority.MEDIUM, Priority.LOW};
        long[] categoryIds = {1L, 2L, 3L};

        for (int i = 1; i <= count; i++) {
            Todo todo = new Todo();
            todo.setTitle(prefix + "-sample-task-" + i);
            todo.setAuthor(author);
            todo.setUserId(userId);
            todo.setPriority(priorities[(i - 1) % priorities.length]);
            todo.setCategoryId(categoryIds[(i - 1) % categoryIds.length]);
            todo.setDeadline(startDate.plusDays(i - 6L));
            todo.setDisplayOrder(i);
            todo.setCompleted(i % 4 == 0 || i % 7 == 0);
            todo.setStatus(Boolean.TRUE.equals(todo.getCompleted()) ? TodoStatus.DONE : TodoStatus.TODO);
            todos.add(todo);
        }
        return todos;
    }
}
