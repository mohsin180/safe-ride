-- PostGIS setup for the passenger "nearest available rides" search.
-- Run ONCE against the rides-service database (ride_db) — in Neon, pick
-- "ride_db" in the SQL Editor's database selector first.
--
-- Prereq: the `ride` table must already exist (start rides-service once so
-- Hibernate creates it, or run this after it's been created). Safe to re-run.

-- 1. Enable PostGIS (per-database; must be run in ride_db).
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. A geography point derived from the existing pickup_long / pickup_lat.
--    Unmapped in the entity, so Hibernate ignores it and never drops it.
ALTER TABLE ride ADD COLUMN IF NOT EXISTS pickup_geog geography(Point, 4326);

-- 3. Keep it in sync on every insert / pickup change (trigger is version-safe
--    vs a GENERATED column, whose geography cast isn't immutable everywhere).
CREATE OR REPLACE FUNCTION ride_set_pickup_geog() RETURNS trigger AS $$
BEGIN
  NEW.pickup_geog := ST_SetSRID(ST_MakePoint(NEW.pickup_long, NEW.pickup_lat), 4326)::geography;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ride_pickup_geog ON ride;
CREATE TRIGGER trg_ride_pickup_geog
  BEFORE INSERT OR UPDATE OF pickup_lat, pickup_long ON ride
  FOR EACH ROW EXECUTE FUNCTION ride_set_pickup_geog();

-- 4. Backfill existing rows (the trigger only fires on new writes).
UPDATE ride
   SET pickup_geog = ST_SetSRID(ST_MakePoint(pickup_long, pickup_lat), 4326)::geography
 WHERE pickup_geog IS NULL;

-- 5. The GiST index that makes "nearby" an index lookup instead of a scan
--    (used by both ST_DWithin and the <-> KNN ordering).
CREATE INDEX IF NOT EXISTS idx_ride_pickup_geog ON ride USING GIST (pickup_geog);
