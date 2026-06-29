package com.example.todo.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import com.example.todo.mapper.TodoMapper;
import com.example.todo.mapper.WeeklyReportMapper;
import com.example.todo.model.CategoryTaskCount;
import com.example.todo.model.WeeklyReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyReportService {
    private final TodoMapper todoMapper;
    private final WeeklyReportMapper weeklyReportMapper;
    private final ObjectMapper objectMapper;

    public WeeklyReportService(
            TodoMapper todoMapper,
            WeeklyReportMapper weeklyReportMapper,
            ObjectMapper objectMapper
    ) {
        this.todoMapper = todoMapper;
        this.weeklyReportMapper = weeklyReportMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public WeeklyReport generateForWeek(LocalDate weekStart) {
        LocalDate normalizedWeekStart = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = normalizedWeekStart.plusDays(6);
        LocalDateTime start = normalizedWeekStart.atStartOfDay();
        LocalDateTime endExclusive = weekEnd.plusDays(1).atStartOfDay();

        int createdCount = todoMapper.countCreatedBetween(start, endExclusive);
        int completedCount = todoMapper.countCompletedCreatedBetween(start, endExclusive);
        int completionRate = createdCount == 0 ? 0 : (int) Math.round((completedCount * 100.0) / createdCount);
        List<CategoryTaskCount> categoryBreakdown = todoMapper.countCreatedByCategoryBetween(start, endExclusive);

        WeeklyReport report = new WeeklyReport();
        report.setWeekStart(normalizedWeekStart);
        report.setWeekEnd(weekEnd);
        report.setCreatedCount(createdCount);
        report.setCompletedCount(completedCount);
        report.setCompletionRate(completionRate);
        report.setCategoryBreakdown(serializeCategoryBreakdown(categoryBreakdown));
        report.setGeneratedAt(LocalDateTime.of(LocalDate.now(), LocalTime.now()));

        weeklyReportMapper.upsert(report);
        List<WeeklyReport> reports = weeklyReportMapper.findAll();
        return reports.stream()
                .filter(r -> normalizedWeekStart.equals(r.getWeekStart()) && weekEnd.equals(r.getWeekEnd()))
                .findFirst()
                .orElse(report);
    }

    @Transactional(readOnly = true)
    public List<WeeklyReport> findAll() {
        return weeklyReportMapper.findAll();
    }

    @Transactional(readOnly = true)
    public WeeklyReport findById(Long id) {
        return weeklyReportMapper.findById(id);
    }

    public LocalDate previousWeekStart(LocalDate today) {
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return currentWeekStart.minusWeeks(1);
    }

    private String serializeCategoryBreakdown(List<CategoryTaskCount> breakdown) {
        try {
            return objectMapper.writeValueAsString(breakdown);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("カテゴリ内訳のJSON変換に失敗しました", e);
        }
    }
}
