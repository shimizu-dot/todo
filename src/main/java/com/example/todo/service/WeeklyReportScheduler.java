package com.example.todo.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeeklyReportScheduler {
    private static final Logger logger = LoggerFactory.getLogger(WeeklyReportScheduler.class);

    private final WeeklyReportService weeklyReportService;

    public WeeklyReportScheduler(WeeklyReportService weeklyReportService) {
        this.weeklyReportService = weeklyReportService;
    }

    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Tokyo")
    public void generatePreviousWeekReport() {
        LocalDate previousWeekStart = weeklyReportService.previousWeekStart(LocalDate.now());
        weeklyReportService.generateForWeek(previousWeekStart);
        logger.info("Weekly report generated for weekStart={}", previousWeekStart);
    }
}
