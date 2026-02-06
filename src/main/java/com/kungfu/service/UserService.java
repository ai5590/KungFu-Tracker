package com.kungfu.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Service
public class UserService implements UserDetailsService {

    private final Path usersFile;

    public UserService(@Value("${app.users-file}") String usersFilePath) {
        this.usersFile = Path.of(usersFilePath);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            if (!Files.exists(usersFile)) {
                throw new UsernameNotFoundException("Users file not found");
            }
            List<String> lines = Files.readAllLines(usersFile);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(":", 2);
                if (parts.length == 2 && parts[0].equals(username)) {
                    return User.builder()
                            .username(parts[0])
                            .password("{noop}" + parts[1])
                            .roles("USER")
                            .build();
                }
            }
            throw new UsernameNotFoundException("User not found: " + username);
        } catch (IOException e) {
            throw new UsernameNotFoundException("Error reading users file", e);
        }
    }
}
