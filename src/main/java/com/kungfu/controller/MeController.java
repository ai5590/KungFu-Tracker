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
        return Map.of(
            "login", user.getLogin(),
            "admin", user.isAdmin(),
            "canEdit", user.isCanEdit()
        );
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
}
