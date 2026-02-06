package com.kungfu.controller;

import com.kungfu.service.SectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/sections")
public class SectionController {

    private final SectionService sectionService;

    public SectionController(SectionService sectionService) {
        this.sectionService = sectionService;
    }

    @PostMapping
    public ResponseEntity<?> createSection(@RequestBody Map<String, String> body) throws IOException {
        String parentPath = body.get("parentPath");
        String title = body.get("title");
        String created = sectionService.createSection(parentPath, title);
        return ResponseEntity.ok(Map.of("path", created));
    }

    @PutMapping("/rename")
    public ResponseEntity<?> renameSection(@RequestBody Map<String, String> body) throws IOException {
        String newPath = sectionService.renameSection(body.get("path"), body.get("newTitle"));
        return ResponseEntity.ok(Map.of("path", newPath));
    }

    @DeleteMapping
    public ResponseEntity<?> deleteSection(@RequestParam String path) throws IOException {
        sectionService.deleteSection(path);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
