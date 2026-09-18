package com.orthiva.core.file;

import java.time.Instant;
import java.util.UUID;

/** Media metadata plus short-lived signed URLs for download/preview. */
public record MediaDto(
        UUID id,
        MediaKind kind,
        String fileName,
        String mimeType,
        long sizeBytes,
        Integer widthPx,
        Integer heightPx,
        String url,
        String thumbnailUrl,
        Instant createdAt) {
}
