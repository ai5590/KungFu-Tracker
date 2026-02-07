package com.kungfu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kungfu.model.ExerciseMeta;
import com.kungfu.model.SectionMeta;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DataInitService {

    @Value("${app.data-dir}")
    private String dataDir;

    private final UserService userService;

    public DataInitService(UserService userService) {
        this.userService = userService;
    }

    @PostConstruct
    public void init() throws IOException {
        userService.initAndMigrate();

        Path dataRoot = Path.of(dataDir);
        Files.createDirectories(dataRoot);

        boolean isEmpty = true;
        try (var stream = Files.list(dataRoot)) {
            isEmpty = stream.findAny().isEmpty();
        }

        if (isEmpty) {
            createDemoData(dataRoot);
        }
    }

    private void createDemoData(Path dataRoot) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Path kungfu = dataRoot.resolve("KungFu");
        Files.createDirectories(kungfu);
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                kungfu.resolve("_section.json").toFile(), new SectionMeta("KungFu"));

        Path basics = kungfu.resolve("Basics");
        Files.createDirectories(basics);
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                basics.resolve("_section.json").toFile(), new SectionMeta("Basics"));

        Path horse = basics.resolve("HorseStance");
        Files.createDirectories(horse.resolve("media"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                horse.resolve("exercise.json").toFile(), new ExerciseMeta("HorseStance", "Demo text"));
        Files.writeString(horse.resolve("notes.md"), "Demo notes", StandardCharsets.UTF_8);
    }
}
