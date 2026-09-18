package com.orthiva.core.file;

import java.util.Set;

/** Mirrors the PostgreSQL enum {@code media_kind}. */
public enum MediaKind {
    PHOTO_FRONTAL, PHOTO_PROFILE, PHOTO_SMILE,
    PHOTO_INTRAORAL_UPPER, PHOTO_INTRAORAL_LOWER, PHOTO_INTRAORAL_RIGHT, PHOTO_INTRAORAL_LEFT, PHOTO_INTRAORAL_FRONTAL,
    XRAY_PANORAMIC, XRAY_LATERAL,
    VIDEO, STL, PDF,
    MODEL3D_BEFORE_LEFT, MODEL3D_BEFORE_FRONTAL, MODEL3D_BEFORE_RIGHT,
    MODEL3D_AFTER_LEFT, MODEL3D_AFTER_FRONTAL, MODEL3D_AFTER_RIGHT,
    UPPER_BEFORE, UPPER_AFTER, UPPER_MOVEMENT, LOWER_BEFORE, LOWER_AFTER, LOWER_MOVEMENT,
    FOLLOW_UP_PHOTO, AVATAR, OTHER;

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    /** Photos, X-rays and rendered 3D views are images: compressed to JPG and given a thumbnail. */
    public boolean isImage() {
        return this != VIDEO && this != STL && this != PDF && this != OTHER;
    }

    public boolean accepts(String mimeType) {
        if (mimeType == null) return false;
        return switch (this) {
            case VIDEO -> mimeType.startsWith("video/");
            case STL -> mimeType.equals("model/stl") || mimeType.equals("application/sla")
                    || mimeType.equals("application/octet-stream") || mimeType.equals("application/vnd.ms-pki.stl");
            case PDF -> mimeType.equals("application/pdf");
            case OTHER -> true;
            default -> IMAGE_TYPES.contains(mimeType);
        };
    }

    /** Upload limit per kind, in bytes. */
    public long maxBytes() {
        return switch (this) {
            case VIDEO -> 200L * 1024 * 1024;
            case STL -> 100L * 1024 * 1024;
            case PDF -> 25L * 1024 * 1024;
            default -> 25L * 1024 * 1024;   // raw camera photos before we compress them
        };
    }
}
