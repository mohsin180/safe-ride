---
name: rides-spec-entity-naming
description: rides-service task specs use idealized field names that differ from the real entities — translation map
metadata:
  type: project
---

Backend task specs for `rides-service` are written against an idealized schema whose names do NOT match the actual JPA entities. Always translate before implementing.

**Why:** The user pastes controller/service/repo snippets from a design doc; copying them verbatim breaks compilation and semantics.

**How to apply — translation map:**
- `ride.passengerId` → `ride.createdByUserId` (the host/creator field).
- `ride.getSeats()` → `ride.getTotalSeats()`; `ride.getJoinedCount()` → count of participants.
- `ride.getDrop()` (String) → `ride.getDestination()` which is an `@Embedded Location` (`getAddress()`/`getLatitude()`/`getLongitude()`); same for `getPickup()`.
- DTO `drop` ← `destination.address`.
- Entity `RideParticipant` with a `rideId` field → real entity is `RideParticipants` (plural) with a `@ManyToOne ride` relationship + `UUID userId` + `LocalDateTime joinedAt` (`@CreationTimestamp`, auto-set, no builder). Derived queries traverse the relationship: `countByRide_Id`, `existsByRide_IdAndUserId`, `deleteByRide_IdAndUserId`.
- Spec uses `OffsetDateTime.now()`; entity timestamps are `Instant` (`createdAt`, `cancelledAt`) — use `Instant.now()`.
- Exceptions referenced by specs (`ForbiddenException`→403, `ConflictException`→409) were added to `exceptions/` with handlers in `GlobalExceptionHandler`; `RoleNotAllowedException`→403 and `NotFoundException`→404 already existed. All return `ErrorResponse` JSON `{"message": "..."}`.

**Seats model:** there is a stored `availableSeats` counter that `findAvailableRides` (filters `> 0`), `getAvailableRides`, and `getMyRides` depend on — keep it in sync (decrement on join, increment on leave) rather than removing it, even though specs say "compute on the fly." `getRideDetails` independently derives `seatsAvailable = totalSeats - participants.size()`, so both stay consistent.

Run/build via Maven (`.\mvnw.cmd spring-boot:run`), NOT the VS Code Run button — MapStruct's annotation processor only runs under Maven, else `RideMapper` bean is missing. See [[mapstruct-requires-maven-run]].
