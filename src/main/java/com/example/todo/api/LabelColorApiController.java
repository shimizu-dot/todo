package com.example.todo.api;

import java.util.Map;

import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import com.example.todo.service.LabelColorService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/label-colors")
@PreAuthorize("hasAnyRole('ADMIN','USER')")
public class LabelColorApiController {
    private final UserMapper userMapper;
    private final LabelColorService labelColorService;
    private final MessageSource messageSource;

    public LabelColorApiController(UserMapper userMapper, LabelColorService labelColorService, MessageSource messageSource) {
        this.userMapper = userMapper;
        this.labelColorService = labelColorService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getColors(@AuthenticationPrincipal UserDetails principal) {
        AppUser user = getUser(principal);
        return ResponseEntity.ok(labelColorService.resolveForUser(user.getId()));
    }

    @PostMapping("/{labelKey}")
    public ResponseEntity<Void> saveColor(
            @PathVariable("labelKey") String labelKey,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser user = getUser(principal);
        labelColorService.saveOne(user.getId(), labelKey, body.get("color"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{labelKey}")
    public ResponseEntity<Void> resetOne(
            @PathVariable("labelKey") String labelKey,
            @AuthenticationPrincipal UserDetails principal
    ) {
        AppUser user = getUser(principal);
        labelColorService.resetOne(user.getId(), labelKey);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> resetAll(@AuthenticationPrincipal UserDetails principal) {
        AppUser user = getUser(principal);
        labelColorService.resetAll(user.getId());
        return ResponseEntity.noContent().build();
    }

    private AppUser getUser(UserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msg("auth.required"));
        }
        AppUser user = userMapper.findByUsername(principal.getUsername());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msg("auth.userNotFound"));
        }
        return user;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
