package com.orthiva.core.file.web;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.file.MediaService;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read access to media by id (tenant-scoped through RLS). Uploads go through the owning
 * aggregate's endpoints (e.g. POST /api/orders/{id}/media) so ownership is enforced there.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    /** Fresh signed URLs for one asset (the ones embedded in lists expire after a few minutes). */
    @GetMapping("/{id}")
    public MediaDto get(@PathVariable UUID id) {
        return media.get(id);
    }
}
