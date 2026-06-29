package com.example.todo.api;

import java.time.LocalDate;

import com.example.todo.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class TodoApiRequest {
    @NotBlank(message = "{validation.todo.title.notBlank}")
    private String title;

    @NotBlank(message = "{validation.todo.author.notBlank}")
    private String author;

    @NotNull(message = "{validation.todo.priority.notNull}")
    private Priority priority = Priority.MEDIUM;

    @NotNull(message = "{validation.todo.category.notNull}")
    private Long categoryId = 1L;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadline;

    private Boolean completed;
}
