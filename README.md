# Dogtrainer backend

## Social authentication

Apple acts only as external identity proof. This service verifies provider ID tokens, stores the provider subject against a local user, and issues the same app JWT access token and rotating refresh token used by password login.

Configure:

```bash
APPLE_CLIENT_IDS=com.oskar.ott.dogtrainerapp
APPLE_CLIENT_ID=com.oskar.ott.dogtrainerapp
APPLE_TEAM_ID=your-team-id
APPLE_KEY_ID=your-sign-in-with-apple-key-id
APPLE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
AUTH_REAUTH_MAX_AGE_SECONDS=300
```

`APPLE_PRIVATE_KEY` is the Sign in with Apple `.p8` key in PKCS#8 PEM form; keep it in secret management, never source control or Expo public variables.

Provider ID tokens are validated against provider JWK sets with issuer, audience, expiry, issued-at, subject, and verified-email checks. Existing accounts are never linked by email automatically. A user must first authenticate with an existing method and link the provider from Profile.

Apple account deletion requires a fresh identity token and authorization code. The backend exchanges the code and revokes Apple authorization before deleting local account data.