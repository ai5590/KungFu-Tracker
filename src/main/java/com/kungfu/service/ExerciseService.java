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

    public static final String DEFAULT_VARIANT = "_default";

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

        migrateToVariantsIfNeeded(dir);

        ExerciseMeta meta = mapper.readValue(jsonFile.toFile(), ExerciseMeta.class);

        List<String> variantNames = listVariantNames(dir);

        ExerciseView view = new ExerciseView();
        view.setPath(exercisePath);
        view.setTitle(meta.getTitle());
        view.setText(meta.getText());

        if (variantNames.size() == 1) {
            String variantName = variantNames.get(0);
            Path variantDir = dir.resolve(variantName);
            String variantPath = exercisePath + "/" + variantName;

            String notes = "";
            Path notesFile = variantDir.resolve("notes.md");
            if (Files.exists(notesFile)) {
                notes = Files.readString(notesFile, StandardCharsets.UTF_8);
            }

            List<FileInfo> files = listMediaFiles(variantPath, variantDir);

            view.setNotes(notes);
            view.setFiles(files);

            VariantData vd = new VariantData();
            vd.setName(variantName);
            vd.setTitle(meta.getTitle());
            vd.setText(meta.getText());
            vd.setNotes(notes);
            vd.setFiles(files);
            view.setVariants(List.of(vd));
        } else {
            List<VariantData> variants = new ArrayList<>();
            for (String variantName : variantNames) {
                Path variantDir = dir.resolve(variantName);
                String variantPath = exercisePath + "/" + variantName;

                ExerciseMeta variantMeta = null;
                Path variantJson = variantDir.resolve("exercise.json");
                if (Files.exists(variantJson)) {
                    variantMeta = mapper.readValue(variantJson.toFile(), ExerciseMeta.class);
                }

                String notes = "";
                Path notesFile = variantDir.resolve("notes.md");
                if (Files.exists(notesFile)) {
                    notes = Files.readString(notesFile, StandardCharsets.UTF_8);
                }

                List<FileInfo> files = listMediaFiles(variantPath, variantDir);

                VariantData vd = new VariantData();
                vd.setName(variantName);
                vd.setTitle(variantMeta != null ? variantMeta.getTitle() : variantName);
                vd.setText(variantMeta != null ? variantMeta.getText() : "");
                vd.setNotes(notes);
                vd.setFiles(files);
                variants.add(vd);
            }
            view.setVariants(variants);
            view.setNotes("");
            view.setFiles(List.of());
        }

        return view;
    }

    public void migrateToVariantsIfNeeded(Path exerciseDir) throws IOException {
        if (!Files.exists(exerciseDir.resolve("exercise.json"))) {
            return;
        }

        List<String> existingVariants = listVariantNames(exerciseDir);
        if (!existingVariants.isEmpty()) {
            return;
        }

        Path defaultDir = exerciseDir.resolve(DEFAULT_VARIANT);
        Files.createDirectories(defaultDir);

        Path notesFile = exerciseDir.resolve("notes.md");
        if (Files.exists(notesFile)) {
            Files.move(notesFile, defaultDir.resolve("notes.md"));
        } else {
            Files.writeString(defaultDir.resolve("notes.md"), "", StandardCharsets.UTF_8);
        }

        Path filesJson = exerciseDir.resolve("files.json");
        if (Files.exists(filesJson)) {
            Files.move(filesJson, defaultDir.resolve("files.json"));
        }

        Path mediaDir = exerciseDir.resolve("media");
        if (Files.exists(mediaDir) && Files.isDirectory(mediaDir)) {
            Files.move(mediaDir, defaultDir.resolve("media"));
        } else {
            Files.createDirectories(defaultDir.resolve("media"));
        }

        ExerciseMeta containerMeta = mapper.readValue(exerciseDir.resolve("exercise.json").toFile(), ExerciseMeta.class);
        ExerciseMeta variantMeta = new ExerciseMeta(containerMeta.getTitle(), containerMeta.getText());
        variantMeta.setCreatedAt(containerMeta.getCreatedAt());
        variantMeta.setUpdatedAt(containerMeta.getUpdatedAt());
        mapper.writerWithDefaultPrettyPrinter().writeValue(defaultDir.resolve("exercise.json").toFile(), variantMeta);
    }

    public List<String> listVariantNames(Path exerciseDir) throws IOException {
        List<String> variants = new ArrayList<>();
        if (!Files.exists(exerciseDir) || !Files.isDirectory(exerciseDir)) {
            return variants;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(exerciseDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String name = entry.getFileName().toString();
                    if (!name.equals("media") && !name.startsWith(".") && Files.exists(entry.resolve("exercise.json"))) {
                        variants.add(name);
                    }
                }
            }
        }
        variants.sort((a, b) -> {
            if (a.equals(DEFAULT_VARIANT)) return -1;
            if (b.equals(DEFAULT_VARIANT)) return 1;
            return a.compareToIgnoreCase(b);
        });
        return variants;
    }

    public int countVariants(Path exerciseDir) throws IOException {
        return listVariantNames(exerciseDir).size();
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

    public Path resolveToVariantDir(Path dir) throws IOException {
        List<String> variants = listVariantNames(dir);
        if (!variants.isEmpty()) {
            return dir.resolve(variants.get(0));
        }
        return dir;
    }

    public void updateFileDescription(String exercisePath, String fileName, String description) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        PathUtil.validateFileName(fileName);

        Path targetDir = resolveToVariantDir(dir);
        FilesData data = syncFilesJson(targetDir);
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
        mapper.writerWithDefaultPrettyPrinter().writeValue(targetDir.resolve("files.json").toFile(), data);
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
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        Path targetDir = resolveToVariantDir(dir);
        Path notesFile = targetDir.resolve("notes.md");
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
        Files.createDirectories(exerciseDir);
        ExerciseMeta meta = new ExerciseMeta(title, "");
        mapper.writerWithDefaultPrettyPrinter().writeValue(exerciseDir.resolve("exercise.json").toFile(), meta);

        Path defaultDir = exerciseDir.resolve(DEFAULT_VARIANT);
        Files.createDirectories(defaultDir.resolve("media"));
        ExerciseMeta variantMeta = new ExerciseMeta(title, "");
        mapper.writerWithDefaultPrettyPrinter().writeValue(defaultDir.resolve("exercise.json").toFile(), variantMeta);
        Files.writeString(defaultDir.resolve("notes.md"), "", StandardCharsets.UTF_8);

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

    public String createVariant(String exercisePath, String variantName) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        migrateToVariantsIfNeeded(dir);

        if (variantName == null || variantName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant name must not be empty");
        }
        if (variantName.contains("..") || variantName.contains("/") || variantName.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid variant name");
        }

        Path variantDir = dir.resolve(variantName);
        if (Files.exists(variantDir)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Variant already exists");
        }

        Files.createDirectories(variantDir.resolve("media"));
        ExerciseMeta variantMeta = new ExerciseMeta(variantName, "");
        mapper.writerWithDefaultPrettyPrinter().writeValue(variantDir.resolve("exercise.json").toFile(), variantMeta);
        Files.writeString(variantDir.resolve("notes.md"), "", StandardCharsets.UTF_8);

        return exercisePath + "/" + variantName;
    }

    public void deleteVariant(String exercisePath, String variantName) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        if (variantName == null || variantName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant name must not be empty");
        }

        Path variantDir = dir.resolve(variantName);
        if (!Files.exists(variantDir) || !Files.exists(variantDir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }

        List<String> variants = listVariantNames(dir);
        if (variants.size() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete the last variant");
        }

        deleteRecursive(variantDir);
    }

    public String renameVariant(String exercisePath, String oldName, String newName) throws IOException {
        Path dir = PathUtil.resolveAndValidate(dataRoot, exercisePath);
        if (!Files.exists(dir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found");
        }
        if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant names must not be empty");
        }
        if (newName.contains("..") || newName.contains("/") || newName.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid variant name");
        }

        Path oldDir = dir.resolve(oldName);
        if (!Files.exists(oldDir) || !Files.exists(oldDir.resolve("exercise.json"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }

        Path newDir = dir.resolve(newName);
        if (!newDir.equals(oldDir) && Files.exists(newDir)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A variant with this name already exists");
        }

        ExerciseMeta variantMeta = mapper.readValue(oldDir.resolve("exercise.json").toFile(), ExerciseMeta.class);
        variantMeta.setTitle(newName);
        variantMeta.setUpdatedAt(Instant.now());
        mapper.writerWithDefaultPrettyPrinter().writeValue(oldDir.resolve("exercise.json").toFile(), variantMeta);

        if (!newDir.equals(oldDir)) {
            Files.move(oldDir, newDir);
        }

        return exercisePath + "/" + newName;
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
