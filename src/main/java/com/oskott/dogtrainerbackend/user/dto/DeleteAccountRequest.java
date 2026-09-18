package com.oskott.dogtrainerbackend.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Requires re-entering the current password as a safety check before this destructive,
 * irreversible action proceeds.
 */
public record DeleteAccountRequest(@NotBlank String password) {
}
