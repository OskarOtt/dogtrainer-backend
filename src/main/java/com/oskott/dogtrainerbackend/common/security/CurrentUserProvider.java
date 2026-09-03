package com.oskott.dogtrainerbackend.common.security;

import com.oskott.dogtrainerbackend.common.exception.AuthenticationFailedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Convenience accessor for the currently authenticated user, used by services to enforce
 * that a user only ever reads/writes their own dogs and related data.
 */
@Component
public class CurrentUserProvider {

    public AuthenticatedUser getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (!(principal instanceof AuthenticatedUser authenticatedUser)) {
            throw new AuthenticationFailedException("No authenticated user in the current request context");
        }
        return authenticatedUser;
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().id();
    }
}
