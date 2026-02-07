package com.kungfu.controller;

import com.kungfu.service.ExerciseService;
import com.kungfu.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final ExerciseService exerciseService;

    public FileController(FileService fileService, ExerciseService exerciseService) {
        this.fileService = fileService;
        this.exerciseService = exerciseService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam String exercisePath,
                                    @RequestParam("files") MultipartFile[] files) throws IOException {
        fileService.uploadFiles(exercisePath, files);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping
    public ResponseEntity<?> deleteFile(@RequestParam String exercisePath,
                                        @RequestParam String fileName) throws IOException {
        fileService.deleteFile(exercisePath, fileName);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PutMapping("/description")
    public ResponseEntity<?> updateDescription(@RequestParam String exercisePath,
                                               @RequestParam String fileName,
                                               @RequestBody Map<String, String> body) throws IOException {
        String description = body.get("description");
        exerciseService.updateFileDescription(exercisePath, fileName, description != null ? description : "");
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/stream")
    public void streamFile(@RequestParam String exercisePath,
                           @RequestParam String fileName,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        Path filePath = fileService.getFilePath(exercisePath, fileName);
        long fileLength = Files.size(filePath);
        String contentType = ExerciseService.detectContentType(filePath);

        response.setContentType(contentType);
        response.setHeader("Accept-Ranges", "bytes");

        String rangeHeader = request.getHeader("Range");

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String rangeValue = rangeHeader.substring(6);
            String[] parts = rangeValue.split("-", 2);
            long start = Long.parseLong(parts[0]);
            long end = (parts.length > 1 && !parts[1].isEmpty())
                    ? Long.parseLong(parts[1])
                    : fileLength - 1;

            if (start >= fileLength) {
                response.setStatus(416);
                response.setHeader("Content-Range", "bytes */" + fileLength);
                return;
            }
            if (end >= fileLength) {
                end = fileLength - 1;
            }

            long contentLength = end - start + 1;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            response.setHeader("Content-Length", String.valueOf(contentLength));

            try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
                raf.seek(start);
                OutputStream out = response.getOutputStream();
                byte[] buffer = new byte[8192];
                long remaining = contentLength;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = raf.read(buffer, 0, toRead);
                    if (read == -1) break;
                    out.write(buffer, 0, read);
                    remaining -= read;
                }
                out.flush();
            }
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader("Content-Length", String.valueOf(fileLength));
            try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r");
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = raf.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
        }
    }
}
