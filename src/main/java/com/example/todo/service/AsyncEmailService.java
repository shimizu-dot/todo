package com.example.todo.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.example.todo.model.Todo;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class AsyncEmailService {
    private static final Logger logger = LoggerFactory.getLogger(AsyncEmailService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@example.com}")
    private String mailFrom;

    @Value("${app.mail.default-to:}")
    private String defaultTo;

    public AsyncEmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Async("emailExecutor")
    public void sendTextMail(String to, String subject, String body) {
        if (!isSendable(to)) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        logger.info("Text mail sent: to={}, subject={}", to, subject);
    }

    @Async("emailExecutor")
    public void sendHtmlMail(String to, String subject, String templateName, Map<String, Object> variables) {
        if (!isSendable(to)) {
            return;
        }
        Context context = new Context();
        if (variables != null) {
            context.setVariables(variables);
        }
        String html = templateEngine.process(templateName, context);
        sendMimeMail(to, subject, html, null, null);
        logger.info("HTML mail sent: to={}, subject={}, template={}", to, subject, templateName);
    }

    @Async("emailExecutor")
    public void sendMultipartMail(
            String to,
            String subject,
            String templateName,
            Map<String, Object> variables,
            String attachmentFileName,
            byte[] attachmentBytes
    ) {
        if (!isSendable(to)) {
            return;
        }
        Context context = new Context();
        if (variables != null) {
            context.setVariables(variables);
        }
        String html = templateEngine.process(templateName, context);
        sendMimeMail(to, subject, html, attachmentFileName, attachmentBytes);
        logger.info("Multipart mail sent: to={}, subject={}, attachment={}", to, subject, attachmentFileName);
    }

    @Async("emailExecutor")
    public void sendTodoCreatedNotification(Todo todo) {
        if (todo == null || todo.getId() == null) {
            throw new IllegalArgumentException("通知対象のToDoが不正です");
        }
        String recipient = resolveRecipient(todo.getAuthor());
        if (!isSendable(recipient)) {
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("todoId", todo.getId());
        variables.put("title", todo.getTitle());
        variables.put("author", todo.getAuthor());
        variables.put("deadline", todo.getDeadline());
        sendHtmlMail(recipient, "ToDo作成通知", "mail/todo-created", variables);
    }

    @Async("emailExecutor")
    public void sendDeadlineReminder(String to, LocalDate baseDate, List<Todo> dueTodos) {
        if (dueTodos == null || dueTodos.isEmpty()) {
            logger.info("No due todos. Skip deadline reminder.");
            return;
        }
        if (!isSendable(to)) {
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("baseDate", baseDate);
        variables.put("todos", dueTodos);
        variables.put("count", dueTodos.size());

        String csvBody = buildCsvAttachment(dueTodos);
        sendMultipartMail(
                to,
                "ToDo期限リマインダー",
                "mail/deadline-reminder",
                variables,
                "todo-deadline-reminder.csv",
                csvBody.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void sendMimeMail(
            String to,
            String subject,
            String htmlBody,
            String attachmentFileName,
            byte[] attachmentBytes
    ) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(Objects.requireNonNull(mailFrom, "mailFrom must not be null"));
            helper.setTo(Objects.requireNonNull(to, "to must not be null"));
            helper.setSubject(Objects.requireNonNull(subject, "subject must not be null"));
            helper.setText(Objects.requireNonNull(htmlBody, "htmlBody must not be null"), true);
            if (attachmentFileName != null && attachmentBytes != null && attachmentBytes.length > 0) {
                helper.addAttachment(attachmentFileName, new ByteArrayResource(attachmentBytes));
            }
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            throw new IllegalStateException("メール送信に失敗しました", ex);
        }
    }

    private boolean isSendable(String recipient) {
        if (!mailEnabled) {
            logger.debug("Mail sending is disabled. Skip.");
            return false;
        }
        if (recipient == null || recipient.isBlank()) {
            logger.warn("Recipient is empty. Skip sending mail.");
            return false;
        }
        return true;
    }

    private String resolveRecipient(String candidate) {
        if (candidate != null && EMAIL_PATTERN.matcher(candidate).matches()) {
            return candidate;
        }
        if (defaultTo != null && !defaultTo.isBlank()) {
            return defaultTo;
        }
        return null;
    }

    private String buildCsvAttachment(List<Todo> dueTodos) {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,タイトル,期限日,作成者,完了\n");
        for (Todo todo : dueTodos) {
            csv.append(todo.getId() == null ? "" : todo.getId()).append(",");
            csv.append(escapeCsv(todo.getTitle())).append(",");
            csv.append(todo.getDeadline() == null ? "" : todo.getDeadline()).append(",");
            csv.append(escapeCsv(todo.getAuthor())).append(",");
            csv.append(Boolean.TRUE.equals(todo.getCompleted()) ? "完了" : "未完了").append("\n");
        }
        return csv.toString();
    }

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
}
