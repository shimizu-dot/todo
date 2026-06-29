package com.example.todo.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.todo.mapper.LabelColorMapper;
import com.example.todo.model.LabelColor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelColorService {
    public static final String PRIORITY_HIGH = "priorityHigh";
    public static final String PRIORITY_MEDIUM = "priorityMedium";
    public static final String PRIORITY_LOW = "priorityLow";
    public static final String STATUS_COMPLETED = "statusCompleted";
    public static final String STATUS_OPEN = "statusOpen";

    private static final Map<String, String> DEFAULTS = Map.of(
            PRIORITY_HIGH, "#dc3545",
            PRIORITY_MEDIUM, "#ffc107",
            PRIORITY_LOW, "#198754",
            STATUS_COMPLETED, "#198754",
            STATUS_OPEN, "#6c757d"
    );

    private final LabelColorMapper labelColorMapper;
    private final UserSettingsFilePersistenceService userSettingsFilePersistenceService;

    public LabelColorService(
            LabelColorMapper labelColorMapper,
            UserSettingsFilePersistenceService userSettingsFilePersistenceService
    ) {
        this.labelColorMapper = labelColorMapper;
        this.userSettingsFilePersistenceService = userSettingsFilePersistenceService;
    }

    @Transactional(readOnly = true)
    public Map<String, String> resolveForUser(Long userId) {
        Map<String, String> resolved = new LinkedHashMap<>(DEFAULTS);
        if (userId == null) {
            return resolved;
        }
        List<LabelColor> custom = labelColorMapper.findByUserId(userId);
        for (LabelColor color : custom) {
            if (DEFAULTS.containsKey(color.getLabelKey()) && isValidHex(color.getColor())) {
                resolved.put(color.getLabelKey(), color.getColor());
            }
        }
        return resolved;
    }

    @Transactional
    public void saveOne(Long userId, String labelKey, String color) {
        if (userId == null || !DEFAULTS.containsKey(labelKey) || !isValidHex(color)) {
            throw new IllegalArgumentException("不正なラベル色設定です");
        }
        LabelColor labelColor = new LabelColor();
        labelColor.setUserId(userId);
        labelColor.setLabelKey(labelKey);
        labelColor.setColor(color);
        labelColorMapper.upsert(labelColor);
        userSettingsFilePersistenceService.exportLabelColors();
    }

    @Transactional
    public void resetOne(Long userId, String labelKey) {
        if (userId == null || !DEFAULTS.containsKey(labelKey)) {
            throw new IllegalArgumentException("不正なリセット要求です");
        }
        labelColorMapper.deleteByUserIdAndKey(userId, labelKey);
        userSettingsFilePersistenceService.exportLabelColors();
    }

    @Transactional
    public void resetAll(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("不正なリセット要求です");
        }
        labelColorMapper.deleteByUserId(userId);
        userSettingsFilePersistenceService.exportLabelColors();
    }

    public Set<String> supportedKeys() {
        return DEFAULTS.keySet();
    }

    public String defaultColor(String key) {
        return DEFAULTS.get(key);
    }

    private boolean isValidHex(String color) {
        return color != null && color.matches("^#[0-9a-fA-F]{6}$");
    }
}
