package com.example.todo.mapper;

import java.util.List;

import com.example.todo.model.WeeklyReport;
import org.apache.ibatis.annotations.Param;

public interface WeeklyReportMapper {
    int upsert(WeeklyReport report);

    List<WeeklyReport> findAll();

    WeeklyReport findById(@Param("id") Long id);
}
