package com.example.todo.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoHistory {
    private Long id;
    private Long todoId;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
