package com.orthiva.core.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.orthiva.core.shared.config.StorageProperties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Single entry point for binary files (photos, X-rays, STL, PDF, video). Files live in
 * object storage; the database only ever stores the returned {@code storageKey}.
 */
@Service
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    public StorageService(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
    }

    /** Uploads a stream under {@code <folder>/<name>} and returns the object key. */
    public String upload(String folder, String name, String contentType, InputStream in, long length) {
        String key = folder + "/" + name;
        s3.putObject(
                PutObjectRequest.builder().bucket(props.bucket()).key(key).contentType(contentType).build(),
                RequestBody.fromInputStream(in, length));
        return key;
    }

    /** Uploads the file under {@code <folder>/<uuid>-<originalName>} and returns the key. */
    public String upload(String folder, MultipartFile file) throws IOException {
        String safeName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
        return upload(folder, UUID.randomUUID() + "-" + safeName, file.getContentType(), file.getInputStream(), file.getSize());
    }

    /** Time-limited download URL; the bucket itself is never public. */
    public URL presignedDownloadUrl(String key) {
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(props.presignMinutes()))
                .getObjectRequest(GetObjectRequest.builder().bucket(props.bucket()).key(key).build())
                .build();
        return presigner.presignGetObject(request).url();
    }
}
