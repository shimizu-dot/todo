package com.example.todo.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ToDoエンティティです。
 * <p>
 * 画面、API、サービス、永続化層で共通利用されるタスク情報を保持します。
 * {@code Lombok} によりアクセサやコンストラクタが自動生成されます。
 * </p>
 *
 * @author Demo Team
 * @version 1.0
 * @since 1.0
 * @see com.example.todo.service.TodoService
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Todo {
    private Long id;
    private String title;
    private String author;
    private Long userId;
    private Priority priority;
    private Long categoryId;
    private String categoryName;
    private String categoryColor;
    private LocalDate deadline;
    private Integer displayOrder;
    private TodoStatus status;
    private Boolean completed;
    private Boolean hasAttachment;
    private LocalDateTime createdAt;

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Todo{"
                + "id=" + id
                + ", title='" + title + '\''
                + ", author='" + author + '\''
                + ", userId=" + userId
                + ", priority=" + priority
                + ", categoryId=" + categoryId
                + ", deadline=" + deadline
                + ", status=" + status
                + ", completed=" + completed
                + '}';
    }
}
