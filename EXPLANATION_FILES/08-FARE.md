# 08 — Fare Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/Fare.java` |
| Controller | `src/main/java/com/metro/ticketing/controller/FareController.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/FareRepository.java` |
| Tables/Indexes | `V1`, `V2` |
| Used by | Ticket |

---

## 1. What a Fare represents

A fare is the price of travelling from one station to another:

```text
Versova (STN-VSV) -> Ghatkopar (STN-GHP) = 40.00
```

The fare matrix is the set of all directed station pairs. It is directed: `A -> B` and `B -> A` are separate rows.

A ticket looks up the fare for its source/destination pair. The ticket stores a reference to the fare, so historical tickets keep their original price even if the fare is later changed.

---

## 2. Database table

```sql
CREATE TABLE "Fares" (
    "FareID" VARCHAR(50) PRIMARY KEY,
    "SourceStationID" VARCHAR(50) NOT NULL,
    "DestStationID" VARCHAR(50) NOT NULL,
    "BaseFare" NUMERIC(10,2) NOT NULL,
    CONSTRAINT "FK_Fare_SourceStation"
        FOREIGN KEY ("SourceStationID") REFERENCES "Stations" ("StationID"),
    CONSTRAINT "FK_Fare_DestStation"
        FOREIGN KEY ("DestStationID") REFERENCES "Stations" ("StationID")
);
```

V2 adds:

```sql
CREATE UNIQUE INDEX "UX_Fare_Station_Pair"
    ON "Fares" ("SourceStationID", "DestStationID");
```

| Column | Meaning |
|---|---|
| `FareID` | Primary key, `FARE-<uuid>` |
| `SourceStationID` | FK to `Stations` |
| `DestStationID` | FK to `Stations` |
| `BaseFare` | Price, `NUMERIC(10,2)` |

The unique index prevents two fares for the same directed pair.

---

## 3. Entity walkthrough

```java
@Entity
@Table(name = "\"Fares\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Fare {

    @Id
    private String fareId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"SourceStationID\"", referencedColumnName = "\"StationID\"", nullable = false)
    private Station sourceStation;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"DestStationID\"", referencedColumnName = "\"StationID\"", nullable = false)
    private Station destStation;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String sourceStationId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String destStationId;

    @NotNull(message = "Base fare is required")
    @DecimalMin(value = "0.01", message = "Base fare must be greater than zero")
    @Column(name = "\"BaseFare\"", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseFare;
}
```

### Relations

`sourceStation` and `destStation` are `@ManyToOne` to `Station`. They are `READ_ONLY` in JSON.

### Transient IDs

Requests send:

```json
{"sourceStationId": "STN-VSV", "destStationId": "STN-GHP", "baseFare": 40.00}
```

The controller resolves both stations.

### Money

`BigDecimal` maps to `NUMERIC(10,2)`.

Why not `double`:

- `double` cannot represent all decimal values exactly
- money needs predictable rounding
- `BigDecimal` is the standard choice for currency

### Validation

- `@NotNull` — base fare must be present
- `@DecimalMin("0.01")` — must be positive

---

## 4. Repository

```java
@Repository
public interface FareRepository extends JpaRepository<Fare, String> {

    boolean existsBySourceStation_StationIdAndDestStation_StationId(
        String sourceStationId, String destStationId);

    boolean existsBySourceStation_StationIdAndDestStation_StationIdAndFareIdNot(
        String sourceStationId, String destStationId, String fareId);

    Optional<Fare> findBySourceStation_StationIdAndDestStation_StationId(
        String sourceStationId, String destStationId);
}
```

### Derived query paths

| Method | SQL idea |
|---|---|
| `existsBySourceStation_StationIdAndDestStation_StationId` | does a fare exist for this directed pair? |
| `...AndFareIdNot` | does another fare exist for this pair, excluding this ID? |
| `findBy...` | return the fare row for this pair |

The underscore traverses relations:

```text
sourceStation -> Station.stationId
```

### Why the `AndFareIdNot` variant

On update, the fare's own row exists. A simple existence check would find itself and reject the update. The `Not` variant excludes the current fare.

---

## 5. Controller endpoints

Base path: `/api/fares`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/fares` | ADMIN | Create fare |
| GET | `/api/fares/lookup?sourceStationId=&destStationId=` | authenticated | Price lookup |
| GET | `/api/fares/{id}` | authenticated | Read one |
| GET | `/api/fares` | authenticated | List all |
| PUT | `/api/fares/{id}` | ADMIN | Update |
| DELETE | `/api/fares/{id}` | ADMIN | Delete |

---

## 6. Create fare — step by step

```java
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public ResponseEntity<Fare> createFare(@Valid @RequestBody Fare request) {
    validateStationPair(request.getSourceStationId(), request.getDestStationId());

    if (fareRepository.existsBySourceStation_StationIdAndDestStation_StationId(
            request.getSourceStationId(), request.getDestStationId())) {
        throw new IllegalArgumentException(
            "A fare for this station pair already exists");
    }

    Fare fare = new Fare();
    fare.setFareId("FARE-" + UUID.randomUUID());
    copyFareFields(request, fare);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(fareRepository.save(fare));
}
```

### `validateStationPair`

```java
if (sourceStationId == null || destStationId == null) {
    throw new IllegalArgumentException(
        "Source and destination stations are required");
}
if (sourceStationId.equals(destStationId)) {
    throw new IllegalArgumentException(
        "Source and destination stations must be different");
}
```

Rules:

- both IDs required
- cannot be the same station

### Duplicate check

The controller checks before saving. The unique index `UX_Fare_Station_Pair` is the database-level guarantee.

### `copyFareFields`

```java
Station source = stationRepository.findById(request.getSourceStationId())
    .orElseThrow(() -> new IllegalArgumentException("Source station not found"));
Station destination = stationRepository.findById(request.getDestStationId())
    .orElseThrow(() -> new IllegalArgumentException("Destination station not found"));

fare.setSourceStation(source);
fare.setDestStation(destination);
fare.setBaseFare(request.getBaseFare());
```

The request sends IDs; the entity stores `Station` relations.

---

## 7. Update fare — step by step

1. Load by ID.
2. Validate station pair.
3. Duplicate check excluding the current fare ID:

```java
existsBySourceStation_StationIdAndDestStation_StationIdAndFareIdNot(
    source, dest, id)
```

4. Copy fields.
5. Save.
6. Return `200`.

---

## 8. Lookup

```java
@GetMapping("/lookup")
@Transactional(readOnly = true)
public ResponseEntity<Fare> getFareByStations(
        @RequestParam String sourceStationId,
        @RequestParam String destStationId) {
    validateStationPair(sourceStationId, destStationId);
    Fare fare = fareRepository
        .findBySourceStation_StationIdAndDestStation_StationId(
            sourceStationId, destStationId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Fare not found for this station pair"));
    return ResponseEntity.ok(fare);
}
```

Example:

```http
GET /api/fares/lookup?sourceStationId=STN-VSV&destStationId=STN-GHP
Authorization: Basic priya:Passw0rd!
```

Response:

```json
{
  "fareId": "FARE-STN-VSV-STN-GHP",
  "baseFare": 40.00,
  "sourceStation": { "stationId": "STN-VSV", "...": "..." },
  "destStation": { "stationId": "STN-GHP", "...": "..." }
}
```

Why a lookup endpoint instead of asking passengers to browse all fares: the passenger knows the station IDs and needs one price.

---

## 9. Delete

1. Check existence.
2. Delete.
3. Return `204`.

If a ticket references the fare, the database rejects the delete. Historical tickets must keep their fare reference.

---

## 10. Relation to Ticket

`Ticket.fare` is a `@ManyToOne` to `Fare`.

Ticket creation:

```java
Fare fare = fareRepository
    .findBySourceStation_StationIdAndDestStation_StationId(sourceId, destId)
    .orElseThrow(() -> new IllegalArgumentException(
        "Fare not found for this station pair"));
```

Then:

```java
ticket.setFare(fare);
```

So the price is looked up at booking time and linked to the ticket.

Important: the ticket stores a reference to the fare row, not just the numeric price. If the fare amount is updated later, the ticket still points to the same fare row. The design is simple but not a full price-history audit.

---

## 11. Seed data

`DataSeeder.seedFares()` inserts 78 fares, both directions, for nearby station pairs on the three lines. Examples:

| Source | Destination | Fare |
|---|---|---:|
| `STN-VSV` | `STN-AND` | 15.00 |
| `STN-AND` | `STN-VSV` | 15.00 |
| `STN-VSV` | `STN-GHP` | 40.00 |
| `STN-ARY` | `STN-CSM` | 60.00 |
| `STN-DHE` | `STN-AEE` | 30.00 |

Fare IDs are deterministic in the seeder:

```text
FARE-<sourceId>-<destId>
```

Example: `FARE-STN-VSV-STN-GHP`.

---

## 12. Errors

| Situation | Status | Message |
|---|---|---|
| Non-admin write | 403 | permission error |
| Missing source/dest | 400 | `Source and destination stations are required` |
| Same station | 400 | `Source and destination stations must be different` |
| Missing base fare | 400 | `baseFare: Base fare is required` |
| Fare <= 0 | 400 | `baseFare: Base fare must be greater than zero` |
| Duplicate pair | 400 | `A fare for this station pair already exists` |
| Unknown station | 400 | `Source station not found` / `Destination station not found` |
| Unknown fare ID | 400 | `Fare not found` |
| No fare for pair | 400 | `Fare not found for this station pair` |
| Delete referenced fare | 500 (DB FK) | generic error |

---

## 13. Swagger test

1. Authorize as admin.
2. `POST /api/fares` with `STN-VSV` -> `STN-GHP`, `40.00`.
3. Try the same pair again -> `400` duplicate.
4. `GET /api/fares/lookup` with the same IDs -> `200`.
5. Try source = destination -> `400`.
6. As `priya`, `POST /api/fares` -> `403`.

---

## 14. Viva questions

**Q: Why is a fare directed?**
A: The table stores `(source, destination)`, so `A -> B` and `B -> A` are separate rows. This allows different prices if needed.

**Q: Why `BigDecimal`?**
A: Money needs exact decimal representation. `double` can produce rounding errors.

**Q: Why is there a unique index on the station pair?**
A: One directed pair should have one fare. V2 enforces it in the database.

**Q: Why does the update check exclude the current fare ID?**
A: Otherwise the row would find itself and look like a duplicate.

**Q: What happens if the fare is missing during ticket booking?**
A: Ticket creation fails with `400 "Fare not found for this station pair"`.

**Q: Is the fare stored on the ticket as a number?**
A: The ticket stores a `Fare` relation. The response includes the fare object.

**Q: Can an admin change a fare after tickets were sold?**
A: Yes. Existing tickets still point to the same fare row; there is no price snapshot.

**Q: Why are source/dest transient IDs used?**
A: Requests send IDs; the entity stores `Station` relations.

**Q: Why is `sourceStation` read-only in JSON?**
A: To stop clients from sending nested station objects and overwriting relations.

**Q: Does the lookup require a role?**
A: Only authentication. Passengers need it to see prices.

**Q: Can two stations have multiple fares (e.g. peak/off-peak)?**
A: Not in the current schema. The unique pair index allows only one fare per directed pair.

**Q: What is the fare ID format?**
A: New fares use `FARE-<uuid>`. Seeded fares use `FARE-<source>-<dest>`.

---

## 15. One-line summary

`Fare` is the directed price between two stations; the unique pair index prevents duplicates, and `Ticket` looks up and stores a fare reference when a passenger books.
