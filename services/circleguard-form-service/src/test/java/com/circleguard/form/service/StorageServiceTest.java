package com.circleguard.form.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit test for form-service StorageService. The service stores into a fixed
 * /tmp/circleguard-uploads path (hardcoded), so we test it as-is by exercising
 * the same path the production code uses.
 */
class StorageServiceTest {

    @Test
    void constructor_initializesStorageDirectory() {
        // The constructor calls Files.createDirectories; it is idempotent.
        StorageService svc = new StorageService();

        assertTrue(Files.isDirectory(Paths.get("/tmp/circleguard-uploads"))
                        || Files.isDirectory(Paths.get("C:/tmp/circleguard-uploads")),
                "storage root must exist after construction");
        assertNotNull(svc);
    }

    @Test
    void store_writesFileAndReturnsUuidPrefixedName() throws IOException {
        StorageService svc = new StorageService();
        MockMultipartFile file = new MockMultipartFile(
                "file", "doctor-note.pdf", "application/pdf", "content".getBytes());

        String name = svc.store(file);

        assertNotNull(name);
        assertTrue(name.endsWith("_doctor-note.pdf"));
        assertEquals(36, name.indexOf('_'),
                "stored filename must start with a 36-char UUID");
    }

    @Test
    void store_wrapsIOExceptionInRuntimeException() throws IOException {
        StorageService svc = new StorageService();
        MultipartFile broken = Mockito.mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.bin");
        when(broken.getInputStream()).thenThrow(new IOException("upstream gone"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.store(broken));
        assertTrue(ex.getMessage().startsWith("Could not store the file"),
                "wrapper must keep the agreed prefix so the controller can surface it");
    }
}
