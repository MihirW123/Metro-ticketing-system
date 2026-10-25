# 12 — Maintenance Issue Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/MaintenanceIssue.java` |
| Controller | `src/main/java/com/metro/ticketing/controller/MaintenanceIssueController.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/MaintenanceIssueRepository.java` |
| Table | `V1__create_metro_ticketing_schema.sql` |
| Related | Train, Station, User |

---

## 1. What a MaintenanceIssue represents

A maintenance issue is a fault report:

```text
Issue ISS-<uuid>
Train TRN-101
Type TRAIN
Description Door sensor on coach 3 failing
Priority HIGH
Status Open
Reported by maint1
Created now
Resolved null
```

An issue can be about:

- a train (`TrainID`)
- a station (`StationID`)

At least one of them should be supplied in practice, but the database allows both to be null.

The issue stores a reporter (`ReportedId`), a status, a priority, and resolution time.

---

## 2. Database table

```sql
CREATE TABLE "MaintenanceIssue" (
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
    CONSTRAINT "FK_Maintenance_Train"
        FOREIGN KEY ("TrainID") REFERENCES "Trains" ("TrainID"),
    CONSTRAINT "FK_Maintenance_Station"
        FOREIGN KEY ("StationID") REFERENCES "Stations" ("StationID"),
    CONSTRAINT "FK_Maintenance_Reporter"
        FOREIGN KEY ("ReportedId") REFERENCES "User" ("UserId")
);
```

| Column | Meaning |
|---|---|
| `IssueID` | Primary key, `ISS-<uuid>` |
| `TrainID` | Optional FK to `Trains` |
| `StationID` | Optional FK to `Stations` |
| `ReportedId` | Optional FK to `User` |
| `IssueType` | Free text: `TRAIN`, `SIGNAL`, `LIFT_ESCALATOR`, `FACILITY`, ... |
| `Description` | Long text |
| `Status` | `Open`, `IN_PROGRESS`, `RESOLVED`, `Closed` |
| `Priority` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `CreatedAt` | Report time |
| `ResolvedAt` | Resolution time |

---

## 3. Entity walkthrough

```java
@Entity
@Table(name = "\"MaintenanceIssue\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MaintenanceIssue {

    @Id
    private String issueId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"TrainID\"", referencedColumnName = "\"TrainID\"")
    private Train train;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"StationID\"", referencedColumnName = "\"StationID\"")
    private Station station;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"ReportedId\"", referencedColumnName = "\"UserId\"")
    private User reportedBy;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String trainId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String stationId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String reportedById;

    @NotBlank(message = "Issue type is required")
    private String issueType;

    @NotBlank(message = "Description is required")
    private String description;

    private String status = "Open";
    private String priority;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;
}
```

### Optional relations

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "\"TrainID\"", ...)
private Train train;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "\"StationID\"", ...)
private Station station;
```

Both are nullable. A train issue may have no station, and a station issue may have no train.

### Reporter

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "\"ReportedId\"", ...)
private User reportedBy;
```

Optional. The controller only sets it if `reportedById` is provided. It does **not** automatically use the authenticated user.

### Transient request IDs

| Field | Meaning |
|---|---|
| `trainId` | Which train is faulty |
| `stationId` | Which station is faulty |
| `reportedById` | Who reported it |

### Required text

`issueType` and `description` use `@NotBlank`.

### Status

Default `"Open"`.

Controller create also explicitly sets `"Open"`.

The resolve endpoint sets `"Closed"`.

### Priority

Free text. If omitted on create, the controller uses `"MEDIUM"`.

The demo uses `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.

---

## 4. Repository

```java
@Repository
public interface MaintenanceIssueRepository
        extends JpaRepository<MaintenanceIssue, String> {

    List<MaintenanceIssue> findByTrain_TrainId(String trainId);

    List<MaintenanceIssue> findByStation_StationId(String stationId);

    List<MaintenanceIssue> findByStatus(String status);
}
```

Derived SQL:

```sql
SELECT * FROM "MaintenanceIssue" WHERE "TrainID" = ?
SELECT * FROM "MaintenanceIssue" WHERE "StationID" = ?
SELECT * FROM "MaintenanceIssue" WHERE "Status" = ?
```

There is no `findByPriority` method anymore because no controller endpoint uses it.

---

## 5. Controller endpoints

Base path: `/api/maintenance`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/maintenance` | ADMIN, MAINTENANCE_STAFF, TICKET_AGENT | Report issue |
| PUT | `/api/maintenance/{id}` | ADMIN, MAINTENANCE_STAFF | Update issue |
| GET | `/api/maintenance/{id}` | authenticated | Read one |
| GET | `/api/maintenance` | ADMIN, MAINTENANCE_STAFF | List all |
| GET | `/api/maintenance/train/{trainId}` | authenticated | Issues for a train |
| GET | `/api/maintenance/station/{stationId}` | authenticated | Issues for a station |
| GET | `/api/maintenance/status/{status}` | ADMIN, MAINTENANCE_STAFF | Filter by status |
| PATCH | `/api/maintenance/{id}/resolve` | ADMIN, MAINTENANCE_STAFF | Resolve |
| DELETE | `/api/maintenance/{id}` | ADMIN | Delete |

Notice: `TICKET_AGENT` can report an issue but cannot resolve it.

---

## 6. Create issue — step by step

```java
@PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'MAINTENANCE_STAFF', 'TICKET_AGENT')")
@Transactional
public ResponseEntity<MaintenanceIssue> createMaintenanceIssue(
        @Valid @RequestBody MaintenanceIssue request) {
```

### Step 1 — build issue

```java
MaintenanceIssue issue = new MaintenanceIssue();
issue.setIssueId("ISS-" + UUID.randomUUID());
issue.setIssueType(request.getIssueType());
issue.setDescription(request.getDescription());
issue.setPriority(request.getPriority() != null
    ? request.getPriority() : "MEDIUM");
issue.setStatus("Open");
issue.setCreatedAt(LocalDateTime.now());
```

### Step 2 — apply optional relations

```java
applyRelations(request, issue);
```

```java
private void applyRelations(MaintenanceIssue request, MaintenanceIssue issue) {
    if (request.getTrainId() != null) {
        Train train = trainRepository.findById(request.getTrainId())
            .orElseThrow(() -> new IllegalArgumentException("Train not found"));
        issue.setTrain(train);
    }
    if (request.getStationId() != null) {
        Station station = stationRepository.findById(request.getStationId())
            .orElseThrow(() -> new IllegalArgumentException("Station not found"));
        issue.setStation(station);
    }
    if (request.getReportedById() != null) {
        User reporter = userRepository.findById(request.getReportedById())
            .orElseThrow(() -> new IllegalArgumentException("Reporter not found"));
        issue.setReportedBy(reporter);
    }
}
```

### Step 3 — save and return `201`.

---

## 7. Update issue — step by step

```java
@PutMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'MAINTENANCE_STAFF')")
@Transactional
public ResponseEntity<MaintenanceIssue> updateMaintenanceIssue(
        @PathVariable String id,
        @Valid @RequestBody MaintenanceIssue request) {
```

1. Load by ID, or throw `Maintenance issue not found`.
2. Set `issueType` and `description` from the request.
3. Set `status` if provided.
4. Set `priority` if provided.
5. Apply optional relations (`trainId`, `stationId`, `reportedById`).
6. Save and return `200`.

Why `issueType` and `description` are mandatory on update: they have `@NotBlank`, so `@Valid` requires them in the request body.

---

## 8. Read endpoints

### One issue

`GET /api/maintenance/{id}` returns one. Any authenticated user.

### All issues

`GET /api/maintenance` requires ADMIN or MAINTENANCE_STAFF.

### By train

`GET /api/maintenance/train/{trainId}` uses `findByTrain_TrainId`.

Any authenticated user.

### By station

`GET /api/maintenance/station/{stationId}` uses `findByStation_StationId`.

Any authenticated user.

### By status

`GET /api/maintenance/status/{status}` requires ADMIN or MAINTENANCE_STAFF.

Uses `findByStatus`.

Status matching is exact and case-sensitive at the database level (for example `Open`). The seed data uses `OPEN`, `IN_PROGRESS`, `RESOLVED`. The resolve endpoint compares `"Closed"` ignoring case.

---

## 9. Resolve issue

```java
@PatchMapping("/{id}/resolve")
@PreAuthorize("hasAnyRole('ADMIN', 'MAINTENANCE_STAFF')")
@Transactional
public ResponseEntity<MaintenanceIssue> resolveMaintenanceIssue(
        @PathVariable String id) {
    MaintenanceIssue issue = maintenanceIssueRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException(
            "Maintenance issue not found"));

    if ("Closed".equalsIgnoreCase(issue.getStatus())) {
        throw new IllegalArgumentException(
            "Maintenance issue is already closed");
    }

    issue.setStatus("Closed");
    issue.setResolvedAt(LocalDateTime.now());
    return ResponseEntity.ok(maintenanceIssueRepository.save(issue));
}
```

Rules:

- only admin or maintenance staff
- cannot resolve an already closed issue
- sets status `Closed`
- sets `resolvedAt` to now

---

## 10. Delete issue

```java
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public ResponseEntity<Void> deleteMaintenanceIssue(@PathVariable String id) {
    if (!maintenanceIssueRepository.existsById(id)) {
        throw new IllegalArgumentException("Maintenance issue not found");
    }
    maintenanceIssueRepository.deleteById(id);
    return ResponseEntity.noContent().build();
}
```

Admin only. No other table references a maintenance issue, so deletion is safe.

---

## 11. Request and response examples

### Report a train issue

```http
POST /api/maintenance
Authorization: Basic maint1:Passw0rd!
Content-Type: application/json
```

```json
{
  "trainId": "TRN-101",
  "issueType": "TRAIN",
  "description": "Door sensor on coach 3 is failing",
  "priority": "HIGH"
}
```

Response `201`:

```json
{
  "issueId": "ISS-<uuid>",
  "issueType": "TRAIN",
  "description": "Door sensor on coach 3 is failing",
  "status": "Open",
  "priority": "HIGH",
  "createdAt": "2026-10-25T...",
  "resolvedAt": null,
  "train": {
    "trainId": "TRN-101",
    "trainNumber": "M1"
  },
  "station": null,
  "reportedBy": null
}
```

Notice `reportedBy` is null because the request did not send `reportedById`. If you want the reporter stored, include:

```json
"reportedById": "STAFF-02"
```

### Report a station issue

```json
{
  "stationId": "STN-AND",
  "issueType": "LIFT_ESCALATOR",
  "description": "Escalator 2 is making a grinding noise",
  "priority": "MEDIUM",
  "reportedById": "STAFF-01"
}
```

### Update

```http
PUT /api/maintenance/ISS-<uuid>
Authorization: Basic maint1:Passw0rd!
```

```json
{
  "issueType": "TRAIN",
  "description": "Door sensor replaced, testing",
  "status": "IN_PROGRESS",
  "priority": "MEDIUM",
  "trainId": "TRN-101"
}
```

### Resolve

```http
PATCH /api/maintenance/ISS-<uuid>/resolve
Authorization: Basic maint1:Passw0rd!
```

Response `200`:

```json
{
  "issueId": "ISS-<uuid>",
  "status": "Closed",
  "resolvedAt": "2026-10-25T..."
}
```

### Filter

```http
GET /api/maintenance/status/Open
Authorization: Basic maint1:Passw0rd!
```

---

## 12. Relations

### Train

`MaintenanceIssue.train -> Train`, optional.

### Station

`MaintenanceIssue.station -> Station`, optional.

### User

`MaintenanceIssue.reportedBy -> User`, optional.

### Deletion

Trains and stations cannot be deleted while issues reference them.

---

## 13. Seed data

`DataSeeder.seedMaintenance()` creates 4 issues:

| ID | Train | Station | Reporter | Type | Status | Priority |
|---|---|---|---|---|---|---|
| `ISS-SEED1` | `TRN-101` | — | maint1 | TRAIN | OPEN | HIGH |
| `ISS-SEED2` | — | `STN-AND` | agent1 | LIFT_ESCALATOR | IN_PROGRESS | MEDIUM |
| `ISS-SEED3` | `TRN-202` | — | maint1 | SIGNAL | RESOLVED | HIGH |
| `ISS-SEED4` | — | `STN-CSM` | agent1 | FACILITY | OPEN | LOW |

Note: seeded statuses use `OPEN`, while the create endpoint uses `Open`. Status is free text, so both exist. The resolve endpoint handles `Closed` case-insensitively.

---

## 14. Errors

| Situation | Status | Message |
|---|---|---|
| Passenger reports | 403 | permission error |
| Missing issue type | 400 | `issueType: Issue type is required` |
| Missing description | 400 | `description: Description is required` |
| Unknown train | 400 | `Train not found` |
| Unknown station | 400 | `Station not found` |
| Unknown reporter | 400 | `Reporter not found` |
| Unknown issue ID | 400 | `Maintenance issue not found` |
| Agent tries to resolve | 403 | permission error |
| Already closed | 400 | `Maintenance issue is already closed` |
| Non-admin delete | 403 | permission error |

---

## 15. Swagger test

1. Authorize as `maint1/Passw0rd!`.
2. `POST /api/maintenance` with `TRN-101`, type `TRAIN`, description, priority `HIGH` -> `201`.
3. Copy `issueId`.
4. `GET /api/maintenance/{id}` -> `200`.
5. `PATCH /api/maintenance/{id}/resolve` -> `200`, status `Closed`.
6. Resolve again -> `400`.
7. Authorize as `priya`, try `POST /api/maintenance` -> `403`.
8. Authorize as `agent1`, try resolve -> `403`.

---

## 16. Viva questions

**Q: Can an issue be about both a train and a station?**
A: The schema allows both IDs, but the normal use case is one or the other. The controller does not reject both.

**Q: Is `reportedBy` set automatically to the logged-in user?**
A: No. It is only set if `reportedById` is provided. This is a limitation; a future version could default to the current user.

**Q: Why are the relations nullable?**
A: An issue may relate to a train or a station, so neither can be mandatory.

**Q: What statuses are used?**
A: The API creates `Open`, resolve sets `Closed`, and demo data also uses `OPEN`, `IN_PROGRESS`, `RESOLVED`. Status is free text.

**Q: Why can a ticket agent report but not resolve?**
A: Reporting is part of customer service. Resolution is maintenance work, restricted to `MAINTENANCE_STAFF` and `ADMIN`.

**Q: How does filtering by status work?**
A: `findByStatus(status)` performs an exact match.

**Q: Why can't an issue be resolved twice?**
A: The controller checks for `Closed` and rejects it.

**Q: What is the issue ID format?**
A: `ISS-<uuid>`.

**Q: Why is `description` TEXT?**
A: Fault descriptions can be long; `TEXT` has no small length limit.

**Q: Why is `priority` not an enum?**
A: It is free text for simplicity. A production system would use an enum or a lookup table.

**Q: What happens if the train is deleted?**
A: The database blocks it while an issue references the train.

**Q: Is there a priority filter endpoint?**
A: No. The repository no longer has `findByPriority`; only status/train/station filters are exposed.

**Q: Why is `trainId` transient?**
A: The request sends an ID, but the entity stores the train relation.

---

## 17. One-line summary

`MaintenanceIssue` records a fault on a train or station, its priority and status, and supports reporting, updating, filtering, and a one-way resolve action that closes the issue and records the resolution time.
