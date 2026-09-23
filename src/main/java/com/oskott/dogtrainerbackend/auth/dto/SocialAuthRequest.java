package com.oskott.dogtrainerbackend.auth.dto;

import com.oskott.dogtrainerbackend.auth.entity.ExternalAuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SocialAuthRequest(
        @NotNull ExternalAuthProvider provider,
        @NotBlank @Size(max = 8192) String idToken,
        @Size(max = 255) String displayName,
        @Size(max = 4096) String authorizationCode
) {
}
