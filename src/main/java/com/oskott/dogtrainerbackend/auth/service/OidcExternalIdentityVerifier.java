package com.oskott.dogtrainerbackend.auth.service;

import com.oskott.dogtrainerbackend.auth.entity.ExternalAuthProvider;
import com.oskott.dogtrainerbackend.common.exception.AuthenticationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OidcExternalIdentityVerifier implements ExternalIdentityVerifier {

    private static final String APPLE_JWK_SET_URI = "https://appleid.apple.com/auth/keys";
    private static final Set<String> APPLE_ISSUERS = Set.of("https://appleid.apple.com");

    private final Map<ExternalAuthProvider, JwtDecoder> decoders;

    @Autowired
    public OidcExternalIdentityVerifier(
            @Value("${app.auth.apple.audiences:}") String appleAudiences
    ) {
        this.decoders = new EnumMap<>(ExternalAuthProvider.class);
        decoders.put(ExternalAuthProvider.APPLE, decoder(
                APPLE_JWK_SET_URI,
                APPLE_ISSUERS,
                parseAudiences(appleAudiences)
        ));
    }

    OidcExternalIdentityVerifier(JwtDecoder appleDecoder) {
        this.decoders = new EnumMap<>(ExternalAuthProvider.class);
        decoders.put(ExternalAuthProvider.APPLE, appleDecoder);
    }

    @Override
    public VerifiedExternalIdentity verify(ExternalAuthProvider provider, String idToken) {
        try {
            Jwt jwt = decoders.get(provider).decode(idToken);
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            boolean emailVerified = booleanClaim(jwt.getClaim("email_verified"));
            String name = jwt.getClaimAsString("name");
            if (subject == null || subject.isBlank() || subject.length() > 255) {
                throw new AuthenticationFailedException("Provider credential has no subject");
            }
            if (email != null && email.length() > 255) {
                throw new AuthenticationFailedException("Provider credential has an invalid email");
            }
            return new VerifiedExternalIdentity(subject, email, emailVerified, name, jwt.getIssuedAt());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthenticationFailedException("Invalid or expired provider credential");
        }
    }

    private JwtDecoder decoder(String jwkSetUri, Set<String> issuers, Set<String> audiences) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> jwt.getIssuer() != null
                && issuers.contains(jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : failure("Unexpected token issuer");
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> !audiences.isEmpty()
                && jwt.getAudience().stream().anyMatch(audiences::contains)
                ? OAuth2TokenValidatorResult.success()
                : failure("Unexpected token audience");
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                issuerValidator,
                audienceValidator
        ));
        return decoder;
    }

    private OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }

    private Set<String> parseAudiences(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean booleanClaim(Object claim) {
        return claim instanceof Boolean value ? value : Boolean.parseBoolean(String.valueOf(claim));
    }
}
