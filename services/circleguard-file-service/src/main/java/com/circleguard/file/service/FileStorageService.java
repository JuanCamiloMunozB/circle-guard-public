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
        String filename = UUID.randomUUID().toString() + "_" + sanitizeFilename(file.getOriginalFilename());
        // Resolve and normalize, then verify the target stays inside the storage
        // root. This neutralizes any path-traversal sequences in the user-supplied
        // filename (e.g. "../../etc/passwd") before touching the filesystem.
        Path target = this.root.resolve(filename).normalize();
        if (!target.startsWith(this.root.normalize())) {
            throw new IllegalArgumentException("Resolved file path escapes the storage root");
        }
        try {
            Files.copy(file.getInputStream(), target);
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    /**
     * Reduces an untrusted client-supplied filename to a single safe path
     * component: directory parts are dropped and only a conservative character
     * set is kept, so the result can never alter the storage location.
     */
    private static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        // Keep only the final path element, discarding any directory separators.
        String baseName = Paths.get(originalFilename).getFileName().toString();
        String safeName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        return safeName.isBlank() ? "file" : safeName;
    }

    public Resource loadFile(String filename) {
        // Implement retrieval logic
        return null;
    }
}

interface Resource {}
