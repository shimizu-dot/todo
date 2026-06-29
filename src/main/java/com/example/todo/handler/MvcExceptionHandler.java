package com.example.todo.handler;

import jakarta.servlet.http.HttpServletRequest;
import com.example.todo.exception.TodoNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice(annotations = Controller.class)
public class MvcExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(MvcExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNotFound(NoResourceFoundException ex, HttpServletRequest request, Model model) {
        logger.warn("404 Not Found: path={}", request.getRequestURI(), ex);
        populateModel(model, HttpStatus.NOT_FOUND, "ページが見つかりません", request.getRequestURI());
        return "error/404";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request,
            Model model
    ) {
        logger.warn("Upload too large: path={}", request.getRequestURI(), ex);
        populateModel(model, HttpStatus.PAYLOAD_TOO_LARGE, "ファイルサイズが上限（10MB）を超えています", request.getRequestURI());
        return resolveErrorView(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(TodoNotFoundException.class)
    public String handleTodoNotFound(TodoNotFoundException ex, HttpServletRequest request, Model model) {
        logger.warn("Todo not found: path={}, message={}", request.getRequestURI(), ex.getMessage());
        populateModel(model, HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
        return "error/404";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request,
            Model model
    ) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String message = ex.getReason() != null ? ex.getReason() : "エラーが発生しました";
        logger.warn("ResponseStatusException: status={}, path={}, message={}", resolved.value(), request.getRequestURI(), message, ex);
        populateModel(model, resolved, message, request.getRequestURI());
        return resolveErrorView(resolved);
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, HttpServletRequest request, Model model) {
        logger.error("Unhandled MVC exception: path={}", request.getRequestURI(), ex);
        populateModel(model, HttpStatus.INTERNAL_SERVER_ERROR, "予期しないエラーが発生しました", request.getRequestURI());
        return "error/500";
    }

    private void populateModel(Model model, HttpStatus status, String message, String path) {
        model.addAttribute("status", status.value());
        model.addAttribute("error", status.getReasonPhrase());
        model.addAttribute("message", message);
        model.addAttribute("path", path);
    }

    private String resolveErrorView(HttpStatus status) {
        if (HttpStatus.NOT_FOUND.equals(status)) {
            return "error/404";
        }
        if (HttpStatus.INTERNAL_SERVER_ERROR.equals(status)) {
            return "error/500";
        }
        return "error/common";
    }
}
