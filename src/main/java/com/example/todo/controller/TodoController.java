package com.example.todo.controller;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.example.todo.form.TodoForm;
import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import com.example.todo.model.Category;
import com.example.todo.model.Priority;
import com.example.todo.model.Todo;
import com.example.todo.model.TodoAttachment;
import com.example.todo.model.TodoStatus;
import com.example.todo.service.AsyncEmailService;
import com.example.todo.service.CategoryService;
import com.example.todo.service.TodoAttachmentService;
import com.example.todo.service.TodoCsvImportService;
import com.example.todo.service.TodoService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * ToDo画面向けのMVCコントローラです。
 * <p>
 * {@code /todo} 配下の画面遷移、CSV出力、添付ファイル操作を扱います。
 * 処理本体は {@link TodoService} などのサービス層へ委譲します。
 * </p>
 *
 * @author Demo Team
 * @version 1.0
 * @since 1.0
 * @see TodoService
 */
@Controller
@RequestMapping("/todo")
public class TodoController {
    private final TodoService todoService;
    private final CategoryService categoryService;
    private final TodoAttachmentService todoAttachmentService;
    private final TodoCsvImportService todoCsvImportService;
    private final AsyncEmailService asyncEmailService;
    private final UserMapper userMapper;
    private final MessageSource messageSource;

    /**
     * コントローラを初期化します。
     *
     * @param todoService ToDoサービス
     * @param categoryService カテゴリサービス
     * @param todoAttachmentService 添付ファイルサービス
     * @param todoCsvImportService CSVインポートサービス
     * @param asyncEmailService 非同期メールサービス
     * @param userMapper ユーザーMapper
     * @param messageSource メッセージソース
     */
    public TodoController(
            TodoService todoService,
            CategoryService categoryService,
            TodoAttachmentService todoAttachmentService,
            TodoCsvImportService todoCsvImportService,
            AsyncEmailService asyncEmailService,
            UserMapper userMapper,
            MessageSource messageSource
    ) {
        this.todoService = todoService;
        this.categoryService = categoryService;
        this.todoAttachmentService = todoAttachmentService;
        this.todoCsvImportService = todoCsvImportService;
        this.asyncEmailService = asyncEmailService;
        this.userMapper = userMapper;
        this.messageSource = messageSource;
    }

    /**
     * ToDo一覧画面を表示します。
     *
     * @param keyword キーワード検索条件
     * @param categoryId カテゴリ条件
     * @param sortBy ソート項目
     * @param sortDir ソート方向（{@code asc}/{@code desc}）
     * @param page ページ番号（0始まり）
     * @param principal ログインユーザー
     * @param model 画面モデル
     * @return テンプレート名（{@code todo/list}）
     * @throws ResponseStatusException 認証ユーザー解決に失敗した場合
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public String list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "sortBy", required = false, defaultValue = "displayOrder") String sortBy,
            @RequestParam(name = "sortDir", required = false, defaultValue = "desc") String sortDir,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @AuthenticationPrincipal UserDetails principal,
            Model model
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Long normalizedCategoryId = normalizeCategoryId(categoryId);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        int normalizedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(normalizedPage, 10);

        Page<Todo> todoPage = todoService.findPageByKeywordAndSort(
                normalizedKeyword,
                normalizedCategoryId,
                isAdmin(loginUser) ? null : loginUser.getId(),
                normalizedSortBy,
                normalizedSortDir,
                pageable
        );
        int startRecord = todoPage.getTotalElements() == 0 ? 0 : (normalizedPage * pageable.getPageSize()) + 1;
        int endRecord = todoPage.getTotalElements() == 0 ? 0 : startRecord + todoPage.getNumberOfElements() - 1;

        model.addAttribute("todos", todoPage.getContent());
        model.addAttribute("todoPage", todoPage);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("categoryId", normalizedCategoryId);
        model.addAttribute("sortBy", normalizedSortBy);
        model.addAttribute("sortDir", normalizedSortDir);
        model.addAttribute("currentPage", normalizedPage);
        model.addAttribute("startRecord", startRecord);
        model.addAttribute("endRecord", endRecord);
        model.addAttribute("resultCount", todoPage.getNumberOfElements());
        model.addAttribute("totalCount", todoPage.getTotalElements());
        LocalDate today = LocalDate.now();
        model.addAttribute("today", today);
        model.addAttribute("nearDeadline", today.plusDays(3));
        return "todo/list";
    }

    /**
     * ToDo一覧をCSV形式でダウンロードします。
     *
     * @param principal ログインユーザー
     * @return CSVレスポンス
     * @throws ResponseStatusException 認証ユーザー解決に失敗した場合
     */
    @GetMapping("/csv")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<byte[]> downloadCsv(
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        List<Todo> todos = todoService.findAll(isAdmin(loginUser) ? null : loginUser.getId());
        DateTimeFormatter createdAtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder csv = new StringBuilder();
        csv.append(msg("todo.csv.header")).append("\n");

        for (Todo todo : todos) {
            csv.append(todo.getId() == null ? "" : todo.getId()).append(",");
            csv.append(escapeCsv(todo.getTitle())).append(",");
            csv.append(escapeCsv(todo.getAuthor())).append(",");
            csv.append(escapeCsv(Boolean.TRUE.equals(todo.getCompleted()) ? msg("todo.status.completed") : msg("todo.status.open"))).append(",");
            csv.append(escapeCsv(todo.getCreatedAt() == null ? "" : todo.getCreatedAt().format(createdAtFormat))).append("\n");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(0xEF);
        outputStream.write(0xBB);
        outputStream.write(0xBF);
        outputStream.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));

        String fileName = "todo_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream.toByteArray());
    }

    /**
     * 新規作成フォームを表示します。
     *
     * @param model 画面モデル
     * @return テンプレート名（{@code todo/form}）
     */
    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public String newForm(
            @RequestParam(name = "deadline", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
            Model model
    ) {
        if (!model.containsAttribute("todoForm")) {
            TodoForm todoForm = new TodoForm();
            if (deadline != null) {
                todoForm.setDeadline(deadline);
            }
            model.addAttribute("todoForm", todoForm);
        }
        return "todo/form";
    }

    /**
     * カレンダービュー画面を表示します。
     *
     * @return テンプレート名（{@code todo/calendar}）
     */
    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public String calendar() {
        return "todo/calendar";
    }

    /**
     * カンバン画面を表示します。
     *
     * @param principal ログインユーザー
     * @param model 画面モデル
     * @return テンプレート名（{@code todo/kanban}）
     * @throws ResponseStatusException 認証ユーザー解決に失敗した場合
     */
    @GetMapping("/kanban")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public String kanban(
            @AuthenticationPrincipal UserDetails principal,
            Model model
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        List<Todo> todos = todoService.findAll(isAdmin(loginUser) ? null : loginUser.getId());
        model.addAttribute("todoStatusValues", TodoStatus.values());
        model.addAttribute("todos", todos);
        return "todo/kanban";
    }

    /**
     * ToDoステータスを更新します。
     *
     * @param id ToDo ID
     * @param body リクエストボディ。{@code status} キーを含む
     * @param principal ログインユーザー
     * @return 更新結果JSON
     * @throws ResponseStatusException 認証ユーザー解決に失敗した場合
     */
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.isTodoOwner(#p0, authentication.name)")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        TodoStatus status;
        try {
            status = TodoStatus.valueOf(body.getOrDefault("status", "").trim());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", msg("todo.api.status.invalid")));
        }

        boolean updated = todoService.updateStatus(id, isAdmin(loginUser) ? null : loginUser.getId(), status);
        if (!updated) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", msg("todo.api.notFound")));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 編集フォームを表示します。
     *
     * @param id ToDo ID
     * @param principal ログインユーザー
     * @param model 画面モデル
     * @param redirectAttributes リダイレクトメッセージ
     * @return テンプレート名またはリダイレクトURL
     * @throws ResponseStatusException 他ユーザーToDoへアクセスした場合
     */
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.isTodoOwner(#p0, authentication.name)")
    public String editForm(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails principal,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Todo todo = todoService.findById(id);
        if (todo == null) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.error.notFound"));
            return "redirect:/todo";
        }
        ensureOwnership(todo, loginUser);
        model.addAttribute("todo", todo);
        List<TodoAttachment> attachments = todoAttachmentService.findByTodoId(id);
        model.addAttribute("attachments", attachments);
        model.addAttribute("priorities", Priority.values());
        return "todo/edit";
    }

    /**
     * 添付ファイルをアップロードします。
     *
     * @param id ToDo ID
     * @param file アップロードファイル
     * @param principal ログインユーザー
     * @param redirectAttributes リダイレクトメッセージ
     * @return リダイレクトURL
     * @throws ResponseStatusException 他ユーザーToDoへアクセスした場合
     */
    @PostMapping("/{id}/attachments")
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.isTodoOwner(#p0, authentication.name)")
    public String uploadAttachment(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Todo todo = todoService.findById(id);
        if (todo == null) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.error.notFound"));
            return "redirect:/todo";
        }
        ensureOwnership(todo, loginUser);

        try {
            todoAttachmentService.saveAttachment(id, file);
            redirectAttributes.addFlashAttribute("successMessage", msg("todo.attachment.upload.success"));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.attachment.upload.failed"));
        }
        return "redirect:/todo/" + id + "/edit";
    }

    /**
     * 添付ファイルをダウンロードします。
     *
     * @param todoId ToDo ID
     * @param attachmentId 添付ID
     * @param principal ログインユーザー
     * @return 添付ファイルレスポンス
     * @throws ResponseStatusException 対象が存在しない場合またはアクセス不可の場合
     */
    @GetMapping("/{todoId}/attachments/{attachmentId}/download")
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.isTodoOwner(#p0, authentication.name)")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("todoId") Long todoId,
            @PathVariable("attachmentId") Long attachmentId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Todo todo = todoService.findById(todoId);
        if (todo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msg("todo.error.notFound"));
        }
        ensureOwnership(todo, loginUser);

        TodoAttachmentService.AttachmentResource attachmentResource;
        try {
            attachmentResource = todoAttachmentService.loadForDownload(todoId, attachmentId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        TodoAttachment attachment = attachmentResource.attachment();
        Resource resource = attachmentResource.resource();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (attachment.getContentType() != null && !attachment.getContentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(attachment.getContentType());
            } catch (IllegalArgumentException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(attachment.getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentLength(attachment.getFileSize() == null ? -1 : attachment.getFileSize())
                .body(resource);
    }

    /**
     * 添付ファイルを削除します。
     *
     * @param todoId ToDo ID
     * @param attachmentId 添付ID
     * @param principal ログインユーザー
     * @param redirectAttributes リダイレクトメッセージ
     * @return リダイレクトURL
     * @throws ResponseStatusException 他ユーザーToDoへアクセスした場合
     */
    @PostMapping("/{todoId}/attachments/{attachmentId}/delete")
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.isTodoOwner(#p0, authentication.name)")
    public String deleteAttachment(
            @PathVariable("todoId") Long todoId,
            @PathVariable("attachmentId") Long attachmentId,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Todo todo = todoService.findById(todoId);
        if (todo == null) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.error.notFound"));
            return "redirect:/todo";
        }
        ensureOwnership(todo, loginUser);

        try {
            boolean deleted = todoAttachmentService.deleteAttachment(todoId, attachmentId);
            if (deleted) {
                redirectAttributes.addFlashAttribute("successMessage", msg("todo.attachment.delete.success"));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", msg("todo.attachment.notFound"));
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.attachment.delete.failed"));
        }
        return "redirect:/todo/" + todoId + "/edit";
    }

    /**
     * 入力内容を確認画面へ遷移します。
     *
     * @param todoForm 入力フォーム
     * @param bindingResult バリデーション結果
     * @param model 画面モデル
     * @return テンプレート名
     */
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public String confirm(
            @Valid @ModelAttribute("todoForm") TodoForm todoForm,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "todo/form";
        }

        model.addAttribute("author", todoForm.getAuthor());
        model.addAttribute("title", todoForm.getTitle());
        model.addAttribute("detail", todoForm.getDetail());
        model.addAttribute("priority", todoForm.getPriority());
        model.addAttribute("category", categoryService.findById(todoForm.getCategoryId()));
        model.addAttribute("deadline", todoForm.getDeadline());
        return "todo/confirm";
    }

    /**
     * ToDoを登録します。
     *
     * @param author 作成者
     * @param title タイトル
     * @param priority 優先度
     * @param categoryId カテゴリID
     * @param deadline 期限日
     * @param principal ログインユーザー
     * @return リダイレクトURL
     * @throws ResponseStatusException 認証ユーザー解決に失敗した場合
     */
    @PostMapping("/complete")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public String complete(
            @RequestParam("author") String author,
            @RequestParam("title") String title,
            @RequestParam("priority") Priority priority,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(name = "deadline", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Todo todo = new Todo();
        todo.setAuthor(author);
        todo.setTitle(title);
        todo.setUserId(loginUser.getId());
        todo.setPriority(priority);
        todo.setCategoryId(categoryId);
        todo.setDeadline(deadline);
        todo.setStatus(TodoStatus.TODO);
        todo.setCompleted(false);
        todoService.insert(todo);
        asyncEmailService.sendTodoCreatedNotification(todo);

        return "redirect:/todo";
    }

    /**
     * CSVファイルを取り込み、ToDoを一括登録します。
     *
     * @param csvFile 取り込み対象CSV
     * @param principal ログインユーザー
     * @param redirectAttributes リダイレクトメッセージ
     * @return リダイレクトURL
     * @throws ResponseStatusException 認証ユーザー解決に失敗した場合
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public String importCsv(
            @RequestParam("csvFile") MultipartFile csvFile,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        try {
            TodoCsvImportService.ImportResult result = todoCsvImportService.importCsv(csvFile, loginUser);
            if (result.importedRows() > 0) {
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        msg("todo.import.success", result.importedRows())
                );
            }
            if (!result.errors().isEmpty()) {
                redirectAttributes.addFlashAttribute("importErrors", result.errors());
                String summary = msg("todo.import.error.summary", result.errors().size());
                if (result.importedRows() == 0) {
                    redirectAttributes.addFlashAttribute("errorMessage", summary);
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", msg("todo.import.partial", summary));
                }
            } else if (result.importedRows() == 0) {
                redirectAttributes.addFlashAttribute("errorMessage", msg("todo.import.noData"));
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.import.failed"));
        }
        return "redirect:/todo";
    }

    /**
     * CSVインポート用テンプレートをダウンロードします。
     *
     * @return テンプレートCSVレスポンス
     */
    @GetMapping("/import/template")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        String template = todoCsvImportService.createTemplateCsv();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(0xEF);
        outputStream.write(0xBB);
        outputStream.write(0xBF);
        outputStream.writeBytes(template.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "todo_import_template.csv");
        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray());
    }

    /**
     * ToDoを削除します。
     *
     * @param id ToDo ID
     * @param principal ログインユーザー
     * @param redirectAttributes リダイレクトメッセージ
     * @return リダイレクトURL
     * @throws ResponseStatusException 他ユーザーToDoへアクセスした場合
     */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.isTodoOwner(#p0, authentication.name)")
    public String delete(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Todo existingTodo = todoService.findById(id);
        if (existingTodo == null) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.delete.targetNotFound"));
            return "redirect:/todo";
        }
        ensureOwnership(existingTodo, loginUser);
        try {
            boolean deleted = todoService.deleteById(id, isAdmin(loginUser) ? null : loginUser.getId());
            if (deleted) {
                redirectAttributes.addFlashAttribute("successMessage", msg("todo.delete.success"));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", msg("todo.delete.failed"));
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.delete.failed"));
        }
        return "redirect:/todo";
    }

    /**
     * ToDoを一括削除します。
     *
     * @param selectedIds 選択されたToDo ID一覧
     * @param principal ログインユーザー
     * @param redirectAttributes リダイレクトメッセージ
     * @return リダイレクトURL
     * @throws ResponseStatusException 認証ユーザー解決に失敗した場合
     */
    @PostMapping("/bulk-delete")
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.areTodosOwnedBy(#p0, authentication.name)")
    public String bulkDelete(
            @RequestParam(name = "selectedIds", required = false) List<Long> selectedIds,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        if (selectedIds == null || selectedIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.bulkDelete.selectRequired"));
            return "redirect:/todo";
        }

        try {
            int deletedCount = todoService.deleteByIds(selectedIds, isAdmin(loginUser) ? null : loginUser.getId());
            if (deletedCount > 0) {
                redirectAttributes.addFlashAttribute("successMessage", msg("todo.bulkDelete.success", deletedCount));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", msg("todo.bulkDelete.none"));
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.bulkDelete.failed"));
        }
        return "redirect:/todo";
    }

    /**
     * ToDoのステータスを一括更新します。
     *
     * @param selectedIds 選択されたToDo ID一覧
     * @param action 更新アクション（{@code done}/{@code open}）
     * @param principal ログインユーザー
     * @param redirectAttributes リダイレクトメッセージ
     * @return リダイレクトURL
     * @throws ResponseStatusException 認証ユーザー解決に失敗した場合
     */
    @PostMapping("/bulk-status")
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.areTodosOwnedBy(#p0, authentication.name)")
    public String bulkStatusUpdate(
            @RequestParam(name = "selectedIds", required = false) List<Long> selectedIds,
            @RequestParam("action") String action,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        if (selectedIds == null || selectedIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.bulkStatus.selectRequired"));
            return "redirect:/todo";
        }

        TodoStatus targetStatus;
        if ("done".equalsIgnoreCase(action)) {
            targetStatus = TodoStatus.DONE;
        } else if ("open".equalsIgnoreCase(action)) {
            targetStatus = TodoStatus.TODO;
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.bulkStatus.invalidAction"));
            return "redirect:/todo";
        }

        try {
            int updatedCount = todoService.bulkUpdateStatus(selectedIds, isAdmin(loginUser) ? null : loginUser.getId(), targetStatus);
            if (updatedCount > 0) {
                String statusLabel = targetStatus == TodoStatus.DONE ? msg("todo.status.completed") : msg("todo.status.open");
                redirectAttributes.addFlashAttribute("successMessage", msg("todo.bulkStatus.success", updatedCount, statusLabel));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", msg("todo.bulkStatus.none"));
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.bulkStatus.failed"));
        }
        return "redirect:/todo";
    }

    /**
     * 完了状態を切り替えます。
     *
     * @param id ToDo ID
     * @param principal ログインユーザー
     * @param redirectAttributes リダイレクトメッセージ
     * @return リダイレクトURL
     * @throws ResponseStatusException 他ユーザーToDoへアクセスした場合
     */
    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.isTodoOwner(#p0, authentication.name)")
    public String toggleCompleted(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Todo existingTodo = todoService.findById(id);
        if (existingTodo == null) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.error.notFound"));
            return "redirect:/todo";
        }
        ensureOwnership(existingTodo, loginUser);
        boolean toggled = todoService.toggleCompleted(id, isAdmin(loginUser) ? null : loginUser.getId());
        if (!toggled) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.toggle.failed"));
        }
        return "redirect:/todo";
    }

    /**
     * ToDoを更新します。
     *
     * @param id ToDo ID
     * @param title タイトル
     * @param priority 優先度
     * @param categoryId カテゴリID
     * @param deadline 期限日
     * @param principal ログインユーザー
     * @param redirectAttributes リダイレクトメッセージ
     * @return リダイレクトURL
     * @throws ResponseStatusException 他ユーザーToDoへアクセスした場合
     */
    @PostMapping("/{id}/update")
    @PreAuthorize("hasRole('ADMIN') or @todoAuthorizationService.isTodoOwner(#p0, authentication.name)")
    public String update(
            @PathVariable("id") Long id,
            @RequestParam("title") String title,
            @RequestParam("priority") Priority priority,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(name = "deadline", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser loginUser = getAuthenticatedUser(principal);
        Todo existingTodo = todoService.findById(id);
        if (existingTodo == null) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.update.targetNotFound"));
            return "redirect:/todo";
        }
        ensureOwnership(existingTodo, loginUser);

        Todo todo = new Todo();
        todo.setId(id);
        todo.setTitle(title);
        todo.setAuthor(existingTodo.getAuthor());
        todo.setUserId(existingTodo.getUserId());
        todo.setPriority(priority);
        todo.setCategoryId(categoryId);
        todo.setDeadline(deadline);
        todo.setStatus(existingTodo.getStatus());
        todo.setCompleted(existingTodo.getCompleted());

        boolean updated = todoService.update(todo);
        if (updated) {
            redirectAttributes.addFlashAttribute("successMessage", msg("todo.update.success"));
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", msg("todo.update.failed"));
        }
        return "redirect:/todo";
    }

    /**
     * ソート対象を正規化します。
     *
     * @param sortBy 受信したソートキー
     * @return 正規化後のソートキー
     */
    private String normalizeSortBy(String sortBy) {
        return switch (sortBy) {
            case "title", "completed", "createdAt", "priority", "category", "deadline", "displayOrder" -> sortBy;
            default -> "displayOrder";
        };
    }

    /**
     * カテゴリIDを正規化します。
     *
     * @param categoryId 受信したカテゴリID
     * @return 正規化後カテゴリID。未指定・不正時は {@code null}
     */
    private Long normalizeCategoryId(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return null;
        }
        return categoryId;
    }

    /**
     * ソート方向を正規化します。
     *
     * @param sortDir 受信したソート方向
     * @return {@code asc} または {@code desc}
     */
    private String normalizeSortDir(String sortDir) {
        return "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
    }

    /**
     * 画面で使用する優先度一覧を提供します。
     *
     * @return 優先度配列
     */
    @ModelAttribute("priorities")
    public Priority[] priorities() {
        return Priority.values();
    }

    /**
     * 画面で使用するカテゴリ一覧を提供します。
     *
     * @return カテゴリ一覧
     */
    @ModelAttribute("categories")
    public List<Category> categories() {
        return categoryService.findAll();
    }

    /**
     * CSV向けに文字列をエスケープします。
     *
     * @param value 元文字列
     * @return CSV仕様に沿ってエスケープした文字列
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    /**
     * 認証ユーザーのドメイン情報を取得します。
     *
     * @param principal 認証情報
     * @return アプリケーションユーザー
     * @throws ResponseStatusException 認証情報が不正な場合
     */
    private AppUser getAuthenticatedUser(UserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msg("auth.required"));
        }
        AppUser user = userMapper.findByUsername(principal.getUsername());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msg("auth.userNotFound"));
        }
        return user;
    }

    /**
     * 所有者チェックを行います。
     *
     * @param todo 対象ToDo
     * @param user 現在ユーザー
     * @throws ResponseStatusException 他ユーザーToDoへアクセスした場合
     */
    private void ensureOwnership(Todo todo, AppUser user) {
        if (isAdmin(user)) {
            return;
        }
        if (todo.getUserId() == null || !todo.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg("todo.error.forbidden"));
        }
    }

    /**
     * 管理者判定を行います。
     *
     * @param user 対象ユーザー
     * @return 管理者の場合は {@code true}
     */
    private boolean isAdmin(AppUser user) {
        return user != null && "ADMIN".equals(user.getRole());
    }

    /**
     * メッセージキーをロケール付きで解決します。
     *
     * @param key メッセージキー
     * @param args プレースホルダ引数
     * @return 解決済みメッセージ
     */
    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
