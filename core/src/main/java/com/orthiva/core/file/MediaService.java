package com.orthiva.core.file;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

/**
 * Public API of the file module. Callers authorise the owner (order, plan, follow-up,
 * person); this module validates, normalises images, stores bytes and signs URLs.
 */
public interface MediaService {

    MediaDto store(MediaOwner owner, MediaKind kind, MultipartFile file);

    List<MediaDto> listFor(MediaOwner owner);

    /** Soft-deletes; fails with 404 unless the asset belongs to {@code expectedOwner}. */
    void delete(UUID mediaId, MediaOwner expectedOwner);

    MediaDto get(UUID mediaId);
}
