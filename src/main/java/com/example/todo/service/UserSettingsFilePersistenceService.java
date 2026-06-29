package com.example.todo.service;

import com.example.todo.mapper.LabelColorMapper;
import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import com.example.todo.model.LabelColor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Service
public class UserSettingsFilePersistenceService {
    private final UserMapper userMapper;
    private final LabelColorMapper labelColorMapper;
    private final ObjectMapper objectMapper;
    private final Path usersFile;
    private final Path labelColorsFile;
    private final Path labelColorsDir;

    public UserSettingsFilePersistenceService(
            UserMapper userMapper,
            LabelColorMapper labelColorMapper,
            ObjectMapper objectMapper,
            @Value("${app.persistence.users-file:data/users.json}") String usersFilePath,
            @Value("${app.persistence.label-colors-file:data/label-colors.json}") String labelColorsFilePath,
            @Value("${app.persistence.label-colors-dir:data/label-colors}") String labelColorsDirPath
    ) {
        this.userMapper = userMapper;
        this.labelColorMapper = labelColorMapper;
        this.objectMapper = objectMapper;
        this.usersFile = Path.of(usersFilePath).toAbsolutePath().normalize();
        this.labelColorsFile = Path.of(labelColorsFilePath).toAbsolutePath().normalize();
        this.labelColorsDir = Path.of(labelColorsDirPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            if (usersFile.getParent() != null) {
                Files.createDirectories(usersFile.getParent());
            }
            if (labelColorsFile.getParent() != null) {
                Files.createDirectories(labelColorsFile.getParent());
            }
            Files.createDirectories(labelColorsDir);
        } catch (IOException e) {
            throw new IllegalStateException("永続化ファイル保存先の初期化に失敗しました", e);
        }
    }

    public void importFromFiles() {
        importUsers();
        importLabelColors();
    }

    public void exportToFiles() {
        exportUsers();
        exportLabelColors();
    }

    public void exportUsers() {
        List<AppUser> users = userMapper.findAllUsers();
        List<UserFileRecord> records = users.stream()
                .map(u -> new UserFileRecord(u.getUsername(), u.getPassword(), u.getRole(), Boolean.TRUE.equals(u.getEnabled())))
                .toList();
        writeJson(usersFile, records);
    }

    public void exportLabelColors() {
        List<AppUser> users = userMapper.findAllUsers();
        cleanupStaleLabelColorFiles(users);

        for (AppUser user : users) {
            List<LabelColor> colors = labelColorMapper.findByUserId(user.getId());
            List<UserLabelColorRecord> records = colors.stream()
                    .map(color -> new UserLabelColorRecord(color.getLabelKey(), color.getColor()))
                    .toList();
            writeJson(userLabelColorFile(user.getUsername()), records);
        }
    }

    private void importUsers() {
        if (!Files.exists(usersFile)) {
            return;
        }
        List<UserFileRecord> records = readJson(usersFile, new TypeReference<>() {});
        for (UserFileRecord record : records) {
            if (isBlank(record.username()) || isBlank(record.password()) || isBlank(record.role())) {
                continue;
            }
            AppUser existing = userMapper.findByUsername(record.username().trim());
            if (existing == null) {
                AppUser user = new AppUser();
                user.setUsername(record.username().trim());
                user.setPassword(record.password().trim());
                user.setRole(record.role().trim());
                user.setEnabled(record.enabled());
                userMapper.insert(user);
            } else {
                userMapper.updateForAdmin(
                        existing.getId(),
                        record.role().trim(),
                        record.enabled(),
                        record.password().trim()
                );
            }
        }
    }

    private void importLabelColors() {
        importLabelColorsFromPerUserFiles();
        importLabelColorsFromLegacySharedFile();
    }

    private void importLabelColorsFromPerUserFiles() {
        if (!Files.exists(labelColorsDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(labelColorsDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        String username = usernameFromFile(path.getFileName().toString());
                        if (isBlank(username)) {
                            return;
                        }
                        AppUser user = userMapper.findByUsername(username);
                        if (user == null) {
                            return;
                        }
                        List<UserLabelColorRecord> records = readJson(path, new TypeReference<>() {});
                        for (UserLabelColorRecord record : records) {
                            if (isBlank(record.labelKey()) || isBlank(record.color())) {
                                continue;
                            }
                            LabelColor labelColor = new LabelColor();
                            labelColor.setUserId(user.getId());
                            labelColor.setLabelKey(record.labelKey().trim());
                            labelColor.setColor(record.color().trim());
                            labelColorMapper.upsert(labelColor);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("ラベル色ファイルの読み込みに失敗しました: " + labelColorsDir, e);
        }
    }

    private void importLabelColorsFromLegacySharedFile() {
        if (!Files.exists(labelColorsFile)) {
            return;
        }

        List<LabelColorFileRecord> records = readJson(labelColorsFile, new TypeReference<>() {});
        for (LabelColorFileRecord record : records) {
            if (isBlank(record.username()) || isBlank(record.labelKey()) || isBlank(record.color())) {
                continue;
            }
            AppUser user = userMapper.findByUsername(record.username().trim());
            if (user == null) {
                continue;
            }
            LabelColor labelColor = new LabelColor();
            labelColor.setUserId(user.getId());
            labelColor.setLabelKey(record.labelKey().trim());
            labelColor.setColor(record.color().trim());
            labelColorMapper.upsert(labelColor);
        }
    }

    private void cleanupStaleLabelColorFiles(List<AppUser> users) {
        if (!Files.exists(labelColorsDir)) {
            return;
        }
        List<String> activeFileNames = users.stream()
                .map(user -> userLabelColorFile(user.getUsername()).getFileName().toString())
                .toList();
        try (Stream<Path> files = Files.list(labelColorsDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !activeFileNames.contains(path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("不要なラベル色ファイル削除に失敗しました: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("ラベル色ファイルの整理に失敗しました: " + labelColorsDir, e);
        }
    }

    private <T> List<T> readJson(Path path, TypeReference<List<T>> typeReference) {
        try {
            List<T> data = objectMapper.readValue(path.toFile(), typeReference);
            return data == null ? List.of() : data;
        } catch (IOException e) {
            throw new IllegalStateException("永続化ファイルの読み込みに失敗しました: " + path, e);
        }
    }

    private void writeJson(Path path, Object value) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new IllegalStateException("永続化ファイルの書き込みに失敗しました: " + path, e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Path userLabelColorFile(String username) {
        return labelColorsDir.resolve(sanitizeUsername(username) + ".json");
    }

    private String sanitizeUsername(String username) {
        String trimmed = username == null ? "" : username.trim();
        if (trimmed.isEmpty()) {
            return "unknown";
        }
        return trimmed.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String usernameFromFile(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private record UserFileRecord(String username, String password, String role, boolean enabled) {}

    private record UserLabelColorRecord(String labelKey, String color) {}

    private record LabelColorFileRecord(String username, String labelKey, String color) {}
}
