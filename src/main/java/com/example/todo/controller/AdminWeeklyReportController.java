package com.example.todo.controller;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import com.example.todo.model.CategoryTaskCount;
import com.example.todo.model.WeeklyReport;
import com.example.todo.service.WeeklyReportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/weekly-reports")
public class AdminWeeklyReportController {
    private final WeeklyReportService weeklyReportService;
    private final ObjectMapper objectMapper;

    public AdminWeeklyReportController(WeeklyReportService weeklyReportService, ObjectMapper objectMapper) {
        this.weeklyReportService = weeklyReportService;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public String list(Model model) {
        model.addAttribute("reports", weeklyReportService.findAll());
        return "admin/weekly-reports";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        WeeklyReport report = weeklyReportService.findById(id);
        if (report == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "対象の週次レポートが見つかりませんでした");
            return "redirect:/admin/weekly-reports";
        }
        model.addAttribute("report", report);
        model.addAttribute("categoryBreakdown", parseCategoryBreakdown(report.getCategoryBreakdown()));
        return "admin/weekly-report-detail";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/generate")
    public String generate(RedirectAttributes redirectAttributes) {
        LocalDate previousWeekStart = weeklyReportService.previousWeekStart(LocalDate.now());
        weeklyReportService.generateForWeek(previousWeekStart);
        redirectAttributes.addFlashAttribute("successMessage", "前週レポートを生成しました");
        return "redirect:/admin/weekly-reports";
    }

    private List<CategoryTaskCount> parseCategoryBreakdown(String categoryBreakdown) {
        if (categoryBreakdown == null || categoryBreakdown.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(categoryBreakdown, new TypeReference<List<CategoryTaskCount>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
