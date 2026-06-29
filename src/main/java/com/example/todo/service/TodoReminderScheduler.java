package com.example.todo.service;

import java.time.LocalDate;
import java.util.List;

import com.example.todo.model.Todo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TodoReminderScheduler {
    private static final Logger logger = LoggerFactory.getLogger(TodoReminderScheduler.class);

    private final TodoService todoService;
    private final AsyncEmailService asyncEmailService;

    @Value("${app.mail.reminder.to:}")
    private String reminderTo;

    public TodoReminderScheduler(TodoService todoService, AsyncEmailService asyncEmailService) {
        this.todoService = todoService;
        this.asyncEmailService = asyncEmailService;
    }

    @Scheduled(
            cron = "${app.mail.reminder.cron:0 0 9 * * *}",
            zone = "${app.mail.reminder.zone:Asia/Tokyo}"
    )
    public void sendDailyDeadlineReminder() {
        LocalDate today = LocalDate.now();
        List<Todo> targetTodos = todoService.findAll(null).stream()
                .filter(todo -> !Boolean.TRUE.equals(todo.getCompleted()))
                .filter(todo -> todo.getDeadline() != null)
                .filter(todo -> !todo.getDeadline().isAfter(today))
                .toList();

        if (targetTodos.isEmpty()) {
            logger.info("No deadline reminders to send at {}", today);
            return;
        }

        logger.info("Sending deadline reminder. date={}, count={}", today, targetTodos.size());
        asyncEmailService.sendDeadlineReminder(reminderTo, today, targetTodos);
    }
}
