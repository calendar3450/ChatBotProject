package com.example.project.service;

import java.util.List;

public class PythonIngestRequest {
    private List<Long> document_id;
    private String file_path;
    private String title;

    public PythonIngestRequest(List<Long> documentId, String filePath, String title) {
        this.document_id = documentId;
        this.file_path = filePath;
        this.title = title;
    

    }

    public List<Long> getDocument_id() {return document_id;}
    public void setDocument_id(List<Long> document_id) {this.document_id = document_id;}

    public String getFile_path() {return file_path;}
    public void setFile_path(String file_path) {this.file_path = file_path;}

    public String getTitle() {return title;}
    public void setTitle(String title) {this.title = title;}
}
