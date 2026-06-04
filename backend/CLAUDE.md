# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo layout

This is a multi-module Spring Boot microservices project for a ride-sharing app. Each module is an independent Maven project (no parent POM at the root) with its own `mvnw`/`pom.xml`:

- `config-server/` — Spring Cloud Config Server (port **8888**, profile `native`, serves YAML from `src/main/resources/config/`).
- `api-gateway/` — Spring Cloud Gateway on **WebFlux** (port **8080**). The single public entry point.
- `user-services/` — Auth, registration, email verification, JWT issuance (port **8001**, DB `user_db`). Uses Spring **WebMVC**. Note the artifact is `user-services` but the Spring `application.name` is `user-service`.
- `profile-service/` — Driver/passenger profile CRUD (port **8002**, DB `profile_db`). WebMVC.
- `rides-service/` — Ride creation/booking. **Skeleton only** — controller method body is empty, service is empty, no `application.yaml` for the config-server import. Do not assume it runs.
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

`user-services` is configured with `spring.jpa.hibernate.ddl-auto: create` — **the schema is dropped and recreated on every restart**. `profile-service` uses `update`. If you change a `user-services` entity, expect data loss on restart; if you change a `profile-service` entity, expect possible silent schema drift.

## Spring Boot version

All services use **Spring Boot 4.0.x** (config-server 4.0.0, api-gateway/profile-service 4.0.1, user-services 4.0.3, rides-service 4.0.5) with Spring Cloud `2025.1.x`. Java 17. Note: Boot 4 uses the new `spring-boot-starter-webmvc` artifact (not `spring-boot-starter-web`), and the gateway uses `spring-cloud-starter-gateway-server-webflux`.

## Lombok + MapStruct

`user-services` and `profile-service` use both Lombok and MapStruct as annotation processors. The `maven-compiler-plugin` configures them via `annotationProcessorPaths` — **do not** also list them as compile-scope deps without `<optional>true</optional>`, or Lombok will leak into the runtime jar. The `spring-boot-maven-plugin` already excludes Lombok from the repackaged jar.

`rides-service` has Lombok but no MapStruct.