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

## User search and usernames

Every user has a unique, immutable `username` handle (e.g. `anna-lee`), separate from the
mutable display `name`. It's auto-generated at account creation by slugifying the display name
(lowercased, non-alphanumerics collapsed to `-`, truncated to 24 chars, falling back to `user`
when empty) and appending `-2`, `-3`, ... on collision (see `UsernameGenerator`). It's read-only -
there's no endpoint to change it. Existing accounts were backfilled the same way by the
`029-add-users-username.xml` migration.

`GET /api/v1/users/search?q=...&limit=...` searches `name`/`username` (`q` must be at least 2
characters; `limit` defaults to 20, capped at 50; soft-deleted accounts are excluded):

- **`prod` (Postgres):** fuzzy, typo-tolerant matching via the `pg_trgm` extension
  (`similarity()`/`%` over GIN trigram indexes on `name`/`username`), ranked by similarity. See
  `PgTrigramUserSearchRepository` and `030-add-users-trgm-search.xml`.
- **`local`/`test` (H2):** `pg_trgm` doesn't exist outside Postgres, so these profiles fall back to
  a simple case-insensitive substring (`LIKE`) match with no fuzzy ranking - see
  `LikeUserSearchRepository`. This fallback path is what the automated test suite exercises; the
  Postgres trigram behavior is prod-only and isn't covered by automated tests.

Note: the existing `PUT /api/v1/users/me/username` endpoint actually updates the mutable display
`name` (kept as-is for API/client compatibility — the URL and request shape predate the new
`username` field); its backend DTO/service method are named `UpdateDisplayNameRequest`/
`updateDisplayName` to avoid confusion with the new, unrelated `username` column.