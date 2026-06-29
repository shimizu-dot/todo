package com.example.todo.controller;

import java.util.List;

import com.example.todo.model.AppUser;
import com.example.todo.service.AdminUserService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;
    private final MessageSource messageSource;

    public AdminUserController(AdminUserService adminUserService, MessageSource messageSource) {
        this.adminUserService = adminUserService;
        this.messageSource = messageSource;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public String listUsers(Model model) {
        List<AppUser> users = adminUserService.findAllUsers();
        model.addAttribute("users", users);
        return "admin/users";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public String createUser(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(name = "role", defaultValue = "USER") String role,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminUserService.createUser(username, password, role);
            redirectAttributes.addFlashAttribute("successMessage", msg("admin.user.create.success"));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("admin.user.create.failed"));
        }
        return "redirect:/admin/users";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/update")
    public String updateUser(
            @PathVariable("id") Long id,
            @RequestParam("role") String role,
            @RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
            @RequestParam(name = "newPassword", required = false) String newPassword,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser targetUser = adminUserService.findById(id);
        if (targetUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("admin.user.notFound"));
            return "redirect:/admin/users";
        }

        if (principal != null && principal.getUsername().equals(targetUser.getUsername()) && (!enabled || !"ADMIN".equals(role))) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("admin.user.self.updateDenied"));
            return "redirect:/admin/users";
        }

        try {
            adminUserService.updateUser(id, role, enabled, newPassword);
            redirectAttributes.addFlashAttribute("successMessage", msg("admin.user.update.success"));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("admin.user.update.failed"));
        }
        return "redirect:/admin/users";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/delete")
    public String deleteUser(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        AppUser targetUser = adminUserService.findById(id);
        if (targetUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("admin.user.notFound"));
            return "redirect:/admin/users";
        }
        if (principal != null && principal.getUsername().equals(targetUser.getUsername())) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("admin.user.self.deleteDenied"));
            return "redirect:/admin/users";
        }
        try {
            adminUserService.deleteUserWithTodos(id);
            redirectAttributes.addFlashAttribute("successMessage", msg("admin.user.delete.success"));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("admin.user.delete.failed"));
        }
        return "redirect:/admin/users";
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
