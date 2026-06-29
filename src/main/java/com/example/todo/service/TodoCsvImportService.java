package com.example.todo.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.todo.mapper.TodoMapper;
import com.example.todo.model.AppUser;
import com.example.todo.model.Priority;
import com.example.todo.model.Todo;
import com.example.todo.model.TodoStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TodoCsvImportService {
    private static final String UTF8_BOM = "\uFEFF";

    private final TodoMapper todoMapper;
    private final CategoryService categoryService;

    public TodoCsvImportService(TodoMapper todoMapper, CategoryService categoryService) {
        this.todoMapper = todoMapper;
        this.categoryService = categoryService;
    }

    public ImportResult importCsv(MultipartFile file, AppUser loginUser) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSVファイルを選択してください");
        }
        if (loginUser == null || loginUser.getId() == null) {
            throw new IllegalArgumentException("ログインユーザー情報が不正です");
        }

        Set<Long> validCategoryIds = categoryService.findAll().stream()
                .map(category -> category.getId())
                .collect(Collectors.toSet());

        List<RowError> errors = new ArrayList<>();
        List<Todo> validRows = new ArrayList<>();
        int totalRows = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSVファイルが空です");
            }
            headerLine = removeBom(headerLine);
            HeaderIndex header = parseHeader(headerLine);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                totalRows++;
                try {
                    List<String> columns = parseCsvLine(line);
                    List<String> rowErrors = new ArrayList<>();
                    Todo todo = toTodo(columns, header, loginUser, validCategoryIds, rowErrors);
                    if (rowErrors.isEmpty()) {
                        validRows.add(todo);
                    } else {
                        errors.add(new RowError(lineNumber, String.join(" / ", rowErrors)));
                    }
                } catch (IllegalArgumentException ex) {
                    errors.add(new RowError(lineNumber, ex.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("CSV読み込みに失敗しました", e);
        }

        int insertedCount = 0;
        if (!validRows.isEmpty()) {
            Integer maxDisplayOrder = todoMapper.findMaxDisplayOrder(loginUser.getId());
            int nextDisplayOrder = (maxDisplayOrder == null ? 0 : maxDisplayOrder) + 1;
            for (Todo todo : validRows) {
                todo.setDisplayOrder(nextDisplayOrder++);
            }
            insertedCount = todoMapper.bulkInsert(validRows);
        }

        return new ImportResult(totalRows, insertedCount, errors);
    }

    public String createTemplateCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("title,author,priority,categoryId,deadline,completed\n");
        sb.append("\"見積もり,確認\",user@example.com,MEDIUM,1,2026-12-31,false\n");
        return sb.toString();
    }

    private Todo toTodo(
            List<String> columns,
            HeaderIndex header,
            AppUser loginUser,
            Set<Long> validCategoryIds,
            List<String> errors
    ) {
        String title = getValue(columns, header.title()).trim();
        String author = getValue(columns, header.author()).trim();
        String priorityText = getValue(columns, header.priority()).trim();
        String categoryText = getValue(columns, header.categoryId()).trim();
        String deadlineText = getValue(columns, header.deadline()).trim();
        String completedText = getValue(columns, header.completed()).trim();

        if (title.isEmpty()) {
            errors.add("titleは必須です");
        } else if (title.length() > 100) {
            errors.add("titleは100文字以内で入力してください");
        }

        if (author.isEmpty()) {
            errors.add("authorは必須です");
        } else if (author.length() > 50) {
            errors.add("authorは50文字以内で入力してください");
        }

        Priority priority = parsePriority(priorityText);
        if (priority == null) {
            errors.add("priorityの形式が不正です（HIGH/MEDIUM/LOW）");
        }

        Long categoryId = parseLong(categoryText);
        if (categoryId == null || categoryId <= 0) {
            errors.add("categoryIdは正の整数で指定してください");
        } else if (!validCategoryIds.contains(categoryId)) {
            errors.add("categoryIdが存在しません: " + categoryId);
        }

        LocalDate deadline = null;
        if (!deadlineText.isEmpty()) {
            try {
                deadline = LocalDate.parse(deadlineText);
            } catch (DateTimeParseException e) {
                errors.add("deadlineの形式が不正です（yyyy-MM-dd）");
            }
        }

        Boolean completed = parseCompleted(completedText);
        if (completed == null) {
            errors.add("completedの形式が不正です（true/false/1/0/完了/未完了）");
        }

        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setAuthor(author);
        todo.setPriority(priority == null ? Priority.MEDIUM : priority);
        todo.setCategoryId(categoryId == null ? 1L : categoryId);
        todo.setDeadline(deadline);
        todo.setCompleted(Boolean.TRUE.equals(completed));
        todo.setStatus(Boolean.TRUE.equals(completed) ? TodoStatus.DONE : TodoStatus.TODO);
        todo.setUserId(loginUser.getId());
        return todo;
    }

    private HeaderIndex parseHeader(String headerLine) {
        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            index.put(normalizeHeader(headers.get(i)), i);
        }

        int title = resolveHeader(index, "title", "タイトル");
        int author = resolveHeader(index, "author", "作成者");
        int priority = resolveHeader(index, "priority", "優先度");
        int categoryId = resolveHeader(index, "categoryid", "category_id", "カテゴリid", "カテゴリID");
        int deadline = resolveHeader(index, "deadline", "期限日");
        int completed = resolveHeader(index, "completed", "完了");

        if (title < 0 || author < 0 || priority < 0 || categoryId < 0 || deadline < 0 || completed < 0) {
            throw new IllegalArgumentException(
                    "CSVヘッダーが不足しています。必要: title,author,priority,categoryId,deadline,completed");
        }

        return new HeaderIndex(title, author, priority, categoryId, deadline, completed);
    }

    private int resolveHeader(Map<String, Integer> index, String... candidates) {
        for (String candidate : candidates) {
            Integer found = index.get(normalizeHeader(candidate));
            if (found != null) {
                return found;
            }
        }
        return -1;
    }

    private String getValue(List<String> columns, int idx) {
        if (idx < 0 || idx >= columns.size()) {
            return "";
        }
        return columns.get(idx);
    }

    private Priority parsePriority(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "高" -> Priority.HIGH;
            case "MEDIUM", "中" -> Priority.MEDIUM;
            case "LOW", "低" -> Priority.LOW;
            default -> null;
        };
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseCompleted(String value) {
        if (value == null || value.isBlank()) {
            return Boolean.FALSE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "完了", "yes", "y" -> Boolean.TRUE;
            case "false", "0", "未完了", "no", "n" -> Boolean.FALSE;
            default -> null;
        };
    }

    private String normalizeHeader(String header) {
        return header == null
                ? ""
                : header.replace("\u3000", "")
                        .replace(" ", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
    }

    private String removeBom(String text) {
        if (text != null && text.startsWith(UTF8_BOM)) {
            return text.substring(1);
        }
        return text;
    }

    private List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (inQuotes) {
            throw new IllegalArgumentException("ダブルクォートが閉じられていません");
        }
        columns.add(current.toString());
        return columns;
    }

    public record ImportResult(int totalRows, int importedRows, List<RowError> errors) {
    }

    public record RowError(int lineNumber, String message) {
    }

    private record HeaderIndex(
            int title,
            int author,
            int priority,
            int categoryId,
            int deadline,
            int completed
    ) {
    }
}
