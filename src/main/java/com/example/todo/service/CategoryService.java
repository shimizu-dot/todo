package com.example.todo.service;

import java.util.List;

import com.example.todo.mapper.TodoMapper;
import com.example.todo.model.Category;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {
    private final TodoMapper todoMapper;

    public CategoryService(TodoMapper todoMapper) {
        this.todoMapper = todoMapper;
    }

    public List<Category> findAll() {
        return todoMapper.findAllCategories();
    }

    public Category findById(Long id) {
        return todoMapper.findCategoryById(id);
    }
}
