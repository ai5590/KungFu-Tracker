package com.kungfu.model;

public class FileInfo {
    private String fileName;
    private long size;
    private String contentType;
    private String url;
    private String description;

    public FileInfo() {}

    public FileInfo(String fileName, long size, String contentType, String url, String description) {
        this.fileName = fileName;
        this.size = size;
        this.contentType = contentType;
        this.url = url;
        this.description = description;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
