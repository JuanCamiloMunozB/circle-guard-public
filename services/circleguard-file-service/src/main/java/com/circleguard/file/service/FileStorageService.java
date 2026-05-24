package com.circleguard.file.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * File storage service. The storage root is parameterized via
 * `file.storage.root` (default `uploads`) so tests can point at a @TempDir
 * instead of polluting the working directory.
 */
@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${file.storage.root:uploads}") String storageRoot) {
        this(Paths.get(storageRoot));
    }

    // Visible-for-testing constructor: lets unit tests inject a @TempDir.
    FileStorageService(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage at " + root, e);
        }
    }

    public String saveFile(MultipartFile file) {
        String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        try {
            Files.copy(file.getInputStream(), this.root.resolve(filename));
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    public Resource loadFile(String filename) {
        // Implement retrieval logic
        return null;
    }
}

interface Resource {}
