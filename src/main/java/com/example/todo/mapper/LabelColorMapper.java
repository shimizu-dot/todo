package com.example.todo.mapper;

import java.util.List;

import com.example.todo.model.LabelColor;
import org.apache.ibatis.annotations.Param;

public interface LabelColorMapper {
    List<LabelColor> findByUserId(@Param("userId") Long userId);

    int upsert(LabelColor labelColor);

    int deleteByUserIdAndKey(@Param("userId") Long userId, @Param("labelKey") String labelKey);

    int deleteByUserId(@Param("userId") Long userId);
}
