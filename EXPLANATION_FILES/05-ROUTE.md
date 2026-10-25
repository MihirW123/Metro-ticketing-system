# 05 — Route Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/Route.java` |
| Controller | `src/main/java/com/metro/ticketing/controller/RouteController.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/RouteRepository.java` |
| Route stop repository | `src/main/java/com/metro/ticketing/repository/RouteStationRepository.java` |
| Table | `V1__create_metro_ticketing_schema.sql` |
| Related | RouteStation, Station, Schedule |

---

## 1. What a Route represents

A route is a named metro line, for example:

> Mumbai Metro Line 1 — Blue (Versova to Ghatkopar)

It stores:

- route ID
- route name
- total distance
- estimated time
- line colour
- created/updated timestamps
- start station
- end station
- optionally, an ordered list of station IDs

The ordered stop list is stored in the `RouteStations` table. `Route` only stores the start and end station plus route metadata.

---

## 2. Database table

```sql
CREATE TABLE "Route" (
    "RouteID" VARCHAR(50) PRIMARY KEY,
    "RouteName" VARCHAR(150) NOT NULL,
    "TotalDistance" NUMERIC(10,2),
    "EstimatedTime" INTEGER,
    "LineColor" VARCHAR(50),
    "CreatedAt" TIMESTAMP,
    "UpdatedAt" TIMESTAMP,
    "StartStationID" VARCHAR(50),
    "EndStationID" VARCHAR(50),
    CONSTRAINT "FK_Route_StartStation"
        FOREIGN KEY ("StartStationID") REFERENCES "Stations" ("StationID"),
    CONSTRAINT "FK_Route_EndStation"
        FOREIGN KEY ("EndStationID") REFERENCES "Stations" ("StationID")
);
```

| Column | Meaning |
|---|---|
| `RouteID` | Primary key, `RT-<uuid>` |
| `RouteName` | Display name |
| `TotalDistance` | Distance, `BigDecimal` |
| `EstimatedTime` | Minutes |
| `LineColor` | Blue / Aqua / Red / Green |
| `CreatedAt` | Creation time |
| `UpdatedAt` | Last update time |
| `StartStationID` | FK to `Stations` |
| `EndStationID` | FK to `Stations` |

Start and end are nullable in the schema, but the controller requires them in the request.

---

## 3. Entity walkthrough

```java
@Entity
@Table(name = "\"Route\"")
public class Route {

    @Id
    private String routeId;

    @NotBlank(message = "Route name is required")
    private String routeName;

    private BigDecimal totalDistance;
    private Integer estimatedTime;
    private String lineColor;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    private Station startStation;

    @ManyToOne(fetch = FetchType.LAZY)
    private Station endStation;
}
```

### Relations

```java
@JsonProperty(access = JsonProperty.Access.READ_ONLY)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "\"StartStationID\"", referencedColumnName = "\"StationID\"")
private Station startStation;
```

- `startStation` and `endStation` are real relations.
- `READ_ONLY` means response JSON includes them, but request JSON cannot overwrite them with nested objects.
- Requests use transient IDs instead.

### Transient request fields

```java
@Transient
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String startStationId;

@Transient
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String endStationId;

@Transient
private List<String> stationIds;
```

| Field | Purpose |
|---|---|
| `startStationId` | Request sends `"startStationId": "STN-VSV"` |
| `endStationId` | Request sends `"endStationId": "STN-GHP"` |
| `stationIds` | Ordered stops to store in `RouteStations` |

`stationIds` is not write-only because responses use it to show the ordered stop list.

### Why `totalDistance` is `BigDecimal`

Distance is stored as `NUMERIC(10,2)` for exact decimal values. Money also uses `BigDecimal` to avoid floating-point rounding.

---

## 4. Repository

```java
@Repository
public interface RouteRepository extends JpaRepository<Route, String> {
}
```

Standard CRUD only.

`RouteStationRepository` handles the stops:

```java
List<RouteStation> findByRoute_RouteIdOrderBySequenceNumberAsc(String routeId);
void deleteByRoute_RouteId(String routeId);
```

`RouteController` injects both repositories plus `StationRepository`.

---

## 5. Controller endpoints

Base path: `/api/routes`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/routes` | ADMIN | Create route + stops |
| PUT | `/api/routes/{id}` | ADMIN | Update route + replace stops |
| GET | `/api/routes/{id}` | authenticated | Read route + stops |
| GET | `/api/routes` | authenticated | List routes + stops |
| DELETE | `/api/routes/{id}` | ADMIN | Delete route + stops |

---

## 6. Create route — step by step

```java
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public ResponseEntity<Route> createRoute(@Valid @RequestBody Route request) {
    Route route = new Route();
    route.setRouteId("RT-" + UUID.randomUUID());
    copyRouteFields(request, route);
    route.setCreatedAt(LocalDateTime.now());
    route.setUpdatedAt(LocalDateTime.now());
    route = routeRepository.save(route);

    saveRouteStations(route, request.getStationIds());
    return ResponseEntity.status(HttpStatus.CREATED).body(loadRoute(route.getRouteId()));
}
```

### 1. Generate ID

`RT-<uuid>`.

### 2. `copyRouteFields`

```java
route.setRouteName(request.getRouteName());
route.setTotalDistance(request.getTotalDistance());
route.setEstimatedTime(request.getEstimatedTime());
route.setLineColor(request.getLineColor());

if (request.getStartStationId() == null || request.getEndStationId() == null) {
    throw new IllegalArgumentException("Start and end stations are required");
}

Station start = stationRepository.findById(request.getStartStationId())
    .orElseThrow(() -> new IllegalArgumentException("Start station not found"));
Station end = stationRepository.findById(request.getEndStationId())
    .orElseThrow(() -> new IllegalArgumentException("End station not found"));

route.setStartStation(start);
route.setEndStation(end);
```

### 3. Save route

The route row is inserted first so route stops can reference it.

### 4. `saveRouteStations`

```java
List<String> orderedStations = stationIds;
if (orderedStations == null || orderedStations.isEmpty()) {
    orderedStations = List.of(
        route.getStartStation().getStationId(),
        route.getEndStation().getStationId());
}

int sequenceNumber = 1;
for (String stationId : orderedStations) {
    Station station = stationRepository.findById(stationId)
        .orElseThrow(() -> new IllegalArgumentException("Station not found: " + stationId));

    RouteStation routeStation = new RouteStation();
    routeStation.setRouteStationId("RS-" + UUID.randomUUID());
    routeStation.setRoute(route);
    routeStation.setStation(station);
    routeStation.setSequenceNumber(sequenceNumber++);
    routeStationRepository.save(routeStation);
}
```

Rules:

- If `stationIds` is omitted, the route stores `[start, end]`.
- If provided, it stores the list in the given order.
- Sequence starts at 1.
- Each station ID must exist.
- A `RouteStation` ID is generated as `RS-<uuid>`.
- The database unique constraint `(RouteID, StationID)` prevents duplicates.

### 5. Return the loaded route

`loadRoute` re-reads the route and attaches `stationIds` from the stop table.

---

## 7. Update route — step by step

```java
@PutMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public ResponseEntity<Route> updateRoute(@PathVariable String id,
                                         @Valid @RequestBody Route request) {
    Route route = routeRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Route not found"));

    copyRouteFields(request, route);
    route.setUpdatedAt(LocalDateTime.now());
    route = routeRepository.save(route);

    routeStationRepository.deleteByRoute_RouteId(id);
    saveRouteStations(route, request.getStationIds());

    return ResponseEntity.ok(loadRoute(id));
}
```

Update replaces the stop list:

1. Update route fields and start/end.
2. Save.
3. Delete all existing `RouteStations` for this route.
4. Insert the new list.
5. Return the loaded route.

Why delete first: it avoids stale stops and duplicate `(RouteID, StationID)` conflicts.

---

## 8. Read routes

### One route

```java
@GetMapping("/{id}")
@Transactional(readOnly = true)
public ResponseEntity<Route> getRouteById(@PathVariable String id) {
    return ResponseEntity.ok(loadRoute(id));
}
```

`loadRoute`:

```java
Route route = routeRepository.findById(routeId)
    .orElseThrow(() -> new IllegalArgumentException("Route not found"));
route.setStationIds(loadStationIds(routeId));
return route;
```

### All routes

```java
List<Route> routes = routeRepository.findAll();
routes.forEach(route -> route.setStationIds(loadStationIds(route.getRouteId())));
return ResponseEntity.ok(routes);
```

For each route, stops are loaded in sequence order.

### `loadStationIds`

```java
List<String> stationIds = new ArrayList<>();
for (RouteStation routeStation :
        routeStationRepository.findByRoute_RouteIdOrderBySequenceNumberAsc(routeId)) {
    stationIds.add(routeStation.getStation().getStationId());
}
return stationIds;
```

This lazy-loads each `station` inside a transaction/OSIV, so JSON serialization is safe.

---

## 9. Delete route

```java
if (!routeRepository.existsById(id)) {
    throw new IllegalArgumentException("Route not found");
}
routeStationRepository.deleteByRoute_RouteId(id);
routeRepository.deleteById(id);
return ResponseEntity.noContent().build();
```

Stops are deleted first to satisfy the route-stop foreign key. If a schedule still references the route, the database rejects the delete.

---

## 10. Request and response examples

### Create

```http
POST /api/routes
Authorization: Basic admin:admin123
Content-Type: application/json
```

```json
{
  "routeName": "Viva Test Route",
  "totalDistance": 5.0,
  "estimatedTime": 10,
  "lineColor": "Green",
  "startStationId": "STN-VSV",
  "endStationId": "STN-GHP",
  "stationIds": ["STN-VSV", "STN-AND", "STN-GHP"]
}
```

Response `201`:

```json
{
  "routeId": "RT-<uuid>",
  "routeName": "Viva Test Route",
  "totalDistance": 5.0,
  "estimatedTime": 10,
  "lineColor": "Green",
  "createdAt": "2026-10-25T...",
  "updatedAt": "2026-10-25T...",
  "startStation": { "stationId": "STN-VSV", "...": "..." },
  "endStation": { "stationId": "STN-GHP", "...": "..." },
  "stationIds": ["STN-VSV", "STN-AND", "STN-GHP"]
}
```

### Update

```http
PUT /api/routes/RT-<uuid>
Authorization: Basic admin:admin123
```

Same body; stops are replaced.

### Read

```http
GET /api/routes/RT-L1
Authorization: Basic priya:Passw0rd!
```

The seeded routes currently store only intermediate stops (for example `STN-AND`, `STN-WEH`, ...), because `DataSeeder` inserts them that way. New routes created through the API store exactly the `stationIds` you send, or `[start, end]` if omitted.

---

## 11. Relations

### Station

`startStation` and `endStation` are `@ManyToOne` to `Station`.

### RouteStation

One route has many `RouteStation` rows. Each row has a sequence number.

### Schedule

`Schedule.route` points to a route.

Deleting a route with schedules fails due to the schedule foreign key.

---

## 12. Seed data

`DataSeeder.route(...)` creates:

| ID | Name | Start | End | Intermediates |
|---|---|---|---|---|
| `RT-L1` | Blue Line | `STN-VSV` | `STN-GHP` | AND, WEH, APR, MRL, SKN |
| `RT-L2` | Aqua Line | `STN-ARY` | `STN-CSM` | SEP, MRL, BKC, DDR, MCU |
| `RT-L3` | Red Line | `STN-DHE` | `STN-AEE` | DVP, POI, GDU |

`DataSeeder` inserts only the intermediate IDs into `RouteStations`, with sequence numbers starting at 2.

---

## 13. Errors

| Situation | Status | Message |
|---|---|---|
| Non-admin write | 403 | permission error |
| Missing route name | 400 | `routeName: Route name is required` |
| Missing start/end | 400 | `Start and end stations are required` |
| Unknown start station | 400 | `Start station not found` |
| Unknown end station | 400 | `End station not found` |
| Unknown intermediate station | 400 | `Station not found: <id>` |
| Unknown route ID | 400 | `Route not found` |
| Duplicate station in list | 500 (DB unique violation) | generic error |
| Delete with schedules | 500 (DB FK violation) | generic error |

---

## 14. Swagger test

1. Authorize as `admin/admin123`.
2. `POST /api/routes` with the example body.
3. Copy `routeId`.
4. `GET /api/routes/{id}` -> `200` and `stationIds` in order.
5. `PUT /api/routes/{id}` with a shorter list -> stops replaced.
6. `DELETE /api/routes/{id}` -> `204`.
7. As `priya`, `POST /api/routes` -> `403`.

---

## 15. Viva questions

**Q: Is a route the same as a train?**
A: No. A route is the line/path. A train is a vehicle. A schedule connects a train to a route at a time.

**Q: Where are the stops stored?**
A: In `RouteStations`, one row per stop with a sequence number.

**Q: Why does `Route` have `stationIds` if it is not a column?**
A: It is a `@Transient` response/request helper. The real rows are in `RouteStations`.

**Q: Why is `startStation` read-only in JSON?**
A: The client sends `startStationId`; the server loads the real `Station` object. This prevents nested-object overwrites.

**Q: Why generate `RT-<uuid>` instead of using the name?**
A: Names can change and are not guaranteed unique. IDs are stable.

**Q: What happens if `stationIds` is omitted?**
A: The controller stores `[start, end]`.

**Q: What happens on update?**
A: Existing route stops are deleted and re-inserted from the request.

**Q: Why delete stops before deleting the route?**
A: `RouteStations` has a foreign key to `Route`; the child rows must go first.

**Q: Why can route deletion still fail?**
A: A schedule may reference the route. The schedule FK must be handled first.

**Q: Why `BigDecimal` for distance?**
A: Exact decimal storage and no floating-point rounding.

**Q: What is `updatedAt`?**
A: A timestamp refreshed on update.

**Q: Does the API validate that start and end are different?**
A: No. It only checks that both exist. A route from a station to itself would be allowed.

---

## 16. One-line summary

`Route` is the line record (name, distance, time, start, end); its ordered stops live in `RouteStation`, and route creation/update manages both in one transaction.
