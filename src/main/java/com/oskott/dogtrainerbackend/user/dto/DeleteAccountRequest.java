package com.oskott.dogtrainerbackend.user.dto;

import com.oskott.dogtrainerbackend.auth.dto.AuthMethod;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
        AuthMethod method,
        @Size(max = 255) String password,
        @Size(max = 8192) String idToken,
        @Size(max = 4096) String authorizationCode
) {
}
