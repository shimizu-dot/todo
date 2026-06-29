package com.example.todo.form;

import com.example.todo.model.Priority;
import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * ToDo登録・更新画面で利用するフォームクラスです。
 * <p>
 * バリデーション制約を保持し、入力値を {@link com.example.todo.model.Todo} へ変換する前段として利用します。
 * </p>
 *
 * @author Demo Team
 * @version 1.0
 * @since 1.0
 * @see com.example.todo.controller.TodoController
 */
@Data
public class TodoForm {
    @NotBlank(message = "{validation.todo.author.notBlank}")
    @Size(max = 50, message = "{validation.todo.author.size}")
    private String author;

    @NotBlank(message = "{validation.todo.title.notBlank}")
    @Size(max = 100, message = "{validation.todo.title.size}")
    private String title;

    @Size(max = 500, message = "{validation.todo.detail.size}")
    private String detail;

    @NotNull(message = "{validation.todo.priority.notNull}")
    private Priority priority = Priority.MEDIUM;

    @NotNull(message = "{validation.todo.category.notNull}")
    private Long categoryId = 1L;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadline;

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "TodoForm{"
                + "author='" + author + '\''
                + ", title='" + title + '\''
                + ", priority=" + priority
                + ", categoryId=" + categoryId
                + ", deadline=" + deadline
                + '}';
    }
}
