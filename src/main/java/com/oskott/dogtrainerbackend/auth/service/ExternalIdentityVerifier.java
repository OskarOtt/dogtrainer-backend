package com.oskott.dogtrainerbackend.auth.service;

import com.oskott.dogtrainerbackend.auth.entity.ExternalAuthProvider;

public interface ExternalIdentityVerifier {

    VerifiedExternalIdentity verify(ExternalAuthProvider provider, String idToken);
}
