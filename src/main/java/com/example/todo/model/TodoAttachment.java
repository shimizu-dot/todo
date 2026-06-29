package com.example.todo.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoAttachment {
    private Long id;
    private Long todoId;
    private String originalFileName;
    private String storedFileName;
    private String contentType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
