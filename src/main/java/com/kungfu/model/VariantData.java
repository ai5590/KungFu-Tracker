package com.kungfu.model;

import java.util.List;

public class VariantData {
    private String name;
    private String title;
    private String text;
    private String notes;
    private List<FileInfo> files;

    public VariantData() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<FileInfo> getFiles() { return files; }
    public void setFiles(List<FileInfo> files) { this.files = files; }
}
