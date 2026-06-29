package com.example.todo.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageRoot;

    public FileStorageService(@Value("${app.file-storage.location:uploads}") String storageLocation) {
        this.storageRoot = Path.of(storageLocation).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException("ファイル保存ディレクトリの初期化に失敗しました", e);
        }
    }

    public String sanitizeFilename(String originalFilename) {
        String candidate = StringUtils.hasText(originalFilename) ? originalFilename : "file";
        String clean = StringUtils.cleanPath(candidate).replace("\\", "/");
        int lastSlash = clean.lastIndexOf('/');
        String fileNameOnly = lastSlash >= 0 ? clean.substring(lastSlash + 1) : clean;
        if (fileNameOnly.contains("..")) {
            throw new IllegalArgumentException("不正なファイル名です");
        }
        if (!StringUtils.hasText(fileNameOnly)) {
            throw new IllegalArgumentException("ファイル名が不正です");
        }
        return fileNameOnly;
    }

    public String store(MultipartFile file, String sanitizedOriginalFilename) {
        String extension = "";
        int dotIndex = sanitizedOriginalFilename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < sanitizedOriginalFilename.length() - 1) {
            extension = sanitizedOriginalFilename.substring(dotIndex);
        }

        String storedFileName = UUID.randomUUID() + extension;
        Path target = storageRoot.resolve(storedFileName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("保存先パスが不正です");
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return storedFileName;
        } catch (IOException e) {
            throw new IllegalStateException("ファイル保存に失敗しました", e);
        }
    }

    public Resource loadAsResource(String storedFileName) {
        Path target = storageRoot.resolve(storedFileName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("不正な保存ファイル名です");
        }
        Resource resource = new PathResource(target);
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalArgumentException("ファイルが見つかりません");
        }
        return resource;
    }

    public void delete(String storedFileName) {
        Path target = storageRoot.resolve(storedFileName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("不正な保存ファイル名です");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("ファイル削除に失敗しました", e);
        }
    }
}
