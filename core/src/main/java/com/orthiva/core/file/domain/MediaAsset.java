package com.orthiva.core.file.domain;

import com.orthiva.core.file.MediaKind;
import com.orthiva.core.file.MediaOwner;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Metadata of a binary stored in object storage. The bytes never touch the database. */
@Entity
@Table(name = "media_asset")
public class MediaAsset {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "follow_up_id")
    private UUID followUpId;

    @Column(name = "person_id")
    private UUID personId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private MediaKind kind;

    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    @Column(name = "thumbnail_key", length = 400)
    private String thumbnailKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MediaAsset() {
    }

    public MediaAsset(UUID tenantId, MediaOwner owner, MediaKind kind, String storageKey, String thumbnailKey,
               String fileName, String mimeType, long sizeBytes, Integer widthPx, Integer heightPx, UUID uploadedBy) {
        this.tenantId = tenantId;
        switch (owner.type()) {
            case ORDER -> this.orderId = owner.id();
            case PLAN -> this.planId = owner.id();
            case FOLLOW_UP -> this.followUpId = owner.id();
            case PERSON -> this.personId = owner.id();
        }
        this.kind = kind;
        this.storageKey = storageKey;
        this.thumbnailKey = thumbnailKey;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.uploadedBy = uploadedBy;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getFollowUpId() {
        return followUpId;
    }

    public MediaKind getKind() {
        return kind;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getThumbnailKey() {
        return thumbnailKey;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidthPx() {
        return widthPx;
    }

    public Integer getHeightPx() {
        return heightPx;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
