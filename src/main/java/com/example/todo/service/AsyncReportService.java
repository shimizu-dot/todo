package com.example.todo.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.example.todo.model.Todo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncReportService {
    private final TodoService todoService;

    public AsyncReportService(TodoService todoService) {
        this.todoService = todoService;
    }

    @Async("taskExecutor")
    public CompletableFuture<String> generateTodoSummary(Long userId, long delayMs) {
        sleep(delayMs);
        List<Todo> todos = todoService.findAll(userId);
        long total = todos.size();
        long completed = todos.stream().filter(todo -> Boolean.TRUE.equals(todo.getCompleted())).count();
        long pending = total - completed;
        String summary = "total=" + total + ", completed=" + completed + ", pending=" + pending;
        return CompletableFuture.completedFuture(summary);
    }

    private void sleep(long delayMs) {
        long safeDelay = Math.max(delayMs, 0L);
        if (safeDelay == 0L) {
            return;
        }
        try {
            Thread.sleep(safeDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("レポート生成処理が中断されました", e);
        }
    }
}
