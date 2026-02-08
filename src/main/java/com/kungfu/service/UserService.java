package com.kungfu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kungfu.model.UserEntry;
import com.kungfu.model.UsersData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserService implements UserDetailsService {

    private final Path usersJsonFile;
    private final Path usersTextFile;
    private final ObjectMapper mapper;

    private volatile UsersData cachedData;
    private volatile long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 3000;

    public UserService(@Value("${app.users-file}") String usersFilePath) {
        this.usersTextFile = Path.of(usersFilePath);
        this.usersJsonFile = Path.of(usersFilePath.replace(".txt", ".json"));
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path getUsersJsonFile() {
        return usersJsonFile;
    }

    public synchronized void initAndMigrate() throws IOException {
        if (Files.exists(usersJsonFile)) {
            return;
        }
        UsersData data = new UsersData();
        if (Files.exists(usersTextFile)) {
            List<String> lines = Files.readAllLines(usersTextFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String login = parts[0].trim().toLowerCase();
                    String password = parts[1].trim();
                    boolean isAdmin = login.equalsIgnoreCase("ai");
                    data.getUsers().add(new UserEntry(login, password, isAdmin, true));
                }
            }
        }
        if (data.getUsers().isEmpty()) {
            data.getUsers().add(new UserEntry("admin", "admin", true, true));
        }
        data.setUpdatedAt(Instant.now());
        saveData(data);
    }

    private UsersData loadData() throws IOException {
        long now = System.currentTimeMillis();
        if (cachedData != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            return cachedData;
        }
        if (!Files.exists(usersJsonFile)) {
            initAndMigrate();
        }
        UsersData data = mapper.readValue(usersJsonFile.toFile(), UsersData.class);
        cachedData = data;
        cacheTimestamp = System.currentTimeMillis();
        return data;
    }

    private synchronized void saveData(UsersData data) throws IOException {
        data.setUpdatedAt(Instant.now());
        mapper.writerWithDefaultPrettyPrinter().writeValue(usersJsonFile.toFile(), data);
        cachedData = data;
        cacheTimestamp = System.currentTimeMillis();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            UsersData data = loadData();
            for (UserEntry u : data.getUsers()) {
                if (u.getLogin().equalsIgnoreCase(username)) {
                    List<String> roles = new ArrayList<>();
                    roles.add("USER");
                    if (u.isCanEdit()) roles.add("EDITOR");
                    if (u.isAdmin()) roles.add("ADMIN");
                    return User.builder()
                            .username(u.getLogin())
                            .password("{noop}" + u.getPassword())
                            .roles(roles.toArray(new String[0]))
                            .build();
                }
            }
            throw new UsernameNotFoundException("User not found: " + username);
        } catch (IOException e) {
            throw new UsernameNotFoundException("Error reading users file", e);
        }
    }

    public UserEntry findUser(String login) throws IOException {
        UsersData data = loadData();
        for (UserEntry u : data.getUsers()) {
            if (u.getLogin().equalsIgnoreCase(login)) return u;
        }
        return null;
    }

    public List<UserEntry> getAllUsers() throws IOException {
        return loadData().getUsers();
    }

    public synchronized void addUser(String login, String password, boolean admin, boolean canEdit) throws IOException {
        UsersData data = loadData();
        for (UserEntry u : data.getUsers()) {
            if (u.getLogin().equalsIgnoreCase(login)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
            }
        }
        data.getUsers().add(new UserEntry(login.toLowerCase(), password, admin, canEdit));
        saveData(data);
    }

    public synchronized void updateUser(String login, Boolean admin, Boolean canEdit) throws IOException {
        UsersData data = loadData();
        for (UserEntry u : data.getUsers()) {
            if (u.getLogin().equalsIgnoreCase(login)) {
                if (admin != null) u.setAdmin(admin);
                if (canEdit != null) u.setCanEdit(canEdit);
                saveData(data);
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }

    public synchronized void changePassword(String login, String newPassword) throws IOException {
        UsersData data = loadData();
        for (UserEntry u : data.getUsers()) {
            if (u.getLogin().equalsIgnoreCase(login)) {
                u.setPassword(newPassword);
                saveData(data);
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }

    public synchronized void deleteUser(String login) throws IOException {
        UsersData data = loadData();
        if (data.getUsers().stream().anyMatch(u -> u.getLogin().equalsIgnoreCase(login) && u.isAdmin())) {
            long adminCount = data.getUsers().stream().filter(UserEntry::isAdmin).count();
            if (adminCount <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete the last administrator");
            }
        }
        boolean removed = data.getUsers().removeIf(u -> u.getLogin().equalsIgnoreCase(login));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        saveData(data);
    }

    public String getUserPassword(String login) throws IOException {
        UserEntry u = findUser(login);
        if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return u.getPassword();
    }
}
