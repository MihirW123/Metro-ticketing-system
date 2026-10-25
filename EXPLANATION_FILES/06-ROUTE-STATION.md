# 06 — Route Station Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/RouteStation.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/RouteStationRepository.java` |
| Created/used by | `RouteController.java` |
| Table | `V1__create_metro_ticketing_schema.sql`, changed by `V6__replace_route_station_composite_key.sql` |

---

## 1. What a RouteStation represents

A `RouteStation` is **one stop on one route**, with its position.

Example for the Blue Line:

| RouteID | StationID | SequenceNumber |
|---|---|---|
| `RT-L1` | `STN-AND` | 2 |
| `RT-L1` | `STN-WEH` | 3 |
| `RT-L1` | `STN-APR` | 4 |
| `RT-L1` | `STN-MRL` | 5 |
| `RT-L1` | `STN-SKN` | 6 |

The route itself stores start/end stations. The full ordered stop list is stored here.

---

## 2. Difference between `Route`, `RouteStation` and `Station`

| Class | Meaning |
|---|---|
| `Station` | A physical metro stop, e.g. Andheri |
| `Route` | A line/path, e.g. Blue Line |
| `RouteStation` | The link “this station is stop number N on this route” |

A station can appear on many routes. A route has many stations. `RouteStation` is the join/order table.

---

## 3. Database table

Original table in V1:

```sql
CREATE TABLE "RouteStations" (
    "RouteID" VARCHAR(50) NOT NULL,
    "StationID" VARCHAR(50) NOT NULL,
    "SequenceNumber" INTEGER NOT NULL,
    PRIMARY KEY ("RouteID", "StationID"),
    CONSTRAINT "FK_RouteStations_Route"
        FOREIGN KEY ("RouteID") REFERENCES "Route" ("RouteID"),
    CONSTRAINT "FK_RouteStations_Station"
        FOREIGN KEY ("StationID") REFERENCES "Stations" ("StationID")
);
```

V6 changed the primary key:

```sql
ALTER TABLE "RouteStations"
    ADD COLUMN "RouteStationID" VARCHAR(50);

UPDATE "RouteStations"
SET "RouteStationID" = 'RS-' || "RouteID" || '-' || "StationID"
WHERE "RouteStationID" IS NULL;

ALTER TABLE "RouteStations"
    ALTER COLUMN "RouteStationID" SET NOT NULL;

ALTER TABLE "RouteStations"
    DROP CONSTRAINT "RouteStations_pkey";

ALTER TABLE "RouteStations"
    ADD CONSTRAINT "RouteStations_pkey"
    PRIMARY KEY ("RouteStationID");

ALTER TABLE "RouteStations"
    ADD CONSTRAINT "uq_route_station"
    UNIQUE ("RouteID", "StationID");
```

Final columns:

| Column | Meaning |
|---|---|
| `RouteStationID` | Primary key, `RS-<uuid>` |
| `RouteID` | FK to `Route` |
| `StationID` | FK to `Stations` |
| `SequenceNumber` | Stop order, starting at 1 |

---

## 4. Why the composite key was replaced

Originally the primary key was `(RouteID, StationID)`.

That required a separate `@Embeddable` class `RouteStationId` with `equals()` and `hashCode()`. It worked, but it was harder to explain.

V6 replaced it with:

- a single generated `RouteStationID` primary key
- a unique constraint on `(RouteID, StationID)`

The unique constraint keeps the original business rule: **one station cannot appear twice on the same route**.

This removed the extra key class and made the entity easier to understand.

---

## 5. Entity walkthrough

```java
@Entity
@Table(name = "\"RouteStations\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RouteStation {

    @Id
    @Column(name = "\"RouteStationID\"", length = 50)
    private String routeStationId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"RouteID\"")
    private Route route;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"StationID\"")
    private Station station;

    @Column(name = "\"SequenceNumber\"", nullable = false)
    private Integer sequenceNumber;
}
```

### ID

```java
@Id
@Column(name = "\"RouteStationID\"", length = 50)
private String routeStationId;
```

Generated in `RouteController`:

```java
routeStation.setRouteStationId("RS-" + UUID.randomUUID());
```

### Route relation

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "\"RouteID\"")
private Route route;
```

Many stop rows belong to one route. `READ_ONLY` prevents request JSON from replacing the route with a nested object.

### Station relation

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "\"StationID\"")
private Station station;
```

Many stop rows can reference one station.

### Sequence

```java
@Column(name = "\"SequenceNumber\"", nullable = false)
private Integer sequenceNumber;
```

- starts at 1
- increasing order
- determines the order returned by the repository
- is not unique, because different routes have their own sequences; even within a route the controller keeps it unique by using an incrementing counter

---

## 6. Repository

```java
@Repository
public interface RouteStationRepository extends JpaRepository<RouteStation, String> {

    List<RouteStation> findByRoute_RouteIdOrderBySequenceNumberAsc(String routeId);

    void deleteByRoute_RouteId(String routeId);
}
```

### `findByRoute_RouteIdOrderBySequenceNumberAsc`

Derived query:

```sql
SELECT * FROM "RouteStations"
WHERE "RouteID" = ?
ORDER BY "SequenceNumber" ASC
```

The underscore in `Route_RouteId` means:

```text
RouteStation.route -> Route.routeId
```

### `deleteByRoute_RouteId`

Derived delete:

```sql
DELETE FROM "RouteStations" WHERE "RouteID" = ?
```

Used when updating or deleting a route.

---

## 7. Where it is used

Only `RouteController` uses this group directly.

### Create route

```java
saveRouteStations(route, request.getStationIds());
```

For each station ID:

```java
RouteStation routeStation = new RouteStation();
routeStation.setRouteStationId("RS-" + UUID.randomUUID());
routeStation.setRoute(route);
routeStation.setStation(station);
routeStation.setSequenceNumber(sequenceNumber++);
routeStationRepository.save(routeStation);
```

### Update route

```java
routeStationRepository.deleteByRoute_RouteId(id);
saveRouteStations(route, request.getStationIds());
```

The old list is replaced.

### Read route

```java
List<RouteStation> stops =
    routeStationRepository.findByRoute_RouteIdOrderBySequenceNumberAsc(routeId);
```

Then the controller converts it to a list of station IDs.

### Delete route

```java
routeStationRepository.deleteByRoute_RouteId(id);
routeRepository.deleteById(id);
```

Child rows are deleted first.

---

## 8. Why the API does not expose `/route-stations`

There is no separate controller for route stops. They are managed as part of a route:

- `POST /api/routes` with `stationIds`
- `PUT /api/routes/{id}` with `stationIds`
- `GET /api/routes/{id}` returns `stationIds`
- `DELETE /api/routes/{id}` removes the stops

This keeps the API simpler: one route operation manages both the route and its stops in one transaction.

---

## 9. Request example

```json
{
  "routeName": "Blue Line",
  "totalDistance": 11.10,
  "estimatedTime": 21,
  "lineColor": "Blue",
  "startStationId": "STN-VSV",
  "endStationId": "STN-GHP",
  "stationIds": ["STN-VSV", "STN-AND", "STN-WEH", "STN-GHP"]
}
```

Resulting rows:

| RouteStationID | RouteID | StationID | SequenceNumber |
|---|---|---|---|
| `RS-<uuid>` | `RT-<uuid>` | `STN-VSV` | 1 |
| `RS-<uuid>` | `RT-<uuid>` | `STN-AND` | 2 |
| `RS-<uuid>` | `RT-<uuid>` | `STN-WEH` | 3 |
| `RS-<uuid>` | `RT-<uuid>` | `STN-GHP` | 4 |

If `stationIds` is omitted, the controller stores `[start, end]`.

---

## 10. Why the unique constraint matters

Without the unique constraint, the same station could appear twice on one route:

```text
Route RT-L1, Station STN-AND, Sequence 2
Route RT-L1, Station STN-AND, Sequence 7
```

That would be invalid. V6 adds:

```sql
UNIQUE ("RouteID", "StationID")
```

The database rejects duplicates, even if a controller bug tries to insert them.

---

## 11. Seed data

`DataSeeder.route(...)` inserts intermediate stops only:

```java
int seq = 2;
for (String stationId : intermediateIds) {
    RouteStation rs = new RouteStation();
    rs.setRouteStationId("RS-" + UUID.randomUUID());
    rs.setRoute(route);
    rs.setStation(stationRepository.findById(stationId).orElseThrow());
    rs.setSequenceNumber(seq++);
    routeStationRepository.save(rs);
}
```

For the Blue Line, rows are:

| Station | Sequence |
|---|---|
| `STN-AND` | 2 |
| `STN-WEH` | 3 |
| `STN-APR` | 4 |
| `STN-MRL` | 5 |
| `STN-SKN` | 6 |

Start/end are stored in `Route`, not in `RouteStations` for seeded data. New routes created through the API can store start/end too if they are included in `stationIds`.

---

## 12. Errors

| Situation | Status | Message |
|---|---|---|
| Unknown station in list | 400 | `Station not found: <id>` |
| Duplicate station on same route | 500 (DB unique violation) | generic error |
| Unknown route during update | 400 | `Route not found` |
| Delete stop directly | no endpoint | — |

---

## 13. Swagger test

1. Authorize as admin.
2. Create a route with `stationIds`.
3. Read the route and check the order.
4. Update the route with a different list.
5. Read again: old stops are gone.
6. Delete the route: stops are deleted first.

---

## 14. Viva questions

**Q: Why is `RouteStation` a separate table instead of a list column on `Route`?**
A: Relational databases do not store ordered lists in a single column. A join table with a sequence number is the correct normalized design.

**Q: Why does it have its own ID now?**
A: V6 replaced the composite key with a single generated `RS-<uuid>` key to simplify the entity. The unique constraint keeps the old rule.

**Q: Why is `(RouteID, StationID)` unique?**
A: A station should appear only once on a given route.

**Q: Why is `SequenceNumber` not unique?**
A: Different routes both have stop 1, 2, 3. Uniqueness is per route, not global.

**Q: How is order guaranteed?**
A: The repository uses `OrderBySequenceNumberAsc`.

**Q: Why does `RouteController` delete stops before updating?**
A: It replaces the complete stop list. Deleting first avoids stale rows and duplicate keys.

**Q: Why is `route` read-only in JSON?**
A: Clients send station IDs through `Route.stationIds`; they do not send nested `Route` objects.

**Q: Why is `station` read-only in JSON?**
A: The controller resolves the station from the ID. A client should not be able to overwrite the relation with arbitrary nested data.

**Q: Can a station belong to multiple routes?**
A: Yes. `RouteStation` has many rows for the same `StationID` with different `RouteID` values.

**Q: What happens if I send the same station twice?**
A: The unique constraint rejects the second row and the transaction rolls back.

**Q: Why does the API return `stationIds` instead of full `RouteStation` objects?**
A: It is shorter and easier to consume. The full stop information is still in the database.

**Q: Which entity owns the relationship?**
A: `RouteStation` owns the foreign keys `RouteID` and `StationID`.

---

## 15. One-line summary

`RouteStation` is the ordered join between a route and a station: it stores “this station is stop N on this route”, with a generated ID and a database rule that prevents duplicate stops on the same route.
