CREATE UNIQUE INDEX IF NOT EXISTS "UX_Fare_Station_Pair"
    ON "Fares" ("SourceStationID", "DestStationID");
