package com.kungfu.model;

import java.util.ArrayList;
import java.util.List;

public class FilesData {
    private List<FileMeta> files = new ArrayList<>();

    public FilesData() {}

    public List<FileMeta> getFiles() { return files; }
    public void setFiles(List<FileMeta> files) { this.files = files; }
}
