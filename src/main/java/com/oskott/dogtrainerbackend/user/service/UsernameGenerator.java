package com.oskott.dogtrainerbackend.user.service;

import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Derives a unique, immutable {@code username} handle from a display name at account creation
 * time. Mirrors the slugification + collision-suffixing rules used by the one-off
 * {@code 029-add-users-username.xml} backfill migration, so newly created and backfilled
 * usernames look the same.
 */
@Component
public class UsernameGenerator {

    private static final int MAX_SLUG_LENGTH = 24;
    private static final String FALLBACK_SLUG = "user";

    private final UserRepository userRepository;

    public UsernameGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String generate(String displayName) {
        String slug = slugify(displayName);
        String candidate = slug;
        int suffix = 2;
        while (userRepository.existsByUsernameIgnoreCase(candidate)) {
            candidate = slug + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slugify(String displayName) {
        String cleaned = displayName == null
                ? ""
                : displayName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (cleaned.isEmpty()) {
            cleaned = FALLBACK_SLUG;
        }
        return cleaned.length() > MAX_SLUG_LENGTH ? cleaned.substring(0, MAX_SLUG_LENGTH) : cleaned;
    }
}
