# 09 — Ticket Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/Ticket.java` |
| Controller | `src/main/java/com/metro/ticketing/controller/TicketController.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/TicketRepository.java` |
| Table | `V1__create_metro_ticketing_schema.sql` |
| Related | User, Station, Fare, Payment |

---

## 1. What a Ticket represents

A ticket is a booked journey:

> Priya travels from Versova to Ghatkopar, for ₹40, issued at 19:55, valid for 24 hours, not yet used.

It stores:

- ticket ID
- passenger
- fare
- source station
- destination station
- ticket type
- validity time
- used flag
- issue time
- creation time

A ticket is the central booking record. Payments point to it, and the `use` endpoint controls gate validation.

---

## 2. Database table

```sql
CREATE TABLE "Tickets" (
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
    CONSTRAINT "FK_Ticket_Passenger"
        FOREIGN KEY ("PassengerID") REFERENCES "User" ("UserId"),
    CONSTRAINT "FK_Ticket_Fare"
        FOREIGN KEY ("FareID") REFERENCES "Fares" ("FareID"),
    CONSTRAINT "FK_Ticket_SourceStation"
        FOREIGN KEY ("SourceStationID") REFERENCES "Stations" ("StationID"),
    CONSTRAINT "FK_Ticket_DestStation"
        FOREIGN KEY ("DestStationID") REFERENCES "Stations" ("StationID")
);
```

| Column | Meaning |
|---|---|
| `TicketID` | Primary key, `TKT-<uuid>` |
| `PassengerID` | FK to `User` |
| `FareID` | FK to `Fares` |
| `SourceStationID` | FK to `Stations` |
| `DestStationID` | FK to `Stations` |
| `TicketType` | Free text: `SINGLE`, `ROUND_TRIP`, `DAILY`, `WEEKLY` |
| `ValidUntil` | Expiry timestamp |
| `isUsed` | Gate flag |
| `IssueTime` | When issued |
| `CreatedAt` | Row creation time |

---

## 3. Entity walkthrough

```java
@Entity
@Table(name = "\"Tickets\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Ticket {

    @Id
    private String ticketId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"PassengerID\"", referencedColumnName = "\"UserId\"", nullable = false)
    private User passenger;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"FareID\"", referencedColumnName = "\"FareID\"", nullable = false)
    private Fare fare;

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
    private String passengerId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String sourceStationId;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String destStationId;

    @NotBlank(message = "Ticket type is required")
    private String ticketType;

    private LocalDateTime validUntil;
    private Boolean isUsed = false;
    private LocalDateTime issueTime = LocalDateTime.now();
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### Relations

All four relations are `READ_ONLY` in JSON:

- `passenger`
- `fare`
- `sourceStation`
- `destStation`

Why: requests send IDs, not nested objects. The controller resolves the real rows.

### Transient request IDs

| Field | Used when |
|---|---|
| `passengerId` | Agent/admin books for a passenger |
| `sourceStationId` | Always required |
| `destStationId` | Always required |

For a passenger caller, `passengerId` is ignored and replaced by the authenticated user.

### Ticket type

`@NotBlank` makes it required.

There is no enum, so the API accepts free text. The demo data uses:

- `SINGLE`
- `ROUND_TRIP`
- `DAILY`
- `WEEKLY`

A production system would use an enum or a lookup table.

### Times

| Field | Default |
|---|---|
| `validUntil` | controller sets now + 24 hours if omitted |
| `isUsed` | `false` |
| `issueTime` | `now()` |
| `createdAt` | `now()` |

---

## 4. Repository

```java
@Repository
public interface TicketRepository extends JpaRepository<Ticket, String> {
    List<Ticket> findByPassenger_UserId(String passengerId);
}
```

Derived query:

```sql
SELECT * FROM "Tickets" WHERE "PassengerID" = ?
```

Used by `GET /api/tickets/passenger/{passengerId}`.

Standard CRUD comes from `JpaRepository`.

---

## 5. Controller endpoints

Base path: `/api/tickets`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/tickets` | PASSENGER, TICKET_AGENT, ADMIN | Book a ticket |
| GET | `/api/tickets/{id}` | authenticated + ownership | Read one |
| GET | `/api/tickets` | ADMIN, TICKET_AGENT | List all |
| GET | `/api/tickets/passenger/{passengerId}` | authenticated + ownership | List a passenger's tickets |
| PATCH | `/api/tickets/{id}/use` | PASSENGER, TICKET_AGENT, ADMIN | Mark used |
| DELETE | `/api/tickets/{id}` | ADMIN | Delete |

---

## 6. Create ticket — complete flow

```java
@PostMapping
@PreAuthorize("hasAnyRole('PASSENGER', 'TICKET_AGENT', 'ADMIN')")
@Transactional
public ResponseEntity<Ticket> createTicket(
        @Valid @RequestBody Ticket request,
        Authentication authentication) {
```

### Step 1 — required stations

```java
if (request.getSourceStationId() == null || request.getDestStationId() == null) {
    throw new IllegalArgumentException(
        "Source and destination stations are required");
}
if (request.getSourceStationId().equals(request.getDestStationId())) {
    throw new IllegalArgumentException(
        "Source and destination stations must be different");
}
```

### Step 2 — decide the passenger

```java
String passengerId = request.getPassengerId();
if (isPassenger(authentication)) {
    passengerId = currentUser(authentication).getUserId();
}
if (passengerId == null || passengerId.isBlank()) {
    throw new IllegalArgumentException("Passenger ID is required");
}
```

| Caller | Passenger used |
|---|---|
| PASSENGER | the authenticated user (request value is ignored) |
| TICKET_AGENT | `request.passengerId` |
| ADMIN | `request.passengerId` |

Why: a passenger must not be able to book a ticket in someone else's name.

### Step 3 — load related rows

```java
User passenger = userRepository.findById(passengerId)
    .orElseThrow(() -> new IllegalArgumentException("Passenger not found"));

Station source = stationRepository.findById(request.getSourceStationId())
    .orElseThrow(() -> new IllegalArgumentException("Source station not found"));

Station destination = stationRepository.findById(request.getDestStationId())
    .orElseThrow(() -> new IllegalArgumentException("Destination station not found"));
```

### Step 4 — find the fare

```java
Fare fare = fareRepository
    .findBySourceStation_StationIdAndDestStation_StationId(
        request.getSourceStationId(), request.getDestStationId())
    .orElseThrow(() -> new IllegalArgumentException(
        "Fare not found for this station pair"));
```

The ticket cannot be created without a fare. That is why fares are seeded before booking.

### Step 5 — build the ticket

```java
Ticket ticket = new Ticket();
ticket.setTicketId("TKT-" + UUID.randomUUID());
ticket.setPassenger(passenger);
ticket.setFare(fare);
ticket.setSourceStation(source);
ticket.setDestStation(destination);
ticket.setTicketType(request.getTicketType());
ticket.setIssueTime(LocalDateTime.now());
ticket.setCreatedAt(LocalDateTime.now());
ticket.setIsUsed(false);
ticket.setValidUntil(request.getValidUntil() != null
    ? request.getValidUntil() : LocalDateTime.now().plusHours(24));
```

### Step 6 — save and return `201`.

---

## 7. Read ticket by ID

```java
@GetMapping("/{id}")
@Transactional(readOnly = true)
public ResponseEntity<Ticket> getTicketById(
        @PathVariable String id,
        Authentication authentication) {
    Ticket ticket = ticketRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
    checkOwnership(ticket, authentication);
    return ResponseEntity.ok(ticket);
}
```

### Ownership check

```java
private void checkOwnership(Ticket ticket, Authentication authentication) {
    if (isPassenger(authentication)
            && !ticket.getPassenger().getUserId()
                    .equals(currentUser(authentication).getUserId())) {
        throw new AccessDeniedException(
            "You can only access your own tickets");
    }
}
```

- Agent/admin: can read any ticket.
- Passenger: only their own.

Otherwise: `403`.

---

## 8. List all tickets

```java
@GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'TICKET_AGENT')")
public ResponseEntity<List<Ticket>> getAllTickets() {
    return ResponseEntity.ok(ticketRepository.findAll());
}
```

Passengers cannot call this. They get `403`.

---

## 9. List tickets by passenger

```java
@GetMapping("/passenger/{passengerId}")
public ResponseEntity<List<Ticket>> getTicketsByPassenger(
        @PathVariable String passengerId,
        Authentication authentication) {
    if (isPassenger(authentication)) {
        passengerId = currentUser(authentication).getUserId();
    }
    return ResponseEntity.ok(ticketRepository.findByPassenger_UserId(passengerId));
}
```

A passenger who passes someone else's ID is silently redirected to their own ID. They never see another passenger's list.

Agents/admins can query any passenger ID.

---

## 10. Mark ticket as used

```java
@PatchMapping("/{id}/use")
@PreAuthorize("hasAnyRole('PASSENGER', 'TICKET_AGENT', 'ADMIN')")
@Transactional
public ResponseEntity<Ticket> markTicketAsUsed(
        @PathVariable String id,
        Authentication authentication) {
    Ticket ticket = ticketRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
    checkOwnership(ticket, authentication);

    if (Boolean.TRUE.equals(ticket.getIsUsed())) {
        throw new IllegalArgumentException("Ticket already used");
    }
    if (ticket.getValidUntil() != null
            && ticket.getValidUntil().isBefore(LocalDateTime.now())) {
        throw new IllegalArgumentException("Ticket has expired");
    }

    ticket.setIsUsed(true);
    return ResponseEntity.ok(ticketRepository.save(ticket));
}
```

Rules:

1. ticket exists
2. caller owns it (passenger rule)
3. not already used
4. not expired
5. set `isUsed = true`
6. save

Common test:

- first call -> `200`, `isUsed: true`
- second call -> `400 "Ticket already used"`
- expired ticket -> `400 "Ticket has expired"`

---

## 11. Delete ticket

```java
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public ResponseEntity<Void> deleteTicket(@PathVariable String id) {
    if (!ticketRepository.existsById(id)) {
        throw new IllegalArgumentException("Ticket not found");
    }
    ticketRepository.deleteById(id);
    return ResponseEntity.noContent().build();
}
```

Admin only. If a payment references the ticket, the database rejects the delete.

---

## 12. Request and response examples

### Passenger booking

```http
POST /api/tickets
Authorization: Basic priya:Passw0rd!
Content-Type: application/json
```

```json
{
  "sourceStationId": "STN-VSV",
  "destStationId": "STN-GHP",
  "ticketType": "SINGLE"
}
```

The passenger is inferred from `priya`.

Response `201`:

```json
{
  "ticketId": "TKT-<uuid>",
  "ticketType": "SINGLE",
  "isUsed": false,
  "issueTime": "2026-10-25T19:55:22",
  "createdAt": "2026-10-25T19:55:22",
  "validUntil": "2026-10-26T19:55:22",
  "passenger": {
    "userId": "PASS-SEED1",
    "contact": "priya",
    "firstName": "Priya",
    "lastName": "Sharma",
    "role": "PASSENGER"
  },
  "fare": {
    "fareId": "FARE-STN-VSV-STN-GHP",
    "baseFare": 40.00,
    "sourceStation": { "stationId": "STN-VSV" },
    "destStation": { "stationId": "STN-GHP" }
  },
  "sourceStation": { "stationId": "STN-VSV" },
  "destStation": { "stationId": "STN-GHP" }
}
```

The password is absent from `passenger`.

### Agent booking for a passenger

```json
{
  "passengerId": "PASS-SEED2",
  "sourceStationId": "STN-VSV",
  "destStationId": "STN-GHP",
  "ticketType": "SINGLE"
}
```

### Custom validity

```json
{
  "sourceStationId": "STN-VSV",
  "destStationId": "STN-GHP",
  "ticketType": "DAILY",
  "validUntil": "2026-10-26T23:59:59"
}
```

### Read own ticket

```http
GET /api/tickets/TKT-<uuid>
Authorization: Basic priya:Passw0rd!
```

### Read all (agent/admin only)

```http
GET /api/tickets
Authorization: Basic agent1:Passw0rd!
```

### Use

```http
PATCH /api/tickets/TKT-<uuid>/use
Authorization: Basic priya:Passw0rd!
```

---

## 13. Relations

### User

`Ticket.passenger -> User`.

Ownership checks use this relation.

### Fare

`Ticket.fare -> Fare`.

The fare is looked up at booking time.

### Stations

`Ticket.sourceStation` and `Ticket.destStation` point to `Station`.

### Payment

`Payment.ticket -> Ticket`.

One ticket can have multiple payment rows in the current schema; there is no uniqueness constraint on `Payment.TicketID`.

---

## 14. Seed data

`DataSeeder.seedTickets()` creates 5 tickets:

| ID | Passenger | From | To | Type | Used |
|---|---|---|---|---|---|
| `TKT-SEED1` | Priya | `STN-VSV` | `STN-WEH` | SINGLE | true |
| `TKT-SEED2` | Priya | `STN-BKC` | `STN-CSM` | ROUND_TRIP | true |
| `TKT-SEED3` | Priya | `STN-SKN` | `STN-GHP` | SINGLE | false |
| `TKT-SEED4` | Priya | `STN-MRL` | `STN-GHP` | DAILY | false |
| `TKT-SEED5` | Arjun | `STN-ARY` | `STN-BKC` | WEEKLY | false |

Their fares are looked up from the seeded fare table.

---

## 15. Errors

| Situation | Status | Message |
|---|---|---|
| Non-allowed role books | 403 | permission error |
| Missing source/dest | 400 | `Source and destination stations are required` |
| Same station | 400 | `Source and destination stations must be different` |
| Passenger ID missing | 400 | `Passenger ID is required` |
| Passenger not found | 400 | `Passenger not found` |
| Source station not found | 400 | `Source station not found` |
| Destination station not found | 400 | `Destination station not found` |
| Fare missing for pair | 400 | `Fare not found for this station pair` |
| Missing ticket type | 400 | `ticketType: Ticket type is required` |
| Ticket not found | 400 | `Ticket not found` |
| Another passenger's ticket | 403 | `You can only access your own tickets` |
| Ticket already used | 400 | `Ticket already used` |
| Ticket expired | 400 | `Ticket has expired` |
| Passenger lists all | 403 | permission error |

---

## 16. Swagger test

1. Authorize as `priya/Passw0rd!`.
2. `POST /api/tickets` with `STN-VSV` -> `STN-GHP`, type `SINGLE`.
3. Copy `ticketId`.
4. `GET /api/tickets/{id}` -> `200`.
5. `PATCH /api/tickets/{id}/use` -> `200`, `isUsed: true`.
6. Call use again -> `400`.
7. `GET /api/tickets` -> `403`.
8. Authorize as `agent1/Passw0rd!`, `GET /api/tickets` -> `200`.

---

## 17. Viva questions

**Q: How is the ticket price determined?**
A: The controller looks up `Fare` by source and destination. The ticket stores a reference to that fare.

**Q: Can a passenger book a ticket for someone else?**
A: No. For a passenger caller, the controller replaces `passengerId` with the authenticated user's ID.

**Q: How does an agent book for a passenger?**
A: The agent supplies `passengerId` in the request.

**Q: What is the default validity?**
A: 24 hours from issue if `validUntil` is omitted.

**Q: Can a ticket be used twice?**
A: No. `markTicketAsUsed` rejects a second use with 400.

**Q: What happens when a ticket expires?**
A: `use` returns `400 "Ticket has expired"`.

**Q: How is ownership enforced?**
A: `checkOwnership` compares `ticket.passenger.userId` with the current user's ID for passengers.

**Q: Why can't a passenger list all tickets?**
A: `GET /api/tickets` has `@PreAuthorize("hasAnyRole('ADMIN','TICKET_AGENT')")`.

**Q: Why can't a passenger see another passenger's tickets?**
A: The passenger path endpoint replaces the requested ID with their own, and the single-ticket endpoint checks ownership.

**Q: Why are the relations read-only in JSON?**
A: The client sends station/fare IDs; the server resolves the actual rows. This prevents nested-object overwrites.

**Q: Why is `passengerId` transient?**
A: The database stores `PassengerID` through the `passenger` relation. The transient field only carries the request value.

**Q: What happens if the fare is deleted?**
A: The database blocks deletion because tickets reference the fare.

**Q: What happens if the ticket is deleted?**
A: It works only if no payment references it.

**Q: Does the ticket store the train?**
A: No. Tickets reference a fare and stations, not a train or schedule.

**Q: Is `ticketType` validated against a list?**
A: No. It is free text with `@NotBlank`. A production system would use an enum.

**Q: Why is `@Transactional` on create/use/delete?**
A: They modify data and must commit or roll back as one unit.

**Q: Why is `@Transactional(readOnly=true)` on reads?**
A: They only read; read-only is an optimization.

---

## 18. One-line summary

`Ticket` is the booking record: it links a passenger, a fare, and two stations, stores validity and used status, and enforces that passengers can only see and use their own tickets.
