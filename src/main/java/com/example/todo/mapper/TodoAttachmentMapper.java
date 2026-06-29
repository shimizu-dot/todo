package com.example.todo.mapper;

import java.util.List;

import com.example.todo.model.TodoAttachment;
import org.apache.ibatis.annotations.Param;

public interface TodoAttachmentMapper {
    int insert(TodoAttachment attachment);

    List<TodoAttachment> findByTodoId(@Param("todoId") Long todoId);

    TodoAttachment findByIdAndTodoId(
            @Param("id") Long id,
            @Param("todoId") Long todoId
    );

    int deleteByIdAndTodoId(
            @Param("id") Long id,
            @Param("todoId") Long todoId
    );
}
