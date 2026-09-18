package com.oskott.dogtrainerbackend.common.security;

import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the JWT token from the Authorization header, validates it and, if valid,
 * populates the SecurityContext with an {@link AuthenticatedUser} principal.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            AuthenticatedUser authenticatedUser = jwtService.validate(token);
            // Access tokens live up to 15 minutes; re-checking deleted_at here (rather than only
            // at login/refresh) makes sure a deleted account can't keep using an already-issued
            // token for the rest of its lifetime.
            if (authenticatedUser != null && !isDeletedUser(authenticatedUser)) {
                var authentication = new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isDeletedUser(AuthenticatedUser authenticatedUser) {
        return userRepository.findById(authenticatedUser.id())
                .map(user -> user.getDeletedAt() != null)
                .orElse(true);
    }
}
