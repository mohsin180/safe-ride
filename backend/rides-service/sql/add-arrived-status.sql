-- Add the ARRIVED ride status (driver reached the pickup point).
-- Run ONCE against the rides-service database (ride_db) — in Neon, pick
-- "ride_db" in the SQL Editor's database selector first.
--
-- WHY THIS IS NEEDED: Hibernate generated a CHECK constraint
-- (`ride_status_check`) enumerating the RideStatus values when the `ride`
-- table was first created. With `ddl-auto=update` Hibernate does NOT alter
-- an existing check constraint, so after adding ARRIVED to the enum the
-- database still rejects it:
--     new row for relation "ride" violates check constraint "ride_status_check"
-- This migration rewrites the constraint to include ARRIVED. Apply it to
-- every environment (dev / staging / prod) alongside the deploy that adds
-- the ARRIVED status. Safe to re-run.

ALTER TABLE ride DROP CONSTRAINT IF EXISTS ride_status_check;

ALTER TABLE ride ADD CONSTRAINT ride_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'ARRIVED', 'STARTED', 'COMPLETED', 'CANCELLED'));
