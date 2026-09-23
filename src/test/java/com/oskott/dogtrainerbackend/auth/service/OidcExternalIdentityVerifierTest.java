package com.oskott.dogtrainerbackend.auth.service;

import com.oskott.dogtrainerbackend.auth.entity.ExternalAuthProvider;
import com.oskott.dogtrainerbackend.common.exception.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcExternalIdentityVerifierTest {

    @Test
    void mapsVerifiedClaimsWithoutTrustingClientIdentityFields() {
        Instant issuedAt = Instant.now();
        Jwt jwt = Jwt.withTokenValue("signed-provider-token")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .subject("provider-subject")
                .audience(List.of("server-client"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300))
                .claim("email", "trainer@example.com")
                .claim("email_verified", "true")
                .claim("name", "Trainer")
                .build();
        JwtDecoder decoder = ignored -> jwt;
        OidcExternalIdentityVerifier verifier = new OidcExternalIdentityVerifier(decoder);

        VerifiedExternalIdentity verified = verifier.verify(ExternalAuthProvider.APPLE, "token");

        assertThat(verified.subject()).isEqualTo("provider-subject");
        assertThat(verified.email()).isEqualTo("trainer@example.com");
        assertThat(verified.emailVerified()).isTrue();
        assertThat(verified.displayName()).isEqualTo("Trainer");
        assertThat(verified.issuedAt()).isEqualTo(issuedAt);
    }

    @Test
    void convertsDecoderFailureToAuthenticationFailure() {
        JwtDecoder decoder = ignored -> {
            throw new JwtException("bad signature");
        };
        OidcExternalIdentityVerifier verifier = new OidcExternalIdentityVerifier(decoder);

        assertThatThrownBy(() -> verifier.verify(ExternalAuthProvider.APPLE, "invalid"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid or expired provider credential");
    }
}
