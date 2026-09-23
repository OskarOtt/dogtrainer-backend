package com.oskott.dogtrainerbackend.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oskott.dogtrainerbackend.auth.entity.ExternalAuthProvider;
import com.oskott.dogtrainerbackend.common.exception.AuthFlowException;
import com.oskott.dogtrainerbackend.common.exception.AuthenticationFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
public class AppleTokenRevocationService implements AppleTokenRevoker {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    private static final String TOKEN_URL = APPLE_AUDIENCE + "/auth/token";
    private static final String REVOKE_URL = APPLE_AUDIENCE + "/auth/revoke";

    private final RestClient restClient;
    private final String clientId;
    private final String teamId;
    private final String keyId;
    private final String privateKey;
    private final ExternalIdentityVerifier externalIdentityVerifier;

    public AppleTokenRevocationService(
            @Value("${app.auth.apple.client-id:}") String clientId,
            @Value("${app.auth.apple.team-id:}") String teamId,
            @Value("${app.auth.apple.key-id:}") String keyId,
            @Value("${app.auth.apple.private-key:}") String privateKey,
            ExternalIdentityVerifier externalIdentityVerifier
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.clientId = clientId;
        this.teamId = teamId;
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.externalIdentityVerifier = externalIdentityVerifier;
    }

    @Override
    public void revoke(String authorizationCode, String expectedSubject) {
        requireConfigured();
        try {
            String clientSecret = createClientSecret();
            MultiValueMap<String, String> exchangeForm = new LinkedMultiValueMap<>();
            exchangeForm.add("client_id", clientId);
            exchangeForm.add("client_secret", clientSecret);
            exchangeForm.add("code", authorizationCode);
            exchangeForm.add("grant_type", "authorization_code");

            Map<?, ?> tokenResponse = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(exchangeForm)
                    .retrieve()
                    .body(Map.class);
            Object token = tokenResponse == null ? null : tokenResponse.get("access_token");
            Object exchangedIdToken = tokenResponse == null ? null : tokenResponse.get("id_token");
            if (!(token instanceof String accessToken) || accessToken.isBlank()) {
                throw revocationFailed();
            }
            if (!(exchangedIdToken instanceof String idToken)
                    || !externalIdentityVerifier.verify(ExternalAuthProvider.APPLE, idToken)
                    .subject().equals(expectedSubject)) {
                throw new AuthenticationFailedException("Apple authorization does not match this user");
            }

            MultiValueMap<String, String> revokeForm = new LinkedMultiValueMap<>();
            revokeForm.add("client_id", clientId);
            revokeForm.add("client_secret", clientSecret);
            revokeForm.add("token", accessToken);
            revokeForm.add("token_type_hint", "access_token");
            restClient.post()
                    .uri(REVOKE_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(revokeForm)
                    .retrieve()
                    .toBodilessEntity();
        } catch (JOSEException
                 | NoSuchAlgorithmException
                 | InvalidKeySpecException
                 | RestClientException
                 | IllegalArgumentException
                 | ClassCastException ex) {
            throw revocationFailed();
        }
    }

    private String createClientSecret()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException {
        String normalizedKey = privateKey
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalizedKey);
        ECPrivateKey ecPrivateKey = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        Instant now = Instant.now();
        SignedJWT clientSecret = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(keyId)
                        .type(JOSEObjectType.JWT)
                        .build(),
                new JWTClaimsSet.Builder()
                        .issuer(teamId)
                        .subject(clientId)
                        .audience(APPLE_AUDIENCE)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(300)))
                        .build()
        );
        clientSecret.sign(new ECDSASigner(ecPrivateKey));
        return clientSecret.serialize();
    }

    private void requireConfigured() {
        if (clientId.isBlank() || teamId.isBlank() || keyId.isBlank() || privateKey.isBlank()) {
            throw new AuthFlowException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "APPLE_REVOCATION_UNAVAILABLE",
                    "Apple account deletion is not configured"
            );
        }
    }

    private AuthFlowException revocationFailed() {
        return new AuthFlowException(
                HttpStatus.BAD_GATEWAY,
                "APPLE_REVOCATION_FAILED",
                "Could not revoke Sign in with Apple authorization"
        );
    }
}
