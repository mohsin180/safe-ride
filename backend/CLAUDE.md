# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## ACTIVE ARCHITECTURE: the monolith (`ride-sharing-backend/`)

**As of Aug 2026 the backend is a single Spring Boot monolith** in `ride-sharing-backend/` (Boot 4.0.6, Java 17, package `com.saferide.monolith`). The old microservice modules were moved out of this repo to `/Users/novaratech/Documents/projects/microservices/` (with their own `run.sh`, `.env` copy, and `.vscode/launch.json`); the LEGACY section below documents how they worked.

- Run it: `./run.sh` (defaults to monolith; loads `.env`). Or `cd ride-sharing-backend && ../load-env.sh`-style env + `./mvnw spring-boot:run`.
- One port **8080**, same `/api/v1/**` paths as the old gateway → the Flutter app needs no changes.
- One database `saferide_db` (env `DB_NAME`; host/creds from the same `DB_*` vars — currently **local Homebrew Postgres 16** on localhost; the old Neon values are commented in `.env`). All 23 tables from every module live here. Legacy per-service DBs (`user_db` etc.) are untouched.
- Modules are packages: `user` (auth), `profile`, `rides`, `messaging` (WebSocket chat), `notification`, `kyc`, plus `common/security`.
- `kyc` is identity verification (CNIC + liveness) via the Didit REST API, for drivers and passengers alike: `POST /api/v1/kyc/session` returns a `sessionToken` (for Didit's native Flutter SDK) plus a `verificationUrl` (browser fallback), and `GET /api/v1/kyc/status` reports the decision. `KycService` picks the driver or passenger profile from the JWT role — both entities implement `kyc/model/KycVerifiable`, whose three columns (`kycStatus`, `kycSessionId`, `kycVerifiedAt`) are nullable so `ddl-auto=update` can add them to existing rows. No webhook: the backend polls Didit's decision endpoint server-side (local dev has no public HTTPS endpoint) and stays the source of truth over any client-reported result.
- Didit config: only `DIDIT_API_KEY` is secret, but `DIDIT_WORKFLOW_ID` must be changed **with** it — workflow ids are per-environment, so a Sandbox key paired with a Live workflow id fails with "Invalid workflow_id.". The current default is the Sandbox "Free KYC" workflow; the Live environment additionally rejects sessions outright ("not enough credits") until the account has balance.
- Security: `common/security/JwtAuthFilter` validates the Bearer JWT, sets the `SecurityContext` (with `UserContext` in details) **and** rewrites `X-User-Id/-Role/-Gender/-Email` request headers from token claims — so controllers using `@RequestHeader("X-User-Id")` and the WS handshake interceptor work unchanged, and client-sent identity headers can never be spoofed. Public paths: `/api/v1/auth/**` only. There is no gateway and no config-server.
- Former Feign/RestClient inter-service calls are now in-process facades with the same class names/signatures: `rides/client/ProfileClient` + `DriverClient` and `messaging/client/RidesClient` + `ProfileClient` call the other module's repositories directly. The `/internal/**` HTTP endpoints were deleted.
- RabbitMQ is gone: `rides/event/NotificationPublisher` publishes `RideNotificationEvent` via Spring's `ApplicationEventPublisher`; `notification/listener/NotificationListener` consumes it with `@Async @EventListener` (`@EnableAsync` on `MonolithApplication`).
- The three exception advices were renamed (`UserExceptionHandler`, `ProfileExceptionHandler`, `RidesExceptionHandler`) and the two `ProfileClient` beans have explicit names (`ridesProfileClient`, `messagingProfileClient`) to avoid bean-name clashes.
- JWT signing + validation now share the single `jwt.secret` property — the old gateway/user-services secret-sync issue no longer exists.

---

## Signup: an account exists only when it is complete

**There is no such thing as a half-made account.** Everything a user types on the way in — credentials, role, profile, vehicle, KYC progress — lives in a single `pending_signup` row (`user/model/PendingSignup.java`) and nowhere else. `OnboardingService.promote()` is the **only** code in the system that inserts into `users`, and it runs at exactly one moment: the poll that first sees KYC APPROVED. In that one transaction it creates `users` + the driver/passenger profile (+ vehicle), copies the KYC verdict onto the profile, deletes the staging row and its verification tokens, and returns the account's first real JWT.

Consequences worth knowing before changing anything here:

- **The KYC gate isn't enforced, it's unreachable.** An unfinished signup never holds a session token — only the onboarding token, which carries no role and `JwtAuthFilter` rejects for every ordinary endpoint. So a pending user can't touch a ride endpoint even calling the API directly. The `KycGuard.requireVerified` calls left in the ride paths are now belt-and-braces, not the mechanism.
- **A session token means the account is finished.** Client routing depends on this: `sessionRouter.dart` returns the navbar for any valid token and asks the server for the stage otherwise. Never issue a token anywhere but `promote()`.
- **Onboarding endpoints** live under `/api/v1/auth/onboarding/**` (`OnboardingController`) so they fall inside the public `/api/v1/auth/**` matcher; each authorises itself by parsing the onboarding token from the `Authorization` header, exactly as `/select-role` always did. Every one answers with the same `OnboardingStateResponse`, whose `stage` the app routes on — it never infers the step from which lookups succeed.
- `POST /select-role` **no longer issues a token** (it used to, which is how users reached the app with no profile and no verified identity).
- **Email verification** belongs to `pending_signup`, not `users` — `EmailVerificationToken.pendingSignup`. A `users` row is verified by construction. The old `users_id` column was dropped by hand; `ddl-auto=update` won't do that for you.
- **Gender changes** during signup go to `POST /auth/onboarding/gender` (free — nothing is verified yet). `PUT /auth/gender` serves real accounts and always refuses, since by then the value carries a checked CNIC.
- `AbandonedSignupSweeper` deletes pending rows older than `app.signup.abandon-after-days` (default 7). Without it a stalled attempt would hold its email address hostage forever, since registration refuses an address present in *either* table.

---

## LEGACY: the original microservices (moved to /Users/novaratech/Documents/projects/microservices/)

The original architecture was a multi-module Spring Boot microservices project. Each module is an independent Maven project (no parent POM at the root) with its own `mvnw`/`pom.xml`:

- `config-server/` — Spring Cloud Config Server (port **8888**, profile `native`, serves YAML from `src/main/resources/config/`).
- `api-gateway/` — Spring Cloud Gateway on **WebFlux** (port **8080**). The single public entry point.
- `user-services/` — Auth, registration, email verification, JWT issuance (port **8001**, DB `user_db`). Uses Spring **WebMVC**. Note the artifact is `user-services` but the Spring `application.name` is `user-service`.
- `profile-service/` — Driver/passenger profile CRUD (port **8002**, DB `profile_db`). WebMVC.
- `rides-service/` — Core ride lifecycle (port **8003**, DB `ride_db`). **Fully implemented**: create/cancel/publish, co-passenger join → host accept/decline, driver offer → host accept/decline, per-seat fare, ratings, driver feed/status, ride history, and an `internal` controller serving chat membership to messaging-service. WebMVC.
- `docker/init.sql` + `docker-compose.yaml` — Postgres 16 + Adminer; creates `user_db`, `profile_db`, `ride_db` on first start.

There is also a stale `ride-service/` directory shown as deleted in `git status`; the active module is `rides-service/` (with the `s`).

## Common commands

All modules use the Maven wrapper. Run from the module directory:

```powershell
# Build / run a service (PowerShell on Windows)
cd user-services; .\mvnw.cmd spring-boot:run
cd profile-service; .\mvnw.cmd spring-boot:run
cd config-server; .\mvnw.cmd spring-boot:run
cd api-gateway; .\mvnw.cmd spring-boot:run

# Build a single module without running
.\mvnw.cmd clean package

# Run all tests for a module
.\mvnw.cmd test

# Run a single test class / method
.\mvnw.cmd test "-Dtest=ClassName"
.\mvnw.cmd test "-Dtest=ClassName#methodName"

# Bring up Postgres + Adminer (from repo root)
docker compose up -d
```

**Startup order matters**: `config-server` must be running before `user-services` or `profile-service`, because both import config via `optional:configserver:http://localhost:8888`. The `optional:` prefix means they will start without the config server, but they will be missing all DB / JWT / mail settings and fail at runtime. The api-gateway is self-contained (no config-server dependency).

## Architecture: how a request flows

The auth model is **gateway-centric JWT validation**. Downstream services do not validate JWTs — they trust headers injected by the gateway.

1. **Login / register** at the gateway, `POST /api/v1/auth/**` → routed to `user-services:8001` (`api-gateway/.../GatewayConfig.java`). These paths are listed in `JwtForwardingFilter.PUBLIC_PATHS` and bypass JWT checks.
2. `user-services` issues a JWT containing `userId`, `role`, and `gender` claims, signed with the HS256 key in `jwt.secret` (config-server: `user-service.yml`).
3. **Subsequent requests** hit the gateway's `JwtForwardingFilter` (order `-1`), which:
   - Validates the `Authorization: Bearer <token>` header.
   - Extracts the three claims and rewrites the request with `X-User-Id`, `X-User-Role`, `X-User-Gender` headers.
   - The original `Authorization` header is forwarded as-is. There is no stripping.
4. `RoleGenderAuthorizationFilter` (order `0`) does coarse role gating per path prefix. **It is defined as a class but is not registered as a `@Component` or `@Bean`** — so it is currently inert. Real authorization happens downstream.
5. Downstream services use `GatewayAuthFilter` (e.g. `profile-service/.../config/GatewayAuthFilter.java`) which reads the three `X-User-*` headers, builds a `UsernamePasswordAuthenticationToken` with `ROLE_<role>` authority, and stashes a `UserContext` record in `Authentication.getDetails()`. Method-level `@PreAuthorize("hasRole('PASSENGER')")` then enforces fine-grained access.
6. To get the current user inside a service: read `SecurityContextHolder.getContext().getAuthentication().getDetails()` and cast to `UserContext` — see `PassengerProfileService.getCurrentUserContext()`.

**Trust boundary**: any service that listens on its own port and is reachable bypassing the gateway can be spoofed by a client setting the `X-User-*` headers directly. Treat the gateway as the only legitimate caller of downstream services in non-local environments.

## JWT secret

The same base64 HS256 secret is duplicated in `api-gateway/src/main/resources/application.yaml` (`jwt.secret`) and `config-server/src/main/resources/config/user-service.yml`. Both must match for tokens issued by user-services to validate at the gateway. The `api-gateway` does not pull from the config server, so keep them in sync manually.

## Auth state machine in user-services

A user goes through three gates in `UserService.login()` before getting a token: `enabled`, `isEmailVerified`, `role != null`. New registrations land with `enabled=true`, `isEmailVerified=false`, `role=null`, so:

1. Register → email sent via `EmailVerificationService` (Gmail SMTP creds in `user-service.yml`).
2. User clicks link → `GET /api/v1/auth/verify-email?token=<raw>` → token is hashed and matched.
3. Login still fails because `role == null` → client calls `POST /api/v1/auth/{id}/select-role` with `DRIVER` or `PASSENGER` → returns a JWT.
4. Subsequent logins return a JWT directly.

`HashedVerificationToken` stores SHA-256 hashes only; raw tokens are never persisted. `resendVerificationEmail` invalidates prior unused tokens before issuing a new one.

## DDL behavior

All services default to `spring.jpa.hibernate.ddl-auto: ${JPA_DDL_AUTO:update}` (see each `config-server/src/main/resources/config/*.y*ml`), and the backend `.env` sets `JPA_DDL_AUTO=update`. So the schema is **updated, not recreated**, on restart — data is preserved. If you change an entity, expect possible silent schema drift rather than data loss. (Set `JPA_DDL_AUTO=create`/`create-drop` only when you intentionally want a clean schema.)

## Spring Boot version

All services use **Spring Boot 4.0.x** (config-server 4.0.0, api-gateway/profile-service 4.0.1, user-services 4.0.3, rides-service 4.0.5) with Spring Cloud `2025.1.x`. Java 17. Note: Boot 4 uses the new `spring-boot-starter-webmvc` artifact (not `spring-boot-starter-web`), and the gateway uses `spring-cloud-starter-gateway-server-webflux`.

## Lombok + MapStruct

`user-services` and `profile-service` use both Lombok and MapStruct as annotation processors. The `maven-compiler-plugin` configures them via `annotationProcessorPaths` — **do not** also list them as compile-scope deps without `<optional>true</optional>`, or Lombok will leak into the runtime jar. The `spring-boot-maven-plugin` already excludes Lombok from the repackaged jar.

`rides-service` has Lombok but no MapStruct.