package com.example.todo.service;

import java.util.List;
import java.util.stream.Collectors;

import com.example.todo.audit.Auditable;
import com.example.todo.exception.TodoNotFoundException;
import com.example.todo.mapper.TodoHistoryMapper;
import com.example.todo.mapper.TodoMapper;
import com.example.todo.model.Todo;
import com.example.todo.model.TodoHistory;
import com.example.todo.model.TodoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ToDoドメインのユースケースを提供するサービスです。
 * <p>
 * 永続化は {@link TodoMapper} に委譲し、操作履歴を {@link TodoHistoryMapper}、
 * 監査情報を {@link AuditLogService} に記録します。
 * </p>
 *
 * @author Demo Team
 * @version 1.0
 * @since 1.0
 * @see TodoMapper
 */
@Service
public class TodoService {
    private final TodoMapper todoMapper;
    private final TodoHistoryMapper todoHistoryMapper;
    private final AuditLogService auditLogService;

    /**
     * サービスを初期化します。
     *
     * @param todoMapper ToDo永続化Mapper
     * @param todoHistoryMapper 履歴保存Mapper
     * @param auditLogService 監査ログサービス
     */
    public TodoService(TodoMapper todoMapper, TodoHistoryMapper todoHistoryMapper, AuditLogService auditLogService) {
        this.todoMapper = todoMapper;
        this.todoHistoryMapper = todoHistoryMapper;
        this.auditLogService = auditLogService;
    }

    /**
     * ToDoを新規登録します。
     *
     * @param todo 登録対象ToDo
     * @throws RuntimeException 登録または履歴・監査保存に失敗した場合
     */
    @Transactional(rollbackFor = Exception.class)
    @Auditable(action = "TODO_CREATE", entityType = "TODO", newValueArgIndex = 0, includeReturnValue = false)
    public void insert(Todo todo) {
        try {
            normalizeStatus(todo);
            if (todo.getDisplayOrder() == null || todo.getDisplayOrder() <= 0) {
                Integer maxDisplayOrder = todoMapper.findMaxDisplayOrder(todo.getUserId());
                int next = (maxDisplayOrder == null ? 0 : maxDisplayOrder) + 1;
                todo.setDisplayOrder(next);
            }
            todoMapper.insert(todo);
            insertHistory(todo.getId(), "CREATE", "ToDo作成: " + summarize(todo));
            auditLogService.log("TODO_CREATE", "SUCCESS", "todoId=" + todo.getId());
        } catch (Exception e) {
            saveFailureAudit("TODO_CREATE", e);
            throw e;
        }
    }

    /**
     * ToDoを一覧取得します。
     *
     * @param userId 対象ユーザーID。{@code null} の場合は全件
     * @return ToDo一覧
     */
    @Transactional(readOnly = true)
    public List<Todo> findAll(Long userId) {
        return todoMapper.findAll(userId);
    }

    /**
     * キーワード・カテゴリ・ソート条件でToDoを取得します。
     *
     * @param keyword タイトル検索キーワード
     * @param categoryId カテゴリID
     * @param userId 対象ユーザーID。{@code null} の場合は全件
     * @param sortBy ソート対象カラム
     * @param sortDir ソート方向（{@code asc}/{@code desc}）
     * @return 条件に一致するToDo一覧
     */
    @Transactional(readOnly = true)
    public List<Todo> findByKeywordAndSort(String keyword, Long categoryId, Long userId, String sortBy,
            String sortDir) {
        return todoMapper.findByKeywordAndSort(keyword, categoryId, userId, sortBy, sortDir);
    }

    /**
     * キーワード・カテゴリ・ソート条件でToDoをページング取得します。
     *
     * @param keyword タイトル検索キーワード
     * @param categoryId カテゴリID
     * @param userId 対象ユーザーID。{@code null} の場合は全件
     * @param sortBy ソート対象カラム
     * @param sortDir ソート方向（{@code asc}/{@code desc}）
     * @param pageable ページング条件
     * @return ページング結果
     */
    @Transactional(readOnly = true)
    public Page<Todo> findPageByKeywordAndSort(
            String keyword,
            Long categoryId,
            Long userId,
            String sortBy,
            String sortDir,
            Pageable pageable) {
        List<Todo> content = todoMapper.findPageByKeywordAndSort(
                keyword,
                categoryId,
                userId,
                sortBy,
                sortDir,
                pageable.getPageSize(),
                pageable.getOffset());
        long total = todoMapper.countByKeyword(keyword, categoryId, userId);
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * タイトルキーワードでToDoを検索します。
     *
     * @param keyword タイトル検索キーワード
     * @param userId 対象ユーザーID。{@code null} の場合は全件
     * @return 条件に一致したToDo一覧
     */
    @Transactional(readOnly = true)
    public List<Todo> findByTitle(String keyword, Long userId) {
        return todoMapper.findByTitle(keyword, userId);
    }

    /**
     * IDでToDoを取得します。
     *
     * @param id ToDo ID
     * @return ToDo。存在しない場合は {@code null}
     */
    @Transactional(readOnly = true)
    public Todo findById(Long id) {
        return todoMapper.findById(id);
    }

    /**
     * IDでToDoを取得し、存在しない場合は例外を送出します。
     *
     * @param id ToDo ID
     * @return 取得したToDo
     * @throws TodoNotFoundException 対象IDのToDoが存在しない場合
     */
    @Transactional(readOnly = true)
    public Todo findByIdOrThrow(Long id) {
        Todo todo = todoMapper.findById(id);
        if (todo == null) {
            throw new TodoNotFoundException(id);
        }
        return todo;
    }

    /**
     * ユーザー所有条件付きでToDoを取得します。
     *
     * @param id ToDo ID
     * @param userId ユーザーID
     * @return 取得したToDo。所有していない場合は {@code null}
     */
    @Transactional(readOnly = true)
    public Todo findByIdForUser(Long id, Long userId) {
        return todoMapper.findByIdForUser(id, userId);
    }

    /**
     * ToDoを更新します。
     *
     * @param todo 更新対象ToDo
     * @return 更新できた場合は {@code true}
     * @throws RuntimeException 更新処理または履歴・監査保存に失敗した場合
     */
    @Transactional(rollbackFor = Exception.class)
    @Auditable(action = "TODO_UPDATE", entityType = "TODO", newValueArgIndex = 0, includeReturnValue = false)
    public boolean update(Todo todo) {
        try {
            normalizeStatus(todo);
            boolean updated = todoMapper.update(todo) > 0;
            if (updated) {
                insertHistory(todo.getId(), "UPDATE", "ToDo更新: " + summarize(todo));
                auditLogService.log("TODO_UPDATE", "SUCCESS", "todoId=" + todo.getId());
            }
            return updated;
        } catch (Exception e) {
            saveFailureAudit("TODO_UPDATE", e);
            throw e;
        }
    }

    /**
     * 完了状態を反転します。
     *
     * @param id ToDo ID
     * @param userId ユーザーID。{@code null} は管理者扱い
     * @return 切り替えできた場合は {@code true}
     * @throws RuntimeException 更新処理または履歴・監査保存に失敗した場合
     */
    @Transactional(rollbackFor = Exception.class)
    @Auditable(action = "TODO_TOGGLE", entityType = "TODO", entityIdArgIndex = 0)
    public boolean toggleCompleted(Long id, Long userId) {
        try {
            Todo todo = todoMapper.findByIdForUser(id, userId);
            if (todo == null) {
                return false;
            }
            boolean current = Boolean.TRUE.equals(todo.getCompleted());
            todo.setCompleted(!current);
            todo.setStatus(todo.getCompleted() ? TodoStatus.DONE : TodoStatus.TODO);
            boolean updated = todoMapper.update(todo) > 0;
            if (updated) {
                insertHistory(id, "TOGGLE_COMPLETED", "完了状態を" + (todo.getCompleted() ? "完了" : "未完了") + "へ変更");
                auditLogService.log("TODO_TOGGLE", "SUCCESS", "todoId=" + id + ", completed=" + todo.getCompleted());
            }
            return updated;
        } catch (Exception e) {
            saveFailureAudit("TODO_TOGGLE", e);
            throw e;
        }
    }

    /**
     * ToDoを1件削除します。
     *
     * @param id ToDo ID
     * @param userId ユーザーID。{@code null} は管理者扱い
     * @return 削除できた場合は {@code true}
     * @throws RuntimeException 削除処理または履歴・監査保存に失敗した場合
     */
    @Transactional(rollbackFor = Exception.class)
    @Auditable(action = "TODO_DELETE", entityType = "TODO", entityIdArgIndex = 0)
    public boolean deleteById(Long id, Long userId) {
        try {
            Todo target = todoMapper.findByIdForUser(id, userId);
            if (target == null) {
                return false;
            }
            int deleted = todoMapper.deleteById(id, userId);
            if (deleted > 0) {
                insertHistory(id, "DELETE", "ToDo削除: " + summarize(target));
                auditLogService.log("TODO_DELETE", "SUCCESS", "todoId=" + id);
                return true;
            }
            return false;
        } catch (Exception e) {
            saveFailureAudit("TODO_DELETE", e);
            throw e;
        }
    }

    /**
     * ToDoを一括削除します。
     *
     * @param ids 削除対象ID一覧
     * @param userId ユーザーID。{@code null} は管理者扱い
     * @return 削除件数
     * @throws RuntimeException 削除処理または履歴・監査保存に失敗した場合
     */
    @Transactional(rollbackFor = Exception.class)
    @Auditable(action = "TODO_BULK_DELETE", entityType = "TODO", newValueArgIndex = 0, includeReturnValue = false)
    public int deleteByIds(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        try {
            List<Todo> targets = todoMapper.findByIds(ids, userId);
            if (targets.isEmpty()) {
                return 0;
            }
            int deletedCount = todoMapper.deleteByIds(ids, userId);
            if (deletedCount > 0) {
                for (Todo target : targets) {
                    insertHistory(target.getId(), "BULK_DELETE", "一括削除: " + summarize(target));
                }
                String deletedIds = targets.stream()
                        .map(Todo::getId)
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                auditLogService.log("TODO_BULK_DELETE", "SUCCESS", "deletedIds=" + deletedIds);
            }
            return deletedCount;
        } catch (Exception e) {
            saveFailureAudit("TODO_BULK_DELETE", e);
            throw e;
        }
    }

    /**
     * ToDoステータスを一括更新します。
     *
     * @param ids 更新対象ID一覧
     * @param userId ユーザーID。{@code null} は管理者扱い
     * @param status 更新先ステータス
     * @return 更新件数
     * @throws RuntimeException 更新処理または履歴・監査保存に失敗した場合
     */
    @Transactional(rollbackFor = Exception.class)
    @Auditable(action = "TODO_BULK_STATUS_UPDATE", entityType = "TODO", newValueArgIndex = 0, includeReturnValue = false)
    public int bulkUpdateStatus(List<Long> ids, Long userId, TodoStatus status) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        try {
            List<Todo> targets = todoMapper.findByIds(ids, userId);
            if (targets.isEmpty()) {
                return 0;
            }
            boolean completed = TodoStatus.DONE.equals(status);
            int updatedCount = todoMapper.bulkUpdateStatus(ids, userId, status, completed);
            if (updatedCount > 0) {
                for (Todo target : targets) {
                    insertHistory(target.getId(), "BULK_STATUS_UPDATE", "一括ステータス更新: " + status);
                }
                String updatedIds = targets.stream().map(Todo::getId).map(String::valueOf).collect(Collectors.joining(","));
                auditLogService.log("TODO_BULK_STATUS_UPDATE", "SUCCESS", "status=" + status + ", ids=" + updatedIds);
            }
            return updatedCount;
        } catch (Exception e) {
            saveFailureAudit("TODO_BULK_STATUS_UPDATE", e);
            throw e;
        }
    }

    /**
     * ToDoの進捗ステータスを更新します。
     *
     * @param id ToDo ID
     * @param userId ユーザーID。{@code null} は管理者扱い
     * @param status 更新先ステータス
     * @return 更新できた場合は {@code true}
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long id, Long userId, TodoStatus status) {
        Todo todo = todoMapper.findByIdForUser(id, userId);
        if (todo == null) {
            return false;
        }
        boolean completed = TodoStatus.DONE.equals(status);
        int updated = todoMapper.updateStatus(id, userId, status, completed);
        return updated > 0;
    }

    /**
     * 指定順で表示順を更新します。
     *
     * @param orderedIds 並び替え後のToDo ID一覧
     * @param userId ユーザーID。{@code null} は管理者扱い
     * @throws IllegalArgumentException アクセス不可のToDoが含まれる場合
     */
    @Transactional(rollbackFor = Exception.class)
    public void reorderDisplayOrder(List<Long> orderedIds, Long userId) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }
        List<Todo> owned = todoMapper.findByIds(orderedIds, userId);
        if (owned.size() != orderedIds.size()) {
            throw new IllegalArgumentException("更新対象にアクセスできないToDoが含まれています");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            todoMapper.updateDisplayOrder(orderedIds.get(i), userId, i + 1);
        }
    }

    /**
     * 操作履歴を保存します。
     *
     * @param todoId ToDo ID
     * @param action 操作種別
     * @param detail 履歴詳細
     * @throws IllegalStateException 履歴保存件数が1件でない場合
     */
    private void insertHistory(Long todoId, String action, String detail) {
        TodoHistory history = new TodoHistory();
        history.setTodoId(todoId);
        history.setAction(action);
        history.setDetail(detail);
        int inserted = todoHistoryMapper.insert(history);
        if (inserted != 1) {
            throw new IllegalStateException("履歴の保存に失敗しました");
        }
    }

    /**
     * 失敗時の監査ログを保存します。
     *
     * @param action アクション種別
     * @param exception 発生した例外
     */
    private void saveFailureAudit(String action, Exception exception) {
        try {
            auditLogService.log(action, "FAIL", exception.getMessage());
        } catch (Exception ignored) {
            // 監査ログ保存失敗は元の例外を優先する
        }
    }

    /**
     * 履歴用の概要文字列を生成します。
     *
     * @param todo 対象ToDo
     * @return {@code title=} と {@code author=} を含む概要文字列
     */
    private String summarize(Todo todo) {
        String title = todo.getTitle() == null ? "" : todo.getTitle();
        String author = todo.getAuthor() == null ? "" : todo.getAuthor();
        return "title=" + title + ", author=" + author;
    }

    /**
     * ステータスと完了フラグの整合を取ります。
     *
     * @param todo 対象ToDo
     */
    private void normalizeStatus(Todo todo) {
        if (todo.getStatus() == null) {
            todo.setStatus(Boolean.TRUE.equals(todo.getCompleted()) ? TodoStatus.DONE : TodoStatus.TODO);
        }
        todo.setCompleted(TodoStatus.DONE.equals(todo.getStatus()));
    }
}
