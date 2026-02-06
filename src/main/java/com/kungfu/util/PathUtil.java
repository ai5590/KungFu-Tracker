package com.kungfu.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;

public final class PathUtil {

    private PathUtil() {}

    public static Path resolveAndValidate(Path dataRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path must not be empty");
        }
        if (relativePath.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path traversal is not allowed");
        }
        if (Path.of(relativePath).isAbsolute()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Absolute paths are not allowed");
        }
        Path resolved = dataRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(dataRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path traversal is not allowed");
        }
        return resolved;
    }

    public static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name must not be empty");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }
    }
}
