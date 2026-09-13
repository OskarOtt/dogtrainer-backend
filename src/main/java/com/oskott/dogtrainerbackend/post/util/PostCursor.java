package com.oskott.dogtrainerbackend.post.util;

import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes/decodes the opaque {@code nextCursor} token used for keyset pagination over posts, so
 * callers only ever pass an opaque string back and forth instead of raw timestamps/ids.
 */
public final class PostCursor {

    private PostCursor() {
    }

    public record Position(Instant createdAt, UUID id) {
    }

    public static String encode(Instant createdAt, UUID id) {
        String raw = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Position decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separatorIndex = raw.indexOf(':');
            Instant createdAt = Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separatorIndex)));
            UUID id = UUID.fromString(raw.substring(separatorIndex + 1));
            return new Position(createdAt, id);
        } catch (RuntimeException e) {
            throw new BusinessRuleException("Invalid cursor");
        }
    }
}
