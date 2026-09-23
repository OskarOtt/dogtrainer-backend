package com.oskott.dogtrainerbackend.auth.service;

import java.time.Instant;

public record VerifiedExternalIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String displayName,
        Instant issuedAt
) {
}
