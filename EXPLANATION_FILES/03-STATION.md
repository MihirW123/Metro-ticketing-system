# 03 — Station Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/Station.java` |
| Controller | `src/main/java/com/metro/ticketing/controller/StationController.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/StationRepository.java` |
| Table | `V1__create_metro_ticketing_schema.sql` |
| Used by | Route, RouteStation, Fare, Ticket, User, MaintenanceIssue |

---

## 1. What a Station represents

A station is a physical metro stop. It has:

- a system ID
- a short station code
- a name stored inside the address field
- latitude and longitude
- an opening date
- a line colour
- an active flag

Stations are the most connected entity in the project. Routes start and end at stations, fares are priced between two stations, tickets reference source and destination stations, staff can be assigned to a station, and maintenance issues can belong to a station.

Because of this, stations are created first in the demo flow.

---

## 2. Database table

```sql
CREATE TABLE "Stations" (
    "StationID" VARCHAR(50) PRIMARY KEY,
    "StationCode" VARCHAR(20) NOT NULL,
    "Latitude" DOUBLE PRECISION,
    "Longitude" DOUBLE PRECISION,
    "Address" VARCHAR(255),
    "OpenedDate" DATE,
    "LineColor" VARCHAR(50),
    "isActive" BOOLEAN DEFAULT TRUE
);

CREATE UNIQUE INDEX "UX_Station_Code" ON "Stations" ("StationCode");
```

| Column | Meaning |
|---|---|
| `StationID` | Primary key, generated as `STN-<uuid>` |
| `StationCode` | Short code such as `VSV`, `AND`, `GHP`; unique |
| `Latitude` | GPS latitude |
| `Longitude` | GPS longitude |
| `Address` | Address; demo data also embeds the station name here |
| `OpenedDate` | Date the station opened |
| `LineColor` | Blue / Aqua / Red / Interchange |
| `isActive` | Soft status flag |

The unique index prevents two stations from sharing the same code.

---

## 3. Entity walkthrough

```java
@Entity
@Table(name = "\"Stations\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Station {

    @Id
    @Column(name = "\"StationID\"", length = 50)
    private String stationId;

    @NotBlank(message = "Station code is required")
    @Column(name = "\"StationCode\"", length = 20, nullable = false)
    private String stationCode;

    @Column(name = "\"Latitude\"")
    private Double latitude;

    @Column(name = "\"Longitude\"")
    private Double longitude;

    @Column(name = "\"Address\"")
    private String address;

    @Column(name = "\"OpenedDate\"")
    private LocalDate openedDate;

    @Column(name = "\"LineColor\"", length = 50)
    private String lineColor;

    @Column(name = "\"isActive\"")
    private Boolean isActive = true;
}
```

### Annotations

- `@Entity` — maps the class to a table.
- `@Table(name = "\"Stations\"")` — exact quoted table name.
- `@Id` — primary key.
- `@NotBlank` — station code is required.
- `@Column(nullable = false)` — database also enforces it.
- `@JsonIgnoreProperties` — hides Hibernate proxy details.

### Why `Double` for coordinates

`Double` maps to PostgreSQL `DOUBLE PRECISION`, which is enough for latitude/longitude. `BigDecimal` is used for money, not coordinates.

### Why `isActive`

It allows a station to be marked inactive without deleting it. Deleting a station used by other tables would break foreign keys.

### Default values

`isActive = true` in the field initializer. If the request does not send it, the entity starts active.

---

## 4. Repository

```java
@Repository
public interface StationRepository extends JpaRepository<Station, String> {
}
```

No custom methods. The controller only needs standard CRUD:

- `save`
- `findById`
- `findAll`
- `existsById`
- `deleteById`

This repository is also injected into:

- `RouteController`
- `FareController`
- `TicketController`
- `MetroCardController`? no
- `MaintenanceIssueController`
- `AuthController`
- `DataSeeder`

---

## 5. Controller endpoints

Base path: `/api/stations`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/stations` | ADMIN | Create station |
| PUT | `/api/stations/{id}` | ADMIN | Update station |
| GET | `/api/stations/{id}` | authenticated | Read one station |
| GET | `/api/stations` | authenticated | List all stations |
| DELETE | `/api/stations/{id}` | ADMIN | Delete station |

---

## 6. Method-by-method flow

### Create station

```java
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public ResponseEntity<Station> createStation(@Valid @RequestBody Station request) {
    Station station = new Station();
    station.setStationId("STN-" + UUID.randomUUID());
    copyStationFields(request, station);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(stationRepository.save(station));
}
```

Steps:

1. `@PreAuthorize` checks `ROLE_ADMIN`.
2. `@Valid` checks `stationCode`.
3. A new `Station` is created.
4. The controller generates `STN-<uuid>`.
5. `copyStationFields` copies the editable fields.
6. `save` inserts the row.
7. Returns `201 Created`.

`copyStationFields`:

```java
station.setStationCode(request.getStationCode());
station.setLatitude(request.getLatitude());
station.setLongitude(request.getLongitude());
station.setAddress(request.getAddress());
station.setOpenedDate(request.getOpenedDate());
station.setLineColor(request.getLineColor());
if (request.getIsActive() != null) {
    station.setIsActive(request.getIsActive());
}
```

Why the `if`: if the client omits `isActive`, the existing default is kept. For a new station this means `true`; for an update it means "leave it unchanged".

### Update station

1. Load by ID:

```java
Station station = stationRepository.findById(id)
    .orElseThrow(() -> new IllegalArgumentException("Station not found"));
```

2. Copy fields.
3. Save.
4. Return `200 OK`.

The `stationId` is never changed. The path ID is the source of truth.

### Get one

```java
Station station = stationRepository.findById(id)
    .orElseThrow(() -> new IllegalArgumentException("Station not found"));
return ResponseEntity.ok(station);
```

Missing ID -> `400 "Station not found"` from `GlobalExceptionHandler`.

Why not 404? The project uses `IllegalArgumentException`, which the handler maps to 400. This is a design choice. In a production API, 404 would be more precise.

### Get all

```java
return ResponseEntity.ok(stationRepository.findAll());
```

Returns a JSON array of stations.

### Delete

```java
if (!stationRepository.existsById(id)) {
    throw new IllegalArgumentException("Station not found");
}
stationRepository.deleteById(id);
return ResponseEntity.noContent().build();
```

Returns `204 No Content`.

**Important:** if the station is referenced by a route, fare, ticket, user, or maintenance issue, PostgreSQL will reject the delete with a foreign-key error. There is no cascade delete. That is intentional: historical data should not silently disappear.

---

## 7. Request and response examples

### Create

```http
POST /api/stations
Authorization: Basic admin:admin123
Content-Type: application/json
```

```json
{
  "stationCode": "TST",
  "latitude": 19.07,
  "longitude": 72.87,
  "address": "Test Station, Mumbai",
  "openedDate": "2024-01-01",
  "lineColor": "Blue",
  "isActive": true
}
```

Response `201`:

```json
{
  "stationId": "STN-<uuid>",
  "stationCode": "TST",
  "latitude": 19.07,
  "longitude": 72.87,
  "address": "Test Station, Mumbai",
  "openedDate": "2024-01-01",
  "lineColor": "Blue",
  "isActive": true
}
```

### Update

```http
PUT /api/stations/STN-<uuid>
Authorization: Basic admin:admin123
```

```json
{
  "stationCode": "TST2",
  "latitude": 19.08,
  "longitude": 72.88,
  "address": "Updated address",
  "lineColor": "Red",
  "isActive": false
}
```

### List

```http
GET /api/stations
Authorization: Basic priya:Passw0rd!
```

Any authenticated role can read stations. This makes sense because passengers need station lists to book tickets.

---

## 8. How Station connects to other groups

### Route

```java
Route.startStation -> Station
Route.endStation   -> Station
```

A route must have a start and end station. If either does not exist, route creation fails.

### RouteStation

```java
RouteStation.station -> Station
```

Every ordered stop points to a station.

### Fare

```java
Fare.sourceStation -> Station
Fare.destStation   -> Station
```

A fare is a price for a station pair. The same station cannot be both source and destination.

### Ticket

```java
Ticket.sourceStation -> Station
Ticket.destStation   -> Station
```

A ticket stores the exact stations used, even if the fare changes later.

### User

```java
User.station -> Station
```

Staff may be assigned to a station. Passengers have `null`.

### MaintenanceIssue

```java
MaintenanceIssue.station -> Station
```

An issue can be about a station (for example, an escalator).

---

## 9. Seed data

`DataSeeder` inserts 18 Mumbai stations across three lines:

- Blue Line: Versova, Andheri, WEH, Airport Road, Marol Naka, Saki Naka, Ghatkopar
- Aqua Line: Aarey, SEEPZ, BKC, Dadar, Mumbai Central, CSMT
- Red Line: Dahisar East, Devipada, Poisar, Gundavali, Andheri East

IDs look like `STN-VSV`, `STN-AND`, etc.

---

## 10. Errors

| Situation | Status | Message |
|---|---|---|
| Non-admin create/update/delete | 403 | permission error |
| Missing `stationCode` | 400 | `stationCode: Station code is required` |
| Unknown ID | 400 | `Station not found` |
| Duplicate station code | 500 (DB unique violation) | generic database error |
| Delete referenced station | 500 (DB FK violation) | generic database error |

The duplicate-code case is protected by the unique index, but the controller does not check it in advance. If you want a friendlier 400, add an `existsByStationCode` check.

---

## 11. Swagger test

1. Authorize as `admin/admin123`.
2. `POST /api/stations` with the example body.
3. Copy the returned `stationId`.
4. `GET /api/stations/{id}` -> `200`.
5. `PUT /api/stations/{id}` -> `200`, changed fields.
6. `DELETE /api/stations/{id}` -> `204` (if nothing references it).
7. Re-authorize as `priya`, try `POST /api/stations` -> `403`.

---

## 12. Viva questions

**Q: Why is Station the first entity created in the demo?**
A: Routes, fares, tickets, staff, and maintenance all depend on stations.

**Q: Why is station code unique?**
A: It is the short human-readable identifier. The unique index prevents duplicates.

**Q: Why not use the station code as the primary key?**
A: The system uses generated IDs (`STN-<uuid>`) so codes can change without breaking foreign keys.

**Q: Why `@NotBlank` on stationCode?**
A: Every station needs a code. Other fields such as latitude are optional.

**Q: Why no cascade delete?**
A: Deleting a station used by tickets or fares would destroy history. The database blocks it.

**Q: Why does `GET /api/stations` require login?**
A: The API is not public; even read endpoints require Basic auth. Only registration and Swagger are public.

**Q: What is `LineColor`?**
A: A simple line identifier: Blue, Aqua, Red, Interchange.

**Q: Why is `Address` storing the name too?**
A: The original seed data combines name and address in one field. It is a demo convenience, not a separate `StationName` column.

**Q: What happens if I send `isActive: false`?**
A: The station is saved as inactive. It still exists and can still be referenced; only the flag changes.

**Q: How does the controller know the current user?**
A: It does not need to. Station operations are role-based, not ownership-based.

---

## 13. One-line summary

`Station` is the reference table for the physical network; routes, fares, tickets, staff, and maintenance all point to it, so it is created before everything else and protected from unsafe deletes by foreign keys.
