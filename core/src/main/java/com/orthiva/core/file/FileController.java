package com.orthiva.core.file;

import java.io.IOException;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final StorageService storage;

    public FileController(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Phase-0 smoke test for MinIO: uploads a file and returns its key plus a
     * presigned URL. Will be replaced by domain-specific upload endpoints.
     */
    @PostMapping("/test")
    public Map<String, String> uploadTest(@RequestPart("file") MultipartFile file) throws IOException {
        String key = storage.upload("test", file);
        return Map.of("key", key, "url", storage.presignedDownloadUrl(key).toString());
    }
}
