-- Replace the composite (RouteID, StationID) primary key with a single generated ID.
-- The unique constraint keeps the original rule: one station per route.
ALTER TABLE "RouteStations" ADD COLUMN IF NOT EXISTS "RouteStationID" VARCHAR(50);

UPDATE "RouteStations"
SET "RouteStationID" = 'RS-' || "RouteID" || '-' || "StationID"
WHERE "RouteStationID" IS NULL;

ALTER TABLE "RouteStations" ALTER COLUMN "RouteStationID" SET NOT NULL;

ALTER TABLE "RouteStations" DROP CONSTRAINT IF EXISTS "RouteStations_pkey";
ALTER TABLE "RouteStations" ADD CONSTRAINT "RouteStations_pkey" PRIMARY KEY ("RouteStationID");

ALTER TABLE "RouteStations" DROP CONSTRAINT IF EXISTS "uq_route_station";
ALTER TABLE "RouteStations" ADD CONSTRAINT "uq_route_station" UNIQUE ("RouteID", "StationID");
