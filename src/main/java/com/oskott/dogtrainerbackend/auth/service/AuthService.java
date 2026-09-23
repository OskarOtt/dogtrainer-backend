package com.oskott.dogtrainerbackend.auth.service;

import com.oskott.dogtrainerbackend.auth.dto.AuthResponse;
import com.oskott.dogtrainerbackend.auth.dto.AuthMethod;
import com.oskott.dogtrainerbackend.auth.dto.LoginRequest;
import com.oskott.dogtrainerbackend.auth.dto.RegisterRequest;
import com.oskott.dogtrainerbackend.auth.dto.SocialAuthRequest;
import com.oskott.dogtrainerbackend.auth.entity.ExternalIdentity;
import com.oskott.dogtrainerbackend.auth.entity.RefreshToken;
import com.oskott.dogtrainerbackend.auth.repository.ExternalIdentityRepository;
import com.oskott.dogtrainerbackend.common.exception.AuthFlowException;
import com.oskott.dogtrainerbackend.common.exception.AuthenticationFailedException;
import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;
import com.oskott.dogtrainerbackend.common.security.JwtService;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final ExternalIdentityRepository externalIdentityRepository;
    private final ExternalIdentityVerifier externalIdentityVerifier;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            ExternalIdentityRepository externalIdentityRepository,
            ExternalIdentityVerifier externalIdentityVerifier
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.externalIdentityRepository = externalIdentityRepository;
        this.externalIdentityVerifier = externalIdentityVerifier;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        User user = new User(
                UUID.randomUUID(),
                email,
                request.name().trim(),
                passwordEncoder.encode(request.password()),
                null,
                Instant.now()
        );
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password"));
        if (!user.hasPassword() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthenticationFailedException("Invalid email or password");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse socialLogin(SocialAuthRequest request) {
        VerifiedExternalIdentity verified = externalIdentityVerifier.verify(request.provider(), request.idToken());
        return externalIdentityRepository
                .findByProviderAndProviderSubject(request.provider(), verified.subject())
                .map(identity -> {
                    ensureActive(identity.getUser());
                    identity.updateProviderEmail(normalizeEmail(verified.email()), Instant.now());
                    return issueTokens(identity.getUser());
                })
                .orElseGet(() -> registerSocialUser(request, verified));
    }

    @Transactional
    public List<AuthMethod> linkSocialIdentity(UUID userId, SocialAuthRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException("Authenticated user no longer exists"));
        ensureActive(user);
        VerifiedExternalIdentity verified = externalIdentityVerifier.verify(request.provider(), request.idToken());

        ExternalIdentity existing = externalIdentityRepository
                .findByProviderAndProviderSubject(request.provider(), verified.subject())
                .orElse(null);
        if (existing != null) {
            if (!existing.getUser().getId().equals(userId)) {
                throw new AuthFlowException(
                        HttpStatus.CONFLICT,
                        "IDENTITY_ALREADY_LINKED",
                        "This provider account is linked to another user"
                );
            }
            existing.updateProviderEmail(normalizeEmail(verified.email()), Instant.now());
            return authMethods(user);
        }
        if (externalIdentityRepository.existsByUserIdAndProvider(userId, request.provider())) {
            throw new AuthFlowException(
                    HttpStatus.CONFLICT,
                    "PROVIDER_ALREADY_LINKED",
                    "A different account from this provider is already linked"
            );
        }

        externalIdentityRepository.save(new ExternalIdentity(
                UUID.randomUUID(),
                user,
                request.provider(),
                verified.subject(),
                normalizeEmail(verified.email()),
                Instant.now()
        ));
        return authMethods(user);
    }

    @Transactional(readOnly = true)
    public List<AuthMethod> authMethods(User user) {
        return AuthMethod.resolve(user.hasPassword(), externalIdentityRepository.findAllByUserId(user.getId()));
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken rotated = refreshTokenService.rotate(refreshToken);
        User user = rotated.getUser();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        return new AuthResponse(accessToken, rotated.getToken(), jwtService.getAccessTokenExpirationSeconds());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken.getToken(), jwtService.getAccessTokenExpirationSeconds());
    }

    private AuthResponse registerSocialUser(SocialAuthRequest request, VerifiedExternalIdentity verified) {
        String email = normalizeEmail(verified.email());
        if (!verified.emailVerified() || email == null) {
            throw new AuthFlowException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "VERIFIED_EMAIL_REQUIRED",
                    "Provider did not supply a verified email address"
            );
        }
        if (userRepository.existsByEmail(email)) {
            throw new AuthFlowException(
                    HttpStatus.CONFLICT,
                    "ACCOUNT_LINK_REQUIRED",
                    "An account with this email already exists. Log in with an existing method, then connect this provider from Profile."
            );
        }

        String displayName = firstNonBlank(request.displayName(), verified.displayName());
        if (displayName == null) {
            throw new AuthFlowException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "PROFILE_NAME_REQUIRED",
                    "Enter your name to finish creating your account"
            );
        }

        Instant now = Instant.now();
        User user = new User(UUID.randomUUID(), email, displayName, null, null, now);
        userRepository.save(user);
        externalIdentityRepository.save(new ExternalIdentity(
                UUID.randomUUID(),
                user,
                request.provider(),
                verified.subject(),
                email,
                now
        ));
        return issueTokens(user);
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }

    private void ensureActive(User user) {
        if (user.getDeletedAt() != null) {
            throw new AuthenticationFailedException("This account is no longer active");
        }
    }
}
