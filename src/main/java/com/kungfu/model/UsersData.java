package com.kungfu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UsersData {
    private List<UserEntry> users = new ArrayList<>();
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant updatedAt;

    public UsersData() {
        this.updatedAt = Instant.now();
    }

    public List<UserEntry> getUsers() { return users; }
    public void setUsers(List<UserEntry> users) { this.users = users; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
