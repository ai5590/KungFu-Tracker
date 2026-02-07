package com.kungfu.controller;

import com.kungfu.model.UserEntry;
import com.kungfu.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> getUsers() throws IOException {
        return userService.getAllUsers().stream()
            .map(u -> Map.<String, Object>of(
                "login", u.getLogin(),
                "admin", u.isAdmin(),
                "canEdit", u.isCanEdit()
            ))
            .collect(Collectors.toList());
    }

    @GetMapping("/users/password")
    public Map<String, String> getUserPassword(@RequestParam String login) throws IOException {
        String password = userService.getUserPassword(login);
        return Map.of("password", password);
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) throws IOException {
        String login = (String) body.get("login");
        String password = (String) body.get("password");
        boolean admin = Boolean.TRUE.equals(body.get("admin"));
        boolean canEdit = body.get("canEdit") == null ? true : Boolean.TRUE.equals(body.get("canEdit"));
        userService.addUser(login, password, admin, canEdit);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PutMapping("/users")
    public ResponseEntity<?> updateUser(@RequestBody Map<String, Object> body) throws IOException {
        String login = (String) body.get("login");
        Boolean admin = body.containsKey("admin") ? Boolean.TRUE.equals(body.get("admin")) : null;
        Boolean canEdit = body.containsKey("canEdit") ? Boolean.TRUE.equals(body.get("canEdit")) : null;
        String password = (String) body.get("password");

        if (password != null && !password.isBlank()) {
            userService.changePassword(login, password);
        }
        userService.updateUser(login, admin, canEdit);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping("/users")
    public ResponseEntity<?> deleteUser(@RequestParam String login) throws IOException {
        userService.deleteUser(login);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
