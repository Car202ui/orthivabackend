package com.orthiva.core.followup;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.orthiva.core.file.MediaDto;
import com.orthiva.core.file.MediaKind;

/** Public API of the follow-up module. */
public interface FollowUpService {

    /** Doctor only, order in SHIPPED or IN_FOLLOW_UP; the first one moves SHIPPED → IN_FOLLOW_UP. */
    FollowUpDto create(UUID orderId, FollowUpInput in);

    FollowUpDto update(UUID followUpId, FollowUpInput in);

    void delete(UUID followUpId);

    MediaDto addMedia(UUID followUpId, MediaKind kind, MultipartFile file);

    void removeMedia(UUID followUpId, UUID mediaId);

    /** Everyone who can see the order (doctor, patient, lab), ordered by treatment month. */
    List<FollowUpDto> forOrder(UUID orderId);
}
