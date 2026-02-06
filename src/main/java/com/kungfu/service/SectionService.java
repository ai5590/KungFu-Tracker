package com.kungfu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kungfu.model.SectionMeta;
import com.kungfu.util.PathUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Service
public class SectionService {

    private final Path dataRoot;
    private final ObjectMapper mapper;

    public SectionService(@Value("${app.data-dir}") String dataDir) {
        this.dataRoot = Path.of(dataDir).toAbsolutePath().normalize();
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path getDataRoot() {
        return dataRoot;
    }

    public String createSection(String parentPath, String title) throws IOException {
        Path parentDir;
        if (parentPath == null || parentPath.isBlank()) {
            parentDir = dataRoot;
        } else {
            parentDir = PathUtil.resolveAndValidate(dataRoot, parentPath);
        }
        if (!parentDir.equals(dataRoot) && !Files.exists(parentDir.resolve("_section.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent section not found");
        }
        Path sectionDir = parentDir.resolve(title);
        if (Files.exists(sectionDir)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Section already exists");
        }
        Files.createDirectories(sectionDir);
        SectionMeta meta = new SectionMeta(title);
        mapper.writerWithDefaultPrettyPrinter().writeValue(sectionDir.resolve("_section.json").toFile(), meta);
        return dataRoot.relativize(sectionDir).toString().replace('\\', '/');
    }

    public String renameSection(String sectionPath, String newTitle) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, sectionPath);
        Path jsonFile = dir.resolve("_section.json");
        if (!Files.exists(jsonFile)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found");
        }
        SectionMeta meta = mapper.readValue(jsonFile.toFile(), SectionMeta.class);
        meta.setTitle(newTitle);
        meta.setUpdatedAt(Instant.now());

        Path newDir = dir.getParent().resolve(newTitle);
        if (!newDir.equals(dir) && Files.exists(newDir)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A folder with this name already exists");
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile.toFile(), meta);
        if (!newDir.equals(dir)) {
            Files.move(dir, newDir);
        }
        return dataRoot.relativize(newDir).toString().replace('\\', '/');
    }

    public void deleteSection(String sectionPath) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, sectionPath);
        if (!Files.exists(dir.resolve("_section.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found");
        }
        deleteRecursive(dir);
    }

    private void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursive(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
