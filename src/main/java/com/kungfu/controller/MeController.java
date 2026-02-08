package com.kungfu.controller;

import com.kungfu.model.UserEntry;
import com.kungfu.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Map<String, Object> getMe(Authentication auth) throws IOException {
        String login = auth.getName();
        UserEntry user = userService.findUser(login);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        var result = new java.util.HashMap<String, Object>();
        result.put("login", user.getLogin());
        result.put("admin", user.isAdmin());
        result.put("canEdit", user.isCanEdit());
        result.put("theme", user.getTheme() != null ? user.getTheme() : "midnight");
        return result;
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication auth, @RequestBody Map<String, String> body) throws IOException {
        String login = auth.getName();
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required");
        }

        UserEntry user = userService.findUser(login);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (!user.getPassword().equals(oldPassword)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Old password is incorrect");
        }

        userService.changePassword(login, newPassword);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private static final java.util.Set<String> VALID_THEMES = java.util.Set.of("light", "midnight", "dark", "sakura");

    @PostMapping("/theme")
    public ResponseEntity<?> setTheme(Authentication auth, @RequestBody Map<String, String> body) throws IOException {
        String login = auth.getName();
        String theme = body.get("theme");
        if (theme == null || theme.isBlank() || !VALID_THEMES.contains(theme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid theme");
        }
        userService.setUserTheme(login, theme);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
