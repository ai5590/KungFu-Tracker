package com.kungfu.controller;

import com.kungfu.model.ExerciseView;
import com.kungfu.service.ExerciseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping
    public ExerciseView getExercise(@RequestParam String path) throws IOException {
        return exerciseService.getExercise(path);
    }

    @PutMapping("/text")
    public ResponseEntity<?> updateText(@RequestParam String path, @RequestBody Map<String, String> body) throws IOException {
        exerciseService.updateText(path, body.get("text"));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PutMapping("/notes")
    public ResponseEntity<?> updateNotes(@RequestParam String path, @RequestBody Map<String, String> body) throws IOException {
        exerciseService.updateNotes(path, body.get("notes"));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping
    public ResponseEntity<?> createExercise(@RequestBody Map<String, String> body) throws IOException {
        String sectionPath = body.get("sectionPath");
        String title = body.get("title");
        String created = exerciseService.createExercise(sectionPath, title);
        return ResponseEntity.ok(Map.of("path", created));
    }

    @PutMapping("/rename")
    public ResponseEntity<?> renameExercise(@RequestBody Map<String, String> body) throws IOException {
        String newPath = exerciseService.renameExercise(body.get("path"), body.get("newTitle"));
        return ResponseEntity.ok(Map.of("path", newPath));
    }

    @DeleteMapping
    public ResponseEntity<?> deleteExercise(@RequestParam String path) throws IOException {
        exerciseService.deleteExercise(path);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
