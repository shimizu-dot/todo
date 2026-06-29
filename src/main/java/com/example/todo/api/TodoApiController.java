package com.example.todo.api;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.LocalDate;

import com.example.todo.exception.TodoNotFoundException;
import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import com.example.todo.model.Category;
import com.example.todo.model.Todo;
import com.example.todo.model.TodoStatus;
import com.example.todo.model.CategoryTaskCount;
import com.example.todo.model.PriorityTaskCount;
import com.example.todo.mapper.TodoMapper;
import com.example.todo.service.AsyncEmailService;
import com.example.todo.service.AsyncReportService;
import com.example.todo.service.CategoryService;
import com.example.todo.service.TodoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@RequestMapping(value = "/api/todo", produces = MediaType.APPLICATION_JSON_VALUE)
public class TodoApiController {
    private final TodoService todoService;
    private final AsyncEmailService asyncEmailService;
    private final AsyncReportService asyncReportService;
    private final UserMapper userMapper;
    private final CategoryService categoryService;
    private final TodoMapper todoMapper;

    public TodoApiController(
            TodoService todoService,
            AsyncEmailService asyncEmailService,
            AsyncReportService asyncReportService,
            UserMapper userMapper,
            CategoryService categoryService,
            TodoMapper todoMapper
    ) {
        this.todoService = todoService;
        this.asyncEmailService = asyncEmailService;
        this.asyncReportService = asyncReportService;
        this.userMapper = userMapper;
        this.categoryService = categoryService;
        this.todoMapper = todoMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Todo>>> list() {
        List<Todo> todos = todoService.findAll(null);
        return ResponseEntity.ok(new ApiResponse<>(true, "ToDo一覧を取得しました", todos));
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<List<CalendarEventResponse>> listCalendarEvents(
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Long userId = isAdmin(loginUser) ? null : loginUser.getId();
        LocalDate today = LocalDate.now();

        List<CalendarEventResponse> events = todoService.findAll(userId).stream()
                .filter(todo -> todo.getDeadline() != null)
                .map(todo -> {
                    String color = resolveEventColor(todo, today);
                    return new CalendarEventResponse(
                            String.valueOf(todo.getId()),
                            todo.getTitle(),
                            todo.getDeadline().toString(),
                            true,
                            "/todo/" + todo.getId() + "/edit",
                            color,
                            color
                    );
                })
                .toList();
        return ResponseEntity.ok(events);
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ProgressSummaryResponse> getProgressSummary(
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Long userId = isAdmin(loginUser) ? null : loginUser.getId();

        List<Todo> todos = todoService.findAll(userId);
        List<Category> categories = categoryService.findAll();

        int total = todos.size();
        int completed = (int) todos.stream().filter(todo -> Boolean.TRUE.equals(todo.getCompleted())).count();
        int overallRate = calculateRate(completed, total);

        List<CategoryProgressResponse> categoryProgresses = categories.stream()
                .map(category -> {
                    List<Todo> categoryTodos = todos.stream()
                            .filter(todo -> category.getId() != null && category.getId().equals(todo.getCategoryId()))
                            .toList();
                    int categoryTotal = categoryTodos.size();
                    int categoryCompleted = (int) categoryTodos.stream()
                            .filter(todo -> Boolean.TRUE.equals(todo.getCompleted()))
                            .count();
                    int categoryRate = calculateRate(categoryCompleted, categoryTotal);
                    return new CategoryProgressResponse(
                            category.getId(),
                            category.getName(),
                            categoryTotal,
                            categoryCompleted,
                            categoryRate,
                            resolveProgressColor(categoryRate)
                    );
                })
                .toList();

        ProgressSummaryResponse response = new ProgressSummaryResponse(
                total,
                completed,
                overallRate,
                resolveProgressColor(overallRate),
                categoryProgresses
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/category")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<List<CategoryTaskCount>> getCategoryStats(
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Long userId = isAdmin(loginUser) ? null : loginUser.getId();
        return ResponseEntity.ok(todoMapper.countByCategory(userId));
    }

    @GetMapping("/stats/priority")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<List<PriorityTaskCount>> getPriorityStats(
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Long userId = isAdmin(loginUser) ? null : loginUser.getId();
        return ResponseEntity.ok(todoMapper.countByPriority(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Todo>> findById(@PathVariable("id") @Positive(message = "idは1以上で指定してください") Long id) {
        Todo todo = todoService.findByIdOrThrow(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "ToDoを取得しました", todo));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Todo>> create(@Valid @RequestBody TodoApiRequest request) {
        Todo todo = new Todo();
        todo.setTitle(request.getTitle().trim());
        todo.setAuthor(request.getAuthor().trim());
        todo.setUserId(1L);
        todo.setPriority(request.getPriority());
        todo.setCategoryId(request.getCategoryId());
        todo.setDeadline(request.getDeadline());
        todo.setCompleted(Boolean.TRUE.equals(request.getCompleted()));
        todo.setStatus(Boolean.TRUE.equals(request.getCompleted()) ? TodoStatus.DONE : TodoStatus.TODO);
        todoService.insert(todo);
        Todo created = todoService.findById(todo.getId());
        asyncEmailService.sendTodoCreatedNotification(created);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "ToDoを作成しました", created));
    }

    @GetMapping("/report/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateSummaryReport(
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "timeoutMs", defaultValue = "1500") long timeoutMs,
            @RequestParam(name = "delayMs", defaultValue = "0") long delayMs
    ) {
        long safeTimeoutMs = Math.max(timeoutMs, 100L);
        CompletableFuture<String> future = asyncReportService.generateTodoSummary(userId, delayMs);
        try {
            String summary = future.get(safeTimeoutMs, TimeUnit.MILLISECONDS);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("summary", summary);
            data.put("timeoutMs", safeTimeoutMs);
            data.put("delayMs", Math.max(delayMs, 0L));
            return ResponseEntity.ok(new ApiResponse<>(true, "レポート生成が完了しました", data));
        } catch (TimeoutException e) {
            future.cancel(true);
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                    .body(new ApiResponse<>(false, "レポート生成がタイムアウトしました", null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "レポート生成待機中に割り込みが発生しました", null));
        } catch (ExecutionException e) {
            throw new IllegalStateException("レポート生成に失敗しました", e.getCause());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Todo>> update(
            @PathVariable("id") @Positive(message = "idは1以上で指定してください") Long id,
            @Valid @RequestBody TodoApiRequest request
    ) {
        Todo existing = todoService.findByIdOrThrow(id);
        Todo todo = new Todo();
        todo.setId(id);
        todo.setTitle(request.getTitle().trim());
        todo.setAuthor(request.getAuthor().trim());
        todo.setUserId(existing.getUserId());
        todo.setPriority(request.getPriority());
        todo.setCategoryId(request.getCategoryId());
        todo.setDeadline(request.getDeadline());
        todo.setCompleted(Boolean.TRUE.equals(request.getCompleted()));
        todo.setStatus(Boolean.TRUE.equals(request.getCompleted()) ? TodoStatus.DONE : TodoStatus.TODO);
        boolean updated = todoService.update(todo);
        if (!updated) {
            throw new TodoNotFoundException(id);
        }
        Todo refreshed = todoService.findByIdOrThrow(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "ToDoを更新しました", refreshed));
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApiResponse<Object>> reorder(
            @RequestBody ReorderRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "idsは必須です", null));
        }
        Long userId = isAdmin(loginUser) ? null : loginUser.getId();
        todoService.reorderDisplayOrder(request.ids(), userId);
        return ResponseEntity.ok(new ApiResponse<>(true, "並び順を更新しました", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") @Positive(message = "idは1以上で指定してください") Long id) {
        todoService.findByIdOrThrow(id);
        boolean deleted = todoService.deleteById(id, null);
        if (!deleted) {
            throw new TodoNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    private AppUser getAuthenticatedUser(UserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ログインが必要です");
        }
        AppUser user = userMapper.findByUsername(principal.getUsername());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザー情報が見つかりません");
        }
        return user;
    }

    private boolean isAdmin(AppUser user) {
        return user != null && "ADMIN".equals(user.getRole());
    }

    private String resolveEventColor(Todo todo, LocalDate today) {
        if (Boolean.TRUE.equals(todo.getCompleted())) {
            return "#198754";
        }
        if (todo.getDeadline() != null && todo.getDeadline().isBefore(today)) {
            return "#dc3545";
        }
        return "#0d6efd";
    }

    private int calculateRate(int completed, int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round((completed * 100.0) / total);
    }

    private String resolveProgressColor(int progressRate) {
        if (progressRate < 30) {
            return "#dc3545";
        }
        if (progressRate < 70) {
            return "#ffc107";
        }
        return "#198754";
    }

    private record CalendarEventResponse(
            String id,
            String title,
            String start,
            boolean allDay,
            String url,
            String backgroundColor,
            String borderColor
    ) {
    }

    private record ProgressSummaryResponse(
            int totalCount,
            int completedCount,
            int completionRate,
            String barColor,
            List<CategoryProgressResponse> categories
    ) {
    }

    private record CategoryProgressResponse(
            Long categoryId,
            String categoryName,
            int totalCount,
            int completedCount,
            int completionRate,
            String barColor
    ) {
    }

    private record ReorderRequest(
            List<Long> ids
    ) {
    }
}
