package com.kungfu.service;

import com.kungfu.util.PathUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileService {

    private final Path dataRoot;

    public FileService(@Value("${app.data-dir}") String dataDir) {
        this.dataRoot = Path.of(dataDir).toAbsolutePath().normalize();
    }

    public Path getDataRoot() {
        return dataRoot;
    }

    public void uploadFiles(String exercisePath, MultipartFile[] files) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        Path mediaDir = dir.resolve("media");
        Files.createDirectories(mediaDir);

        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                originalName = "file";
            }
            originalName = originalName.replace('\\', '/');
            if (originalName.contains("/")) {
                originalName = originalName.substring(originalName.lastIndexOf('/') + 1);
            }

            Path target = mediaDir.resolve(originalName);
            int counter = 1;
            while (Files.exists(target)) {
                String baseName = originalName;
                String ext = "";
                int dotIdx = originalName.lastIndexOf('.');
                if (dotIdx > 0) {
                    baseName = originalName.substring(0, dotIdx);
                    ext = originalName.substring(dotIdx);
                }
                target = mediaDir.resolve(baseName + "(" + counter + ")" + ext);
                counter++;
            }

            try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
                in.transferTo(out);
            }
        }
    }

    public void deleteFile(String exercisePath, String fileName) throws IOException {
        PathUtil.validateFileName(fileName);
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        Path file = dir.resolve("media").resolve(fileName);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        Files.delete(file);
    }

    public Path getFilePath(String exercisePath, String fileName) {
        PathUtil.validateFileName(fileName);
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        Path file = dir.resolve("media").resolve(fileName);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        return file;
    }
}
