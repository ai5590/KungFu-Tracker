package com.kungfu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kungfu.model.*;
import com.kungfu.util.PathUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExerciseService {

    private final Path dataRoot;
    private final ObjectMapper mapper;

    public ExerciseService(@Value("${app.data-dir}") String dataDir) {
        this.dataRoot = Path.of(dataDir).toAbsolutePath().normalize();
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path getDataRoot() {
        return dataRoot;
    }

    public ExerciseView getExercise(String exercisePath) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        Path jsonFile = dir.resolve("exercise.json");
        if (!Files.exists(jsonFile)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }

        ExerciseMeta meta = mapper.readValue(jsonFile.toFile(), ExerciseMeta.class);

        String notes = "";
        Path notesFile = dir.resolve("notes.md");
        if (Files.exists(notesFile)) {
            notes = Files.readString(notesFile, StandardCharsets.UTF_8);
        }

        List<FileInfo> files = listMediaFiles(exercisePath, dir);

        ExerciseView view = new ExerciseView();
        view.setPath(exercisePath);
        view.setTitle(meta.getTitle());
        view.setText(meta.getText());
        view.setNotes(notes);
        view.setFiles(files);
        return view;
    }

    private List<FileInfo> listMediaFiles(String exercisePath, Path dir) throws IOException {
        Path mediaDir = dir.resolve("media");
        List<FileInfo> files = new ArrayList<>();
        if (!Files.exists(mediaDir)) return files;

        FilesData filesData = syncFilesJson(dir);
        Map<String, FileMeta> metaMap = new HashMap<>();
        for (FileMeta fm : filesData.getFiles()) {
            metaMap.put(fm.getFileName(), fm);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mediaDir)) {
            for (Path f : stream) {
                if (Files.isRegularFile(f)) {
                    String fileName = f.getFileName().toString();
                    long size = Files.size(f);
                    String contentType = detectContentType(f);
                    String encodedPath = URLEncoder.encode(exercisePath, StandardCharsets.UTF_8);
                    String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                    String url = "/api/files/stream?exercisePath=" + encodedPath + "&fileName=" + encodedName;
                    FileMeta fm = metaMap.get(fileName);
                    String description = fm != null ? fm.getDescription() : "";
                    files.add(new FileInfo(fileName, size, contentType, url, description));
                }
            }
        }
        return files;
    }

    public FilesData syncFilesJson(Path exerciseDir) throws IOException {
        Path filesJsonPath = exerciseDir.resolve("files.json");
        Path mediaDir = exerciseDir.resolve("media");

        FilesData data;
        if (Files.exists(filesJsonPath)) {
            data = mapper.readValue(filesJsonPath.toFile(), FilesData.class);
        } else {
            data = new FilesData();
        }

        Set<String> actualFiles = new HashSet<>();
        if (Files.exists(mediaDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(mediaDir)) {
                for (Path f : stream) {
                    if (Files.isRegularFile(f)) {
                        actualFiles.add(f.getFileName().toString());
                    }
                }
            }
        }

        Set<String> knownFiles = data.getFiles().stream()
                .map(FileMeta::getFileName)
                .collect(Collectors.toSet());

        boolean changed = false;

        data.getFiles().removeIf(fm -> {
            if (!actualFiles.contains(fm.getFileName())) {
                return true;
            }
            return false;
        });
        if (data.getFiles().size() != knownFiles.size()) {
            changed = true;
        }

        for (String actual : actualFiles) {
            if (!knownFiles.contains(actual)) {
                data.getFiles().add(new FileMeta(actual, ""));
                changed = true;
            }
        }

        if (changed) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(filesJsonPath.toFile(), data);
        }

        return data;
    }

    public void updateFileDescription(String exercisePath, String fileName, String description) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        PathUtil.validateFileName(fileName);

        FilesData data = syncFilesJson(dir);
        boolean found = false;
        for (FileMeta fm : data.getFiles()) {
            if (fm.getFileName().equals(fileName)) {
                fm.setDescription(description);
                fm.setUpdatedAt(Instant.now());
                found = true;
                break;
            }
        }
        if (!found) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found in metadata");
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("files.json").toFile(), data);
    }

    public void updateText(String exercisePath, String text) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        Path jsonFile = dir.resolve("exercise.json");
        if (!Files.exists(jsonFile)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        ExerciseMeta meta = mapper.readValue(jsonFile.toFile(), ExerciseMeta.class);
        meta.setText(text);
        meta.setUpdatedAt(Instant.now());
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile.toFile(), meta);
    }

    public void updateNotes(String exercisePath, String notes) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        Path notesFile = dir.resolve("notes.md");
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        Files.writeString(notesFile, notes, StandardCharsets.UTF_8);
    }

    public String createExercise(String sectionPath, String title) throws IOException {
        Path sectionDir = PathUtil.resolveAndValidate(dataRoot, sectionPath);
        if (!Files.exists(sectionDir.resolve("_section.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found");
        }
        Path exerciseDir = sectionDir.resolve(title);
        if (Files.exists(exerciseDir)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exercise already exists");
        }
        Files.createDirectories(exerciseDir.resolve("media"));
        ExerciseMeta meta = new ExerciseMeta(title, "");
        mapper.writerWithDefaultPrettyPrinter().writeValue(exerciseDir.resolve("exercise.json").toFile(), meta);
        Files.writeString(exerciseDir.resolve("notes.md"), "", StandardCharsets.UTF_8);
        return dataRoot.relativize(exerciseDir).toString().replace('\\', '/');
    }

    public String renameExercise(String exercisePath, String newTitle) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        ExerciseMeta meta = mapper.readValue(dir.resolve("exercise.json").toFile(), ExerciseMeta.class);
        meta.setTitle(newTitle);
        meta.setUpdatedAt(Instant.now());

        Path newDir = dir.getParent().resolve(newTitle);
        if (!newDir.equals(dir) && Files.exists(newDir)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A folder with this name already exists");
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("exercise.json").toFile(), meta);
        if (!newDir.equals(dir)) {
            Files.move(dir, newDir);
        }
        return dataRoot.relativize(newDir).toString().replace('\\', '/');
    }

    public void deleteExercise(String exercisePath) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
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

    public static String detectContentType(Path file) {
        try {
            String ct = Files.probeContentType(file);
            if (ct != null) return ct;
        } catch (IOException ignored) {}
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".avi")) return "video/x-msvideo";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".md")) return "text/markdown";
        if (name.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }
}
