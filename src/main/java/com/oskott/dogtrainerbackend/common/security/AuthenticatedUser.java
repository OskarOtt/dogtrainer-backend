package com.oskott.dogtrainerbackend.common.security;

import java.util.UUID;

/**
 * Lightweight authenticated-principal used as the Authentication#getPrincipal() value
 * so downstream code never needs to re-fetch the User entity just to know who's logged in.
 */
public record AuthenticatedUser(UUID id, String email) {
}
