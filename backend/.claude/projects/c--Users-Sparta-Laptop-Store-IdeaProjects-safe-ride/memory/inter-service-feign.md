---
name: inter-service-feign
description: Use Spring Cloud OpenFeign for service-to-service calls in this backend; how rides→profile name resolution is wired
metadata:
  type: feedback
---

The user prefers **Spring Cloud OpenFeign** for inter-service calls in the SafeRide backend (asked for it explicitly when wiring host/co-passenger names).

**Why:** consistent, declarative client style they want to standardize on.

**How to apply:**
- rides-service resolves passenger display names + ratings via `ProfileClient` (`@FeignClient(name="profile-service", url="${profile.service.url:http://localhost:8002}")`) calling `POST /api/v1/profile/internal/passengers/by-ids` (batch, body = `List<UUID>`).
- That internal endpoint lives in profile-service `InternalProfileController` and is `permitAll()` in its `SecurityConfig` — internal callers hit profile-service directly, so they carry no gateway `X-User-*` headers. Hardening TODO: gateway should refuse to route `/api/v1/profile/internal/**` from the public side.
- Hosts and co-passengers are **always passengers** (both `createRide` and `joinRide` are `@PreAuthorize("hasRole('PASSENGER')")`), so only passenger profiles need resolving.
- Name/rating resolution is best-effort: if profile-service errors, `RideService.fetchPassengerSummaries` returns an empty map and callers fall back to `User <id-prefix>`. Rating of 0.0 is treated as "unrated" → null.
- `@EnableFeignClients` is on `RidesServiceApplication`; dep is `spring-cloud-starter-openfeign`.

Related: [[rides-spec-entity-naming]]
