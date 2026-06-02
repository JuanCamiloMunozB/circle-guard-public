package com.circleguard.form.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Stores uploaded form attachments. The storage root is externalized via
 * `form.storage.root` (relaxed-bound env `FORM_STORAGE_ROOT`, default `uploads`)
 * following the External Configuration pattern. We deliberately avoid a shared
 * world-writable location such as `/tmp`: the default is an application-owned
 * directory and the real path is injected per environment.
 */
@Service
public class StorageService {

    private final Path root;

    @Autowired
    public StorageService(@Value("${form.storage.root:uploads}") String storageRoot) {
        this(Paths.get(storageRoot));
    }

    // Visible-for-testing constructor: lets unit tests inject a @TempDir.
    StorageService(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage at " + root, e);
        }
    }

    public String store(MultipartFile file) {
        String filename = UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
        // Resolve, normalize and confirm the result stays under the storage root
        // so a crafted filename cannot traverse outside it.
        Path target = this.root.resolve(filename).normalize();
        if (!target.startsWith(this.root.normalize())) {
            throw new RuntimeException("Could not store the file. Error: resolved path escapes the storage root");
        }
        try {
            Files.copy(file.getInputStream(), target);
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
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
        String baseName = Paths.get(originalFilename).getFileName().toString();
        String safeName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        return safeName.isBlank() ? "file" : safeName;
    }
}
