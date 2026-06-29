package com.example.todo.service;

import java.util.List;

import com.example.todo.mapper.TodoAttachmentMapper;
import com.example.todo.model.TodoAttachment;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TodoAttachmentService {

    private final TodoAttachmentMapper todoAttachmentMapper;
    private final FileStorageService fileStorageService;

    public TodoAttachmentService(TodoAttachmentMapper todoAttachmentMapper, FileStorageService fileStorageService) {
        this.todoAttachmentMapper = todoAttachmentMapper;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<TodoAttachment> findByTodoId(Long todoId) {
        return todoAttachmentMapper.findByTodoId(todoId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TodoAttachment saveAttachment(Long todoId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("アップロードするファイルを選択してください");
        }

        String originalFileName = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        String storedFileName = fileStorageService.store(file, originalFileName);

        TodoAttachment attachment = new TodoAttachment();
        attachment.setTodoId(todoId);
        attachment.setOriginalFileName(originalFileName);
        attachment.setStoredFileName(storedFileName);
        attachment.setContentType(file.getContentType());
        attachment.setFileSize(file.getSize());

        try {
            todoAttachmentMapper.insert(attachment);
            return attachment;
        } catch (RuntimeException e) {
            fileStorageService.delete(storedFileName);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public AttachmentResource loadForDownload(Long todoId, Long attachmentId) {
        TodoAttachment attachment = todoAttachmentMapper.findByIdAndTodoId(attachmentId, todoId);
        if (attachment == null) {
            throw new IllegalArgumentException("対象の添付ファイルが見つかりません");
        }
        Resource resource = fileStorageService.loadAsResource(attachment.getStoredFileName());
        return new AttachmentResource(attachment, resource);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAttachment(Long todoId, Long attachmentId) {
        TodoAttachment attachment = todoAttachmentMapper.findByIdAndTodoId(attachmentId, todoId);
        if (attachment == null) {
            return false;
        }
        int deleted = todoAttachmentMapper.deleteByIdAndTodoId(attachmentId, todoId);
        if (deleted > 0) {
            fileStorageService.delete(attachment.getStoredFileName());
            return true;
        }
        return false;
    }

    public record AttachmentResource(TodoAttachment attachment, Resource resource) {
    }
}
