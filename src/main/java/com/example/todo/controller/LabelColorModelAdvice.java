package com.example.todo.controller;

import java.util.Map;

import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import com.example.todo.service.LabelColorService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class LabelColorModelAdvice {
    private final UserMapper userMapper;
    private final LabelColorService labelColorService;

    public LabelColorModelAdvice(UserMapper userMapper, LabelColorService labelColorService) {
        this.userMapper = userMapper;
        this.labelColorService = labelColorService;
    }

    @ModelAttribute("labelColors")
    public Map<String, String> labelColors() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return labelColorService.resolveForUser(null);
        }
        String username = authentication.getName();
        AppUser user = userMapper.findByUsername(username);
        return labelColorService.resolveForUser(user == null ? null : user.getId());
    }
}
