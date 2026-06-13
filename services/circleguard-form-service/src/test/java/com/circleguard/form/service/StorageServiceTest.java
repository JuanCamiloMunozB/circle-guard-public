package com.circleguard.form.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit test for form-service StorageService. The storage root is now
 * externalized (form.storage.root); tests inject a @TempDir via the
 * visible-for-testing constructor so they never touch a shared directory.
 */
class StorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void constructor_initializesStorageDirectory() {
        // The constructor calls Files.createDirectories; it is idempotent.
        StorageService svc = new StorageService(tempDir);

        assertTrue(Files.isDirectory(tempDir),
                "storage root must exist after construction");
        assertNotNull(svc);
    }

    @Test
    void store_writesFileAndReturnsUuidPrefixedName() throws IOException {
        StorageService svc = new StorageService(tempDir);
        MockMultipartFile file = new MockMultipartFile(
                "file", "doctor-note.pdf", "application/pdf", "content".getBytes());

        String name = svc.store(file);

        assertNotNull(name);
        assertTrue(name.endsWith("_doctor-note.pdf"));
        assertEquals(36, name.indexOf('_'),
                "stored filename must start with a 36-char UUID");
        assertTrue(Files.exists(tempDir.resolve(name)),
                "file must be written under the storage root");
    }

    @Test
    void store_sanitizesTraversalSequencesInFilename() {
        StorageService svc = new StorageService(tempDir);
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd", "text/plain", "x".getBytes());

        String name = svc.store(file);

        // Directory components are stripped; the stored name keeps only the base.
        assertTrue(name.endsWith("_passwd"),
                "traversal path must collapse to its base filename");
        assertFalse(name.contains("/") || name.contains(".."),
                "stored name must not retain traversal sequences");
        assertTrue(Files.exists(tempDir.resolve(name)),
                "file must be written inside the storage root");
    }

    @Test
    void store_defaultsToFileWhenOriginalFilenameIsMissing() throws IOException {
        StorageService svc = new StorageService(tempDir);
        // A multipart with no original filename must still store under a safe
        // default name; exercises the null/blank branch of sanitizeFilename.
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "application/octet-stream", "x".getBytes());

        String name = svc.store(file);

        assertTrue(name.endsWith("_file"),
                "missing filename must collapse to the safe default 'file'");
        assertTrue(Files.exists(tempDir.resolve(name)),
                "file must be written inside the storage root");
    }

    @Test
    void store_wrapsIOExceptionInRuntimeException() throws IOException {
        StorageService svc = new StorageService(tempDir);
        MultipartFile broken = Mockito.mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.bin");
        when(broken.getInputStream()).thenThrow(new IOException("upstream gone"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.store(broken));
        assertTrue(ex.getMessage().startsWith("Could not store the file"),
                "wrapper must keep the agreed prefix so the controller can surface it");
    }
}
