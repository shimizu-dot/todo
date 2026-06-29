package com.example.todo.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.todo.mapper.TodoMapper;
import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import com.example.todo.model.Todo;
import org.springframework.stereotype.Service;

@Service
public class TodoAuthorizationService {
    private final TodoMapper todoMapper;
    private final UserMapper userMapper;

    public TodoAuthorizationService(TodoMapper todoMapper, UserMapper userMapper) {
        this.todoMapper = todoMapper;
        this.userMapper = userMapper;
    }

    public boolean isTodoOwner(Long todoId, String username) {
        if (todoId == null || username == null || username.isBlank()) {
            return false;
        }
        AppUser user = userMapper.findByUsername(username);
        if (user == null) {
            return false;
        }
        if ("ADMIN".equals(user.getRole())) {
            return true;
        }
        Todo todo = todoMapper.findByIdForUser(todoId, user.getId());
        return todo != null;
    }

    public boolean areTodosOwnedBy(List<Long> todoIds, String username) {
        if (todoIds == null || todoIds.isEmpty()) {
            return true;
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        AppUser user = userMapper.findByUsername(username);
        if (user == null) {
            return false;
        }
        if ("ADMIN".equals(user.getRole())) {
            return true;
        }
        Set<Long> uniqueTodoIds = new HashSet<>(todoIds);
        List<Todo> ownedTodos = todoMapper.findByIds(List.copyOf(uniqueTodoIds), user.getId());
        return ownedTodos.size() == uniqueTodoIds.size();
    }
}
