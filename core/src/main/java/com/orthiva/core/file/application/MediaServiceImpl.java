package com.orthiva.core.file.application;

import com.orthiva.core.file.domain.MediaAsset;
import com.orthiva.core.file.infrastructure.persistence.MediaAssetRepository;
import com.orthiva.core.file.infrastructure.storage.StorageService;
import com.orthiva.core.file.MediaDto;
import com.orthiva.core.file.MediaService;
import com.orthiva.core.file.MediaKind;
import com.orthiva.core.file.MediaOwner;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import net.coobird.thumbnailator.Thumbnails;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.orthiva.core.shared.tenant.TenantContext;
import com.orthiva.core.shared.web.DomainException;

/**
 * Stores uploads for any owner (order, plan, follow-up, person). Images are normalised
 * to JPEG at most {@value #MAX_IMAGE_EDGE}px on the long edge (≈ ≤ 2 MB) plus a
 * {@value #THUMB_EDGE}px thumbnail; other kinds are stored as-is. Callers are
 * responsible for authorising the owner; this service only checks the tenant.
 */
@Service
@Transactional
class MediaServiceImpl implements MediaService {

    static final int MAX_IMAGE_EDGE = 2000;
    static final int THUMB_EDGE = 320;
    private static final float JPEG_QUALITY = 0.85f;

    private final MediaAssetRepository assets;
    private final StorageService storage;

    MediaServiceImpl(MediaAssetRepository assets, StorageService storage) {
        this.assets = assets;
        this.storage = storage;
    }

    @Override
    public MediaDto store(MediaOwner owner, MediaKind kind, MultipartFile file) {
        var actor = TenantContext.require();
        if (file.isEmpty()) {
            throw DomainException.badRequest("empty_file", "The file is empty");
        }
        if (file.getSize() > kind.maxBytes()) {
            throw DomainException.badRequest("file_too_large", "File exceeds the limit for " + kind);
        }
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!kind.accepts(mime) && !(kind == MediaKind.STL && file.getOriginalFilename() != null
                && file.getOriginalFilename().toLowerCase().endsWith(".stl"))) {
            throw DomainException.badRequest("unsupported_type", mime + " is not valid for " + kind);
        }

        String folder = actor.tenantId() + "/" + owner.type().name().toLowerCase() + "/" + owner.id();
        String baseName = safeName(file.getOriginalFilename());
        try {
            if (kind.isImage()) {
                return storeImage(actor, owner, kind, file, folder, baseName);
            }
            String key = storage.upload(folder, UUID.randomUUID() + "-" + baseName, mime, file.getInputStream(), file.getSize());
            var asset = assets.save(new MediaAsset(actor.tenantId(), owner, kind, key, null, baseName, mime,
                    file.getSize(), null, null, actor.personId()));
            return toDto(asset);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store file", e);
        }
    }

    private MediaDto storeImage(TenantContext.Actor actor, MediaOwner owner, MediaKind kind, MultipartFile file,
                                String folder, String baseName) throws IOException {
        BufferedImage source = ImageIO.read(file.getInputStream());
        if (source == null) {
            throw DomainException.badRequest("unsupported_type", "Unreadable image");
        }
        byte[] main = jpeg(source, MAX_IMAGE_EDGE);
        byte[] thumb = jpeg(source, THUMB_EDGE);
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(main));

        String stem = UUID.randomUUID() + "-" + stripExtension(baseName);
        String key = storage.upload(folder, stem + ".jpg", "image/jpeg", new ByteArrayInputStream(main), main.length);
        String thumbKey = storage.upload(folder + "/thumbs", stem + ".jpg", "image/jpeg", new ByteArrayInputStream(thumb), thumb.length);

        var asset = assets.save(new MediaAsset(actor.tenantId(), owner, kind, key, thumbKey,
                stripExtension(baseName) + ".jpg", "image/jpeg", main.length,
                stored.getWidth(), stored.getHeight(), actor.personId()));
        return toDto(asset);
    }

    private static byte[] jpeg(BufferedImage source, int maxEdge) throws IOException {
        var out = new ByteArrayOutputStream();
        Thumbnails.of(source)
                .size(maxEdge, maxEdge)          // keeps aspect ratio; never upscales beyond the source
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY)
                .toOutputStream(out);
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    @Override
    public List<MediaDto> listFor(MediaOwner owner) {
        List<MediaAsset> found = switch (owner.type()) {
            case ORDER -> assets.findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAsc(owner.id());
            case PLAN -> assets.findByPlanIdAndDeletedAtIsNullOrderByCreatedAtAsc(owner.id());
            case FOLLOW_UP -> assets.findByFollowUpIdAndDeletedAtIsNullOrderByCreatedAtAsc(owner.id());
            case PERSON -> List.of();
        };
        return found.stream().map(this::toDto).toList();
    }

    /** Soft-deletes; the object stays in storage for audit and can be purged by a later job. */
    @Override
    public void delete(UUID mediaId, MediaOwner expectedOwner) {
        var asset = assets.findByIdAndDeletedAtIsNull(mediaId).orElseThrow(() -> DomainException.notFound("Media"));
        boolean matches = switch (expectedOwner.type()) {
            case ORDER -> expectedOwner.id().equals(asset.getOrderId());
            case PLAN -> expectedOwner.id().equals(asset.getPlanId());
            case FOLLOW_UP -> expectedOwner.id().equals(asset.getFollowUpId());
            case PERSON -> false;
        };
        if (!matches) {
            throw DomainException.notFound("Media");
        }
        asset.softDelete();
    }

    @Transactional(readOnly = true)
    @Override
    public MediaDto get(UUID mediaId) {
        return toDto(assets.findByIdAndDeletedAtIsNull(mediaId).orElseThrow(() -> DomainException.notFound("Media")));
    }

    private MediaDto toDto(MediaAsset a) {
        return new MediaDto(a.getId(), a.getKind(), a.getFileName(), a.getMimeType(), a.getSizeBytes(),
                a.getWidthPx(), a.getHeightPx(),
                storage.presignedDownloadUrl(a.getStorageKey()).toString(),
                a.getThumbnailKey() == null ? null : storage.presignedDownloadUrl(a.getThumbnailKey()).toString(),
                a.getCreatedAt());
    }

    private static String safeName(String original) {
        String name = original == null || original.isBlank() ? "file" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Convenience for callers that only have raw bytes (e.g. tests). */
    static InputStream bytes(byte[] data) {
        return new ByteArrayInputStream(data);
    }
}
