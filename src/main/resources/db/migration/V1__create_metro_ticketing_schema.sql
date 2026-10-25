CREATE TABLE IF NOT EXISTS "Stations" (
    "StationID" VARCHAR(50) PRIMARY KEY,
    "StationCode" VARCHAR(20) NOT NULL,
    "Latitude" DOUBLE PRECISION,
    "Longitude" DOUBLE PRECISION,
    "Address" VARCHAR(255),
    "OpenedDate" DATE,
    "LineColor" VARCHAR(50),
    "isActive" BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS "Trains" (
    "TrainID" VARCHAR(50) PRIMARY KEY,
    "TrainNumber" VARCHAR(50) NOT NULL,
    "Capacity" INTEGER,
    "TotalCoaches" INTEGER,
    "ManufactureYear" INTEGER,
    "CreatedAt" TIMESTAMP,
    "isActive" BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS "User" (
    "UserId" VARCHAR(50) PRIMARY KEY,
    "FirstName" VARCHAR(100) NOT NULL,
    "LastName" VARCHAR(100) NOT NULL,
    "Password" VARCHAR(255) NOT NULL,
    "Role" VARCHAR(50) NOT NULL,
    "Contact" VARCHAR(50),
    "RegisterationDate" DATE,
    "isActive" BOOLEAN DEFAULT TRUE,
    "HireDate" DATE,
    "StationID" VARCHAR(50),
    CONSTRAINT "FK_User_Station" FOREIGN KEY ("StationID") REFERENCES "Stations" ("StationID")
);

CREATE TABLE IF NOT EXISTS "Route" (
    "RouteID" VARCHAR(50) PRIMARY KEY,
    "RouteName" VARCHAR(150) NOT NULL,
    "TotalDistance" NUMERIC(10,2),
    "EstimatedTime" INTEGER,
    "LineColor" VARCHAR(50),
    "CreatedAt" TIMESTAMP,
    "UpdatedAt" TIMESTAMP,
    "StartStationID" VARCHAR(50),
    "EndStationID" VARCHAR(50),
    CONSTRAINT "FK_Route_StartStation" FOREIGN KEY ("StartStationID") REFERENCES "Stations" ("StationID"),
    CONSTRAINT "FK_Route_EndStation" FOREIGN KEY ("EndStationID") REFERENCES "Stations" ("StationID")
);

CREATE TABLE IF NOT EXISTS "RouteStations" (
    "RouteID" VARCHAR(50) NOT NULL,
    "StationID" VARCHAR(50) NOT NULL,
    "SequenceNumber" INTEGER NOT NULL,
    PRIMARY KEY ("RouteID", "StationID"),
    CONSTRAINT "FK_RouteStations_Route" FOREIGN KEY ("RouteID") REFERENCES "Route" ("RouteID"),
    CONSTRAINT "FK_RouteStations_Station" FOREIGN KEY ("StationID") REFERENCES "Stations" ("StationID")
);

CREATE TABLE IF NOT EXISTS "Schedule" (
    "ScheduleID" VARCHAR(50) PRIMARY KEY,
    "TrainID" VARCHAR(50) NOT NULL,
    "RouteID" VARCHAR(50) NOT NULL,
    "DayOfWeek" VARCHAR(20),
    "ScheduledDeparture" TIMESTAMP,
    "ScheduledArrival" TIMESTAMP,
    "ValidFrom" DATE,
    "ValidTo" DATE,
    "isActive" BOOLEAN DEFAULT TRUE,
    "CreatedAt" TIMESTAMP,
    CONSTRAINT "FK_Schedule_Train" FOREIGN KEY ("TrainID") REFERENCES "Trains" ("TrainID"),
    CONSTRAINT "FK_Schedule_Route" FOREIGN KEY ("RouteID") REFERENCES "Route" ("RouteID")
);

CREATE TABLE IF NOT EXISTS "Fares" (
    "FareID" VARCHAR(50) PRIMARY KEY,
    "SourceStationID" VARCHAR(50) NOT NULL,
    "DestStationID" VARCHAR(50) NOT NULL,
    "BaseFare" NUMERIC(10,2) NOT NULL,
    CONSTRAINT "FK_Fare_SourceStation" FOREIGN KEY ("SourceStationID") REFERENCES "Stations" ("StationID"),
    CONSTRAINT "FK_Fare_DestStation" FOREIGN KEY ("DestStationID") REFERENCES "Stations" ("StationID")
);

CREATE TABLE IF NOT EXISTS "Tickets" (
    "TicketID" VARCHAR(50) PRIMARY KEY,
    "PassengerID" VARCHAR(50) NOT NULL,
    "FareID" VARCHAR(50) NOT NULL,
    "SourceStationID" VARCHAR(50) NOT NULL,
    "DestStationID" VARCHAR(50) NOT NULL,
    "TicketType" VARCHAR(50),
    "ValidUntil" TIMESTAMP,
    "isUsed" BOOLEAN DEFAULT FALSE,
    "IssueTime" TIMESTAMP,
    "CreatedAt" TIMESTAMP,
    CONSTRAINT "FK_Ticket_Passenger" FOREIGN KEY ("PassengerID") REFERENCES "User" ("UserId"),
    CONSTRAINT "FK_Ticket_Fare" FOREIGN KEY ("FareID") REFERENCES "Fares" ("FareID"),
    CONSTRAINT "FK_Ticket_SourceStation" FOREIGN KEY ("SourceStationID") REFERENCES "Stations" ("StationID"),
    CONSTRAINT "FK_Ticket_DestStation" FOREIGN KEY ("DestStationID") REFERENCES "Stations" ("StationID")
);

CREATE TABLE IF NOT EXISTS "MetroCards" (
    "CardID" VARCHAR(50) PRIMARY KEY,
    "PassengerID" VARCHAR(50) NOT NULL,
    "Balance" NUMERIC(10,2) DEFAULT 0,
    "IssueDate" DATE,
    "ExpiryDate" DATE,
    "CreatedAt" TIMESTAMP,
    "isActive" BOOLEAN DEFAULT TRUE,
    CONSTRAINT "FK_MetroCard_Passenger" FOREIGN KEY ("PassengerID") REFERENCES "User" ("UserId")
);

CREATE TABLE IF NOT EXISTS "Payment" (
    "PaymentID" VARCHAR(50) PRIMARY KEY,
    "TicketID" VARCHAR(50) NOT NULL,
    "Amount" NUMERIC(10,2) NOT NULL,
    "CreatedAt" TIMESTAMP,
    CONSTRAINT "FK_Payment_Ticket" FOREIGN KEY ("TicketID") REFERENCES "Tickets" ("TicketID")
);

CREATE TABLE IF NOT EXISTS "MaintenanceIssue" (
    "IssueID" VARCHAR(50) PRIMARY KEY,
    "TrainID" VARCHAR(50),
    "StationID" VARCHAR(50),
    "ReportedId" VARCHAR(50),
    "IssueType" VARCHAR(100),
    "Description" TEXT,
    "Status" VARCHAR(50) DEFAULT 'Open',
    "Priority" VARCHAR(20),
    "CreatedAt" TIMESTAMP,
    "ResolvedAt" TIMESTAMP,
    CONSTRAINT "FK_Maintenance_Train" FOREIGN KEY ("TrainID") REFERENCES "Trains" ("TrainID"),
    CONSTRAINT "FK_Maintenance_Station" FOREIGN KEY ("StationID") REFERENCES "Stations" ("StationID"),
    CONSTRAINT "FK_Maintenance_Reporter" FOREIGN KEY ("ReportedId") REFERENCES "User" ("UserId")
);

CREATE UNIQUE INDEX IF NOT EXISTS "UX_User_Contact" ON "User" ("Contact");
CREATE UNIQUE INDEX IF NOT EXISTS "UX_Station_Code" ON "Stations" ("StationCode");
CREATE UNIQUE INDEX IF NOT EXISTS "UX_Train_Number" ON "Trains" ("TrainNumber");
