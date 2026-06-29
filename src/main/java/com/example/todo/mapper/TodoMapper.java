package com.example.todo.mapper;

import java.util.List;
import java.time.LocalDateTime;

import com.example.todo.model.Category;
import com.example.todo.model.CategoryTaskCount;
import com.example.todo.model.PriorityTaskCount;
import com.example.todo.model.Todo;
import com.example.todo.model.TodoStatus;
import org.apache.ibatis.annotations.Param;

public interface TodoMapper {
    List<Todo> findAll(@Param("userId") Long userId);

    List<Todo> findByKeywordAndSort(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("userId") Long userId,
            @Param("sortBy") String sortBy,
            @Param("sortDir") String sortDir
    );

    List<Todo> findPageByKeywordAndSort(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("userId") Long userId,
            @Param("sortBy") String sortBy,
            @Param("sortDir") String sortDir,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    long countByKeyword(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("userId") Long userId
    );

    List<Todo> findByTitle(
            @Param("keyword") String keyword,
            @Param("userId") Long userId
    );

    Todo findById(Long id);

    Todo findByIdForUser(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    List<Todo> findByIds(
            @Param("ids") List<Long> ids,
            @Param("userId") Long userId
    );

    int insert(Todo todo);

    int bulkInsert(@Param("todos") List<Todo> todos);

    int update(Todo todo);

    int updateStatus(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("status") TodoStatus status,
            @Param("completed") boolean completed
    );

    int bulkUpdateStatus(
            @Param("ids") List<Long> ids,
            @Param("userId") Long userId,
            @Param("status") TodoStatus status,
            @Param("completed") boolean completed
    );

    int updateDisplayOrder(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("displayOrder") int displayOrder
    );

    Integer findMaxDisplayOrder(@Param("userId") Long userId);

    int deleteById(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    int deleteByIds(
            @Param("ids") List<Long> ids,
            @Param("userId") Long userId
    );

    int deleteByUserId(@Param("userId") Long userId);

    List<Category> findAllCategories();

    Category findCategoryById(@Param("id") Long id);

    List<CategoryTaskCount> countByCategory(@Param("userId") Long userId);

    List<PriorityTaskCount> countByPriority(@Param("userId") Long userId);

    int countCreatedBetween(
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    int countCompletedCreatedBetween(
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    List<CategoryTaskCount> countCreatedByCategoryBetween(
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive
    );
}
