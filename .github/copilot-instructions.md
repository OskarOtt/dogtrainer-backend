# Copilot instructions for dogtrainer-backend

Spring Boot 4.1 / Java 21 REST API (Maven) backing the Flink dog-training app.

## Commands
- `./mvnw spring-boot:run` — run locally (defaults to `local` profile: in-memory H2, dummy
  R2/Apple credentials from `application-local.properties`, which is git-ignored — copy
  `application-local.template` to create it).
- `./mvnw test` — run the full suite (forces `spring.profiles.active=test` via the surefire
  config in `pom.xml`, using `src/test/resources/application-test.properties`).
- Run a single test class: `./mvnw test -Dtest=DogControllerIntegrationTest`.
- Run a single test method: `./mvnw test -Dtest=DogControllerIntegrationTest#dogCrudFlowIsScopedToTheOwningUser`.
- `./mvnw clean package` — build the jar (used by `Dockerfile`).

## Architecture
- Package-by-feature under `com.oskott.dogtrainerbackend`: `auth`, `dog`, `plan`, `training`,
  `post`, `comment`, `like`, `follow`, `goal`, `moderation`, `stats`, `storage`, `user`. Each
  feature package follows the same internal layout: `controller/`, `dto/`, `entity/`,
  `repository/`, `service/`. Cross-cutting code lives in `common/` (`exception/`, `security/`,
  `i18n/`, `dto/`).
- **Auth**: stateless JWT (`common/security/JwtService`, `JwtAuthenticationFilter`) — no sessions
  (`SessionCreationPolicy.STATELESS`). `SecurityConfig` permits only
  `/api/v1/auth/{register,login,social,refresh}` and `/h2-console/**`; every other endpoint
  requires a valid bearer token. Controllers/services fetch the caller via
  `CurrentUserProvider.getCurrentUser()/getCurrentUserId()`, which reads the `AuthenticatedUser`
  principal from `SecurityContextHolder` — services use this to scope all reads/writes to the
  owning user (e.g. dogs, plans, posts are never queried without an owner check).
- Social sign-in (Apple) verifies provider ID tokens against provider JWKs (issuer, audience,
  expiry, subject, verified-email) and stores the subject as an external identity linked to a
  local user; it issues the same JWT access/refresh tokens as password login. Existing accounts
  are never auto-linked by email — a user must log in first and link the provider from Profile.
  See README for the full `APPLE_*` env var list and the account-deletion flow (requires a fresh
  Apple identity token + authorization code; backend revokes Apple authorization before deleting
  local data).
- **Errors**: all exceptions funnel through `common/exception/GlobalExceptionHandler` into one
  `ErrorResponse` shape (`{status, error, message, path, fieldErrors?}`). Feature code should
  throw the existing typed exceptions (`ResourceNotFoundException` → 404,
  `AccessDeniedForResourceException` → 403, `BusinessRuleException` → 409, `InvalidFileException`
  → 415, `AuthenticationFailedException`/`AuthFlowException`) rather than returning error
  responses manually or adding new `@ExceptionHandler`s elsewhere.
- **i18n**: API error/validation messages are localized via `ApiMessageLocalizer` +
  `SupportedLocale` (English/Norwegian Bokmål, resolved from the `Accept-Language` header, `en`
  fallback) — this is separate from the DB-seeded catalog translations (see Liquibase below).
  Exception messages passed to `GlobalExceptionHandler`/`ErrorResponse` are message keys/text run
  through `messageLocalizer.localize(...)`, not raw English strings for the client.
- **Schema**: Liquibase owns the schema — `spring.jpa.hibernate.ddl-auto=validate`, Hibernate must
  never generate DDL. Migrations live in `src/main/resources/db/changelog/changes/`, numbered
  sequentially (`0NN-description.xml`) and registered in `db.changelog-master.xml`; bulk seed data
  (e.g. translated training-catalog rows) is loaded from CSVs in `db/changelog/data/`. Add new
  changes as a new numbered file, never edit an already-applied one.
- **Media storage**: `storage/StorageService` + `storage/ImageProcessingService` handle uploads to
  Cloudflare R2 via the AWS S3 SDK (`R2ClientConfig`/`R2Properties`), presigning PUT URLs for
  clients and validating/processing images (via `thumbnailator` + TwelveMonkeys WebP support)
  before they're referenced by entities like dogs/posts.
- Postgres is the runtime datastore (`prod` profile); H2 in-memory (`MODE=PostgreSQL`) is used for
  `local` and `test` profiles so the app/tests run without external services.

## Conventions
- Integration tests are `@SpringBootTest @AutoConfigureMockMvc` classes named
  `*ControllerIntegrationTest`, driving real HTTP flows through `MockMvc` with a real JWT obtained
  by registering a user in-test (see `registerAndGetAccessToken` helpers) rather than mocking
  security; external services (S3/R2) are stubbed with `@MockitoBean`.
- Ownership is enforced in the service layer, not just at the controller: services load the
  entity, compare its owner against `CurrentUserProvider`, and throw
  `AccessDeniedForResourceException`/`ResourceNotFoundException` before mutating.
