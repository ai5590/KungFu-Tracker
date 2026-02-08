package com.kungfu.model;

public class UserEntry {
    private String login;
    private String password;
    private boolean admin;
    private boolean canEdit;
    private String theme;

    public UserEntry() {}

    public UserEntry(String login, String password, boolean admin, boolean canEdit) {
        this.login = login;
        this.password = password;
        this.admin = admin;
        this.canEdit = canEdit;
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
}
