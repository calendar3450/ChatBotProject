package com.example.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.project.domain.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {


}
