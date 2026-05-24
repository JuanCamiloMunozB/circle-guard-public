package com.circleguard.file.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    @Test
    void constructor_createsStorageDirectoryIfMissing(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("nested").resolve("uploads");
        assertFalse(Files.exists(target));

        new FileStorageService(target);

        assertTrue(Files.isDirectory(target),
                "FileStorageService must create the storage root if it does not exist");
    }

    @Test
    void constructor_acceptsExistingDirectory(@TempDir Path tmp) {
        // Calling createDirectories on an existing path is a no-op, but we want to
        // ensure the constructor does not throw in that case.
        assertDoesNotThrow(() -> new FileStorageService(tmp));
    }

    @Test
    void saveFile_writesFileAndReturnsUuidPrefixedName(@TempDir Path tmp) throws IOException {
        FileStorageService svc = new FileStorageService(tmp);
        MockMultipartFile mpf = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", "hello".getBytes());

        String name = svc.saveFile(mpf);

        assertNotNull(name);
        assertTrue(name.endsWith("_certificate.pdf"),
                "filename must keep the original suffix to preserve mime hints");
        // UUID is 36 chars + "_" + filename
        assertEquals(36, name.indexOf('_'),
                "stored filename must start with a 36-char UUID");
        Path written = tmp.resolve(name);
        assertTrue(Files.exists(written));
        assertArrayEquals("hello".getBytes(), Files.readAllBytes(written));
    }

    @Test
    void saveFile_wrapsIOExceptionInRuntimeException(@TempDir Path tmp) throws IOException {
        FileStorageService svc = new FileStorageService(tmp);
        MultipartFile broken = Mockito.mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.bin");
        when(broken.getInputStream()).thenThrow(new IOException("disk error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.saveFile(broken));
        assertEquals("Could not store file", ex.getMessage());
        assertNotNull(ex.getCause());
        assertEquals("disk error", ex.getCause().getMessage());
    }

    @Test
    void loadFile_currentlyReturnsNullPlaceholder(@TempDir Path tmp) {
        // The implementation is a stub today. Test pins the documented behaviour
        // so a future implementation has to update this test too.
        FileStorageService svc = new FileStorageService(tmp);

        assertNull(svc.loadFile("anything"));
    }

    @Test
    void springConstructor_acceptsStorageRootProperty(@TempDir Path tmp) {
        // Exercises the production constructor path: @Value-bound string root.
        FileStorageService svc = new FileStorageService(tmp.resolve("via-string").toString());

        assertNotNull(svc);
        assertTrue(Files.isDirectory(tmp.resolve("via-string")));
    }
}
