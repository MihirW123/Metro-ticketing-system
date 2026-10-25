# 07 — Schedule Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/Schedule.java` |
| Controller | `src/main/java/com/metro/ticketing/controller/ScheduleController.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/ScheduleRepository.java` |
| Table | `V1__create_metro_ticketing_schema.sql` |
| Related | Train, Route |

---

## 1. What a Schedule represents

A schedule says:

> Train M1 runs Route Blue Line on Sunday, departing at 23:00 and arriving at 23:30.

It connects a **train** to a **route** at a **time**, with an optional day and validity period.

It does not store ticket prices or seats. It is timetable data.

---

## 2. Database table

```sql
CREATE TABLE "Schedule" (
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
    CONSTRAINT "FK_Schedule_Train"
        FOREIGN KEY ("TrainID") REFERENCES "Trains" ("TrainID"),
    CONSTRAINT "FK_Schedule_Route"
        FOREIGN KEY ("RouteID") REFERENCES "Route" ("RouteID")
);
```

| Column | Meaning |
|---|---|
| `ScheduleID` | Primary key, `SCH-<uuid>` |
| `TrainID` | FK to `Trains`, required |
| `RouteID` | FK to `Route`, required |
| `DayOfWeek` | e.g. `DAILY`, `MONDAY`, `SATURDAY` |
| `ScheduledDeparture` | Departure timestamp |
| `ScheduledArrival` | Arrival timestamp |
| `ValidFrom` | Start date of validity |
| `ValidTo` | End date of validity |
| `isActive` | Status flag |
| `CreatedAt` | Creation timestamp |

---

## 3. Entity walkthrough

```java
@Entity
@Table(name = "\"Schedule\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Schedule {

    @Id
    private String scheduleId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"TrainID\"", referencedColumnName = "\"TrainID\"", nullable = false)
    private Train train;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"RouteID\"", referencedColumnName = "\"RouteID\"", nullable = false)
    private Route route;

    private String dayOfWeek;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String trainId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String routeId;

    @NotNull(message = "Scheduled departure is required")
    private LocalDateTime scheduledDeparture;

    @NotNull(message = "Scheduled arrival is required")
    private LocalDateTime scheduledArrival;

    private LocalDate validFrom;
    private LocalDate validTo;
    private Boolean isActive = true;
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### Relations

- `train` is `@ManyToOne` to `Train`.
- `route` is `@ManyToOne` to `Route`.
- Both are `READ_ONLY` in JSON; requests send IDs instead.

### Transient IDs

```java
@Transient
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String trainId;

@Transient
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String routeId;
```

The request sends:

```json
{"trainId": "TRN-101", "routeId": "RT-L1", "...": "..."}
```

The controller loads the real `Train` and `Route`.

### Required times

`@NotNull` on departure and arrival means `@Valid` rejects missing times.

### Validity dates

`validFrom` and `validTo` exist on the entity and in the table, but the controller does **not** copy them from the request. `DataSeeder` sets them, and the API currently leaves them null.

This is a limitation worth knowing in viva: the fields exist, but the create/update endpoints do not expose them yet.

---

## 4. Repository and overlap query

```java
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    @Query("SELECT COUNT(s) > 0 FROM Schedule s WHERE s.train.trainId = :trainId AND " +
           "s.scheduledDeparture < :arrivalTime AND s.scheduledArrival > :departureTime AND " +
           "(:scheduleId IS NULL OR s.scheduleId != :scheduleId)")
    boolean hasOverlappingSchedule(
        @Param("trainId") String trainId,
        @Param("departureTime") LocalDateTime departureTime,
        @Param("arrivalTime") LocalDateTime arrivalTime,
        @Param("scheduleId") String scheduleId
    );
}
```

### What the query means

An overlap exists when:

```text
existing.departure < new.arrival
AND existing.arrival > new.departure
```

This is the standard interval-overlap test. It catches:

- new schedule inside an existing one
- existing inside the new one
- partial overlap at either end

It also checks `s.train.trainId = :trainId`, so only the same train is considered.

### Why `scheduleId` is a parameter

- On create: pass `null` — do not exclude anything.
- On update: pass the current schedule ID — exclude the row being edited from the overlap check.

Without this, updating a schedule would always collide with itself.

### Why `COUNT(s) > 0`

It returns a boolean directly, without loading all rows. Hibernate converts the result to `boolean`.

---

## 5. Controller endpoints

Base path: `/api/schedules`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/schedules` | ADMIN | Create schedule |
| PUT | `/api/schedules/{id}` | ADMIN | Update schedule |
| GET | `/api/schedules/{id}` | authenticated | Read one |
| GET | `/api/schedules` | authenticated | List all |
| DELETE | `/api/schedules/{id}` | ADMIN | Delete |

---

## 6. Create schedule — step by step

```java
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public ResponseEntity<Schedule> createSchedule(@Valid @RequestBody Schedule request) {
    validateTimes(request);
    if (scheduleRepository.hasOverlappingSchedule(
            request.getTrainId(), request.getScheduledDeparture(),
            request.getScheduledArrival(), null)) {
        throw new IllegalArgumentException(
            "Train is already scheduled during this time period");
    }

    Schedule schedule = new Schedule();
    schedule.setScheduleId("SCH-" + UUID.randomUUID());
    copyScheduleFields(request, schedule);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(scheduleRepository.save(schedule));
}
```

### 1. Validate time order

```java
if (request.getScheduledArrival().isBefore(request.getScheduledDeparture())) {
    throw new IllegalArgumentException(
        "Arrival time cannot be before departure time");
}
```

### 2. Check overlap

Same train, same time window -> reject.

### 3. Generate ID

`SCH-<uuid>`.

### 4. Copy fields

```java
private void copyScheduleFields(Schedule request, Schedule schedule) {
    if (request.getRouteId() == null || request.getTrainId() == null) {
        throw new IllegalArgumentException("Train and route are required");
    }
    Route route = routeRepository.findById(request.getRouteId())
        .orElseThrow(() -> new IllegalArgumentException("Route not found"));
    Train train = trainRepository.findById(request.getTrainId())
        .orElseThrow(() -> new IllegalArgumentException("Train not found"));

    schedule.setRoute(route);
    schedule.setTrain(train);
    schedule.setScheduledDeparture(request.getScheduledDeparture());
    schedule.setScheduledArrival(request.getScheduledArrival());
    schedule.setDayOfWeek(request.getDayOfWeek());
    if (request.getIsActive() != null) {
        schedule.setIsActive(request.getIsActive());
    }
}
```

Steps:

1. Require `trainId` and `routeId`.
2. Load the train and route.
3. Set relations.
4. Set times and day.
5. Keep default active flag unless supplied.

### 5. Save and return `201`.

---

## 7. Update schedule — step by step

1. Load by ID.
2. Validate time order.
3. Check overlap with the same query, passing the current ID so the schedule does not collide with itself.
4. Copy fields.
5. Save.
6. Return `200`.

---

## 8. Read and delete

### Read

- `GET /api/schedules/{id}` returns one.
- `GET /api/schedules` returns all.

### Delete

1. Check existence.
2. `deleteById`.
3. Return `204`.

If a schedule is not referenced by anything else, deletion is safe. Nothing else points to a schedule.

---

## 9. Request and response examples

### Create

```http
POST /api/schedules
Authorization: Basic admin:admin123
Content-Type: application/json
```

```json
{
  "trainId": "TRN-101",
  "routeId": "RT-L1",
  "dayOfWeek": "SUNDAY",
  "scheduledDeparture": "2026-10-25T23:00:00",
  "scheduledArrival": "2026-10-25T23:30:00",
  "isActive": true
}
```

Response `201`:

```json
{
  "scheduleId": "SCH-<uuid>",
  "train": { "trainId": "TRN-101", "...": "..." },
  "route": { "routeId": "RT-L1", "...": "..." },
  "dayOfWeek": "SUNDAY",
  "scheduledDeparture": "2026-10-25T23:00:00",
  "scheduledArrival": "2026-10-25T23:30:00",
  "isActive": true,
  "validFrom": null,
  "validTo": null,
  "createdAt": "2026-10-25T..."
}
```

### Overlap example

Existing:

```text
TRN-101, 23:00–23:30
```

New:

```text
TRN-101, 23:15–23:45
```

Result: `400 "Train is already scheduled during this time period"`.

---

## 10. Relations and behavior

### Train

A schedule belongs to one train. Deleting a train with schedules fails due to the FK.

### Route

A schedule belongs to one route. Deleting a route with schedules fails due to the FK.

### Stops

Schedules do not reference individual stops. They reference a route; stops are in `RouteStation`.

### Day of week

`dayOfWeek` is a free string. The overlap query does **not** consider it. Two schedules for the same train on different days but overlapping clock times are still rejected, because the query only compares timestamps.

This is a known limitation. A more precise design would store a date or validate the day.

---

## 11. Seed data

`DataSeeder.seedSchedules()` creates 8 schedules:

| ID | Train | Route | Day | Departure | Arrival |
|---|---|---|---|---|---|
| `SCH-001` | `TRN-101` | `RT-L1` | DAILY | 06:00 | 06:46 |
| `SCH-002` | `TRN-102` | `RT-L1` | DAILY | 07:00 | 07:46 |
| `SCH-003` | `TRN-103` | `RT-L1` | MONDAY | 08:30 | 09:16 |
| `SCH-004` | `TRN-201` | `RT-L2` | DAILY | 06:10 | 07:08 |
| `SCH-005` | `TRN-202` | `RT-L2` | DAILY | 06:55 | 07:53 |
| `SCH-006` | `TRN-203` | `RT-L2` | SATURDAY | 09:15 | 10:13 |
| `SCH-007` | `TRN-301` | `RT-L3` | DAILY | 06:05 | 06:39 |
| `SCH-008` | `TRN-302` | `RT-L3` | DAILY | 06:35 | 07:09 |

The seeder sets `validFrom`, `validTo`, and `createdAt`.

---

## 12. Errors

| Situation | Status | Message |
|---|---|---|
| Non-admin write | 403 | permission error |
| Missing departure/arrival | 400 | validation field map |
| Arrival before departure | 400 | `Arrival time cannot be before departure time` |
| Missing train/route ID | 400 | `Train and route are required` |
| Unknown route | 400 | `Route not found` |
| Unknown train | 400 | `Train not found` |
| Overlap | 400 | `Train is already scheduled during this time period` |
| Unknown schedule | 400 | `Schedule not found` |

---

## 13. Swagger test

1. Authorize as admin.
2. `POST /api/schedules` using `TRN-101` / `RT-L1` and a time that does not overlap (e.g. 23:00–23:30 on a future date).
3. Create the same time again -> `400` overlap.
4. `PUT` the schedule with a new time -> works.
5. `GET /api/schedules` -> list.
6. `DELETE` -> `204`.

---

## 14. Viva questions

**Q: What is the difference between a route and a schedule?**
A: A route is the path; a schedule assigns a train to that route at a time.

**Q: How do you detect overlapping schedules?**
A: `existing.departure < new.arrival AND existing.arrival > new.departure` for the same train.

**Q: Why pass `null` on create and the ID on update?**
A: On update, the current row must be excluded from the overlap check.

**Q: Why is the query a `@Query` instead of a derived method?**
A: The overlap condition is more complex than a method name can express cleanly.

**Q: Why is `trainId` transient?**
A: The request sends an ID, but the database stores the `Train` relation.

**Q: Why is `train` read-only in JSON?**
A: Requests should not overwrite the relation with nested objects.

**Q: Does the API check the day of week?**
A: No. The overlap query compares only timestamps.

**Q: What happens if arrival equals departure?**
A: `isBefore` is false, so it is allowed. The trip would have zero duration.

**Q: What happens if `validFrom`/`validTo` are sent?**
A: They are ignored by `copyScheduleFields`; the entity keeps null unless the seeder sets them.

**Q: Why does the response include `stationIds: null` inside the nested route?**
A: The nested route is serialized without loading its stop list. The top-level route endpoint loads stops explicitly.

**Q: Can a train have two schedules on different days at the same time?**
A: The current query would reject it because it ignores `dayOfWeek`. This is a limitation.

**Q: Why `@Transactional` on reads?**
A: To keep the session open while lazy relations are serialized.

---

## 15. One-line summary

`Schedule` links a train to a route at a time; the controller validates time order and uses an interval-overlap query to prevent the same train from being double-booked.
