package com.oskott.dogtrainerbackend.storage;

import java.util.Map;
import java.util.Optional;

/**
 * Server-side allowlist of content types/extensions and size limits per media kind. Extensions
 * are derived from this map (never from a client-supplied filename) to avoid extension spoofing.
 */
public enum MediaCategory {

    IMAGE(
            Map.of(
                    "image/jpeg", "jpg",
                    "image/png", "png",
                    "image/webp", "webp"
            ),
            5L * 1024 * 1024
    ),
    VIDEO(
            Map.of(
                    "video/mp4", "mp4",
                    "video/quicktime", "mov",
                    "video/webm", "webm"
            ),
            100L * 1024 * 1024
    );

    private final Map<String, String> extensionsByContentType;
    private final long maxSizeBytes;

    MediaCategory(Map<String, String> extensionsByContentType, long maxSizeBytes) {
        this.extensionsByContentType = extensionsByContentType;
        this.maxSizeBytes = maxSizeBytes;
    }

    public boolean supportsContentType(String contentType) {
        return extensionsByContentType.containsKey(contentType);
    }

    public String extensionFor(String contentType) {
        return extensionsByContentType.get(contentType);
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }

    public static Optional<MediaCategory> fromExtension(String extension) {
        for (MediaCategory category : values()) {
            if (category.extensionsByContentType.containsValue(extension)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    public static Optional<MediaCategory> fromContentType(String contentType) {
        for (MediaCategory category : values()) {
            if (category.supportsContentType(contentType)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
