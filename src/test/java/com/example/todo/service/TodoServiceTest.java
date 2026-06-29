package com.example.todo.service;

import java.util.List;

import com.example.todo.exception.TodoNotFoundException;
import com.example.todo.mapper.TodoHistoryMapper;
import com.example.todo.mapper.TodoMapper;
import com.example.todo.model.Priority;
import com.example.todo.model.Todo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoMapper todoMapper;

    @Mock
    private TodoHistoryMapper todoHistoryMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private TodoService todoService;

    @Test
    void findAll_MapperResultIsReturned() {
        Todo todo = new Todo();
        todo.setId(1L);
        todo.setTitle("unit-test");

        when(todoMapper.findAll(10L)).thenReturn(List.of(todo));

        List<Todo> result = todoService.findAll(10L);

        assertEquals(1, result.size());
        assertEquals("unit-test", result.get(0).getTitle());
        verify(todoMapper).findAll(10L);
    }

    @Test
    void findByIdOrThrow_NotFound_ThrowsTodoNotFoundException() {
        when(todoMapper.findById(999L)).thenReturn(null);

        assertThrows(TodoNotFoundException.class, () -> todoService.findByIdOrThrow(999L));

        verify(todoMapper).findById(999L);
    }

    @Test
    void deleteById_TargetExists_DeletesAndWritesAudit() {
        Todo existing = new Todo();
        existing.setId(3L);
        existing.setUserId(1L);
        existing.setTitle("delete-target");
        existing.setAuthor("tester");
        existing.setPriority(Priority.MEDIUM);

        when(todoMapper.findByIdForUser(3L, 1L)).thenReturn(existing);
        when(todoMapper.deleteById(3L, 1L)).thenReturn(1);
        when(todoHistoryMapper.insert(any())).thenReturn(1);

        boolean deleted = todoService.deleteById(3L, 1L);

        assertTrue(deleted);
        verify(todoMapper).findByIdForUser(3L, 1L);
        verify(todoMapper).deleteById(3L, 1L);
        verify(todoHistoryMapper).insert(any());
        verify(auditLogService).log(eq("TODO_DELETE"), eq("SUCCESS"), contains("todoId=3"));
    }
}
