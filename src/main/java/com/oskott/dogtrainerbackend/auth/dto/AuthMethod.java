package com.oskott.dogtrainerbackend.auth.dto;

import com.oskott.dogtrainerbackend.auth.entity.ExternalIdentity;

import java.util.EnumSet;
import java.util.List;

public enum AuthMethod {
    PASSWORD,
    APPLE;

    public static List<AuthMethod> resolve(boolean hasPassword, List<ExternalIdentity> identities) {
        EnumSet<AuthMethod> methods = EnumSet.noneOf(AuthMethod.class);
        if (hasPassword) {
            methods.add(AuthMethod.PASSWORD);
        }
        identities.stream()
                .map(identity -> AuthMethod.valueOf(identity.getProvider().name()))
                .forEach(methods::add);
        return List.copyOf(methods);
    }
}
