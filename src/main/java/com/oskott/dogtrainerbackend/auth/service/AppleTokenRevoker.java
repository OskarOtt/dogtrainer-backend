package com.oskott.dogtrainerbackend.auth.service;

public interface AppleTokenRevoker {

    void revoke(String authorizationCode, String expectedSubject);
}
