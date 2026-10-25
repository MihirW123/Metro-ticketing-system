# 11 — Metro Card Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/MetroCard.java` |
| Controller | `src/main/java/com/metro/ticketing/controller/MetroCardController.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/MetroCardRepository.java` |
| Table | `V1__create_metro_ticketing_schema.sql` |
| Related | User |

---

## 1. What a MetroCard represents

A metro card is a stored-value card belonging to a passenger:

```text
Card CARD-<uuid>
Passenger Priya
Balance 250.00
Issued 2026-10-25
Expires 2031-10-25
Active yes
```

It supports:

- issuing a card
- topping up the balance
- reading card details
- deactivating a card
- deleting a card (admin)

A passenger can have only **one active card** at a time.

---

## 2. Database table

```sql
CREATE TABLE "MetroCards" (
    "CardID" VARCHAR(50) PRIMARY KEY,
    "PassengerID" VARCHAR(50) NOT NULL,
    "Balance" NUMERIC(10,2) DEFAULT 0,
    "IssueDate" DATE,
    "ExpiryDate" DATE,
    "CreatedAt" TIMESTAMP,
    "isActive" BOOLEAN DEFAULT TRUE,
    CONSTRAINT "FK_MetroCard_Passenger"
        FOREIGN KEY ("PassengerID") REFERENCES "User" ("UserId")
);
```

| Column | Meaning |
|---|---|
| `CardID` | Primary key, `CARD-<uuid>` |
| `PassengerID` | FK to `User`, required |
| `Balance` | Money, `NUMERIC(10,2)` |
| `IssueDate` | Date issued |
| `ExpiryDate` | Date it stops working |
| `CreatedAt` | Row creation time |
| `isActive` | Deactivation flag |

There is no database unique constraint on active cards. The controller enforces the one-active-card rule with a query.

---

## 3. Entity walkthrough

```java
@Entity
@Table(name = "\"MetroCards\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MetroCard {

    @Id
    private String cardId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"PassengerID\"", referencedColumnName = "\"UserId\"", nullable = false)
    private User passenger;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passengerId;

    private BigDecimal balance = BigDecimal.ZERO;
    private LocalDate issueDate = LocalDate.now();
    private LocalDate expiryDate;
    private LocalDateTime createdAt = LocalDateTime.now();
    private Boolean isActive = true;
}
```

### Passenger relation

`passenger` is `@ManyToOne` to `User` and `READ_ONLY` in JSON.

Requests send `passengerId`; the controller loads the user.

### Balance

`BigDecimal`, initialized to `BigDecimal.ZERO`.

Why `BigDecimal`:

- money
- exact decimal addition
- no floating-point rounding

Top-up:

```java
card.setBalance(card.getBalance().add(amount));
```

### Dates

| Field | Default |
|---|---|
| `issueDate` | today |
| `expiryDate` | today + 5 years if omitted |
| `createdAt` | now |

### Active flag

`isActive = true` by default. Deactivation sets it to `false`.

---

## 4. Repository

```java
@Repository
public interface MetroCardRepository extends JpaRepository<MetroCard, String> {

    List<MetroCard> findByPassenger_UserId(String passengerId);

    Optional<MetroCard> findByPassenger_UserIdAndIsActiveTrue(String passengerId);
}
```

### `findByPassenger_UserId`

Lists all cards for a passenger.

### `findByPassenger_UserIdAndIsActiveTrue`

Used by card creation to check whether the passenger already has an active card.

Derived SQL:

```sql
SELECT * FROM "MetroCards"
WHERE "PassengerID" = ? AND "isActive" = TRUE
```

---

## 5. Controller endpoints

Base path: `/api/cards`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/cards` | PASSENGER, TICKET_AGENT, ADMIN | Issue card |
| POST | `/api/cards/{id}/topup?amount=` | PASSENGER, TICKET_AGENT, ADMIN | Add balance |
| GET | `/api/cards/{id}` | authenticated + ownership | Read card |
| GET | `/api/cards` | ADMIN, TICKET_AGENT | List all cards |
| GET | `/api/cards/passenger/{passengerId}` | authenticated + ownership | Passenger's cards |
| PATCH | `/api/cards/{id}/deactivate` | ADMIN, TICKET_AGENT | Deactivate |
| DELETE | `/api/cards/{id}` | ADMIN | Delete |

Notice that top-up uses a **query parameter**, not a JSON body:

```http
POST /api/cards/CARD-SEED1/topup?amount=100
```

---

## 6. Issue card — step by step

```java
@PostMapping
@PreAuthorize("hasAnyRole('PASSENGER', 'TICKET_AGENT', 'ADMIN')")
@Transactional
public ResponseEntity<MetroCard> createMetroCard(
        @Valid @RequestBody MetroCard request,
        Authentication authentication) {
```

### Step 1 — decide passenger

```java
String passengerId = request.getPassengerId();
if (isPassenger(authentication)) {
    passengerId = currentUser(authentication).getUserId();
}
if (passengerId == null || passengerId.isBlank()) {
    throw new IllegalArgumentException("Passenger ID is required");
}
```

| Caller | Passenger |
|---|---|
| PASSENGER | authenticated user |
| TICKET_AGENT | `request.passengerId` |
| ADMIN | `request.passengerId` |

### Step 2 — load passenger

```java
User passenger = userRepository.findById(passengerId)
    .orElseThrow(() -> new IllegalArgumentException("Passenger not found"));
```

### Step 3 — one active card rule

```java
if (metroCardRepository
        .findByPassenger_UserIdAndIsActiveTrue(passengerId)
        .isPresent()) {
    throw new IllegalArgumentException(
        "Passenger already has an active metro card");
}
```

The passenger can still have inactive cards, but only one active card.

### Step 4 — build the card

```java
MetroCard card = new MetroCard();
card.setCardId("CARD-" + UUID.randomUUID());
card.setPassenger(passenger);
card.setBalance(request.getBalance() != null
    ? request.getBalance() : BigDecimal.ZERO);
card.setIssueDate(LocalDate.now());
card.setCreatedAt(LocalDateTime.now());
card.setIsActive(true);
card.setExpiryDate(request.getExpiryDate() != null
    ? request.getExpiryDate() : LocalDate.now().plusYears(5));
```

### Step 5 — save and return `201`.

---

## 7. Top up — step by step

```java
@PostMapping("/{id}/topup")
@PreAuthorize("hasAnyRole('PASSENGER', 'TICKET_AGENT', 'ADMIN')")
@Transactional
public ResponseEntity<MetroCard> topUpCard(
        @PathVariable String id,
        @RequestParam BigDecimal amount,
        Authentication authentication) {
```

### Step 1 — amount must be positive

```java
if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
    throw new IllegalArgumentException(
        "Top-up amount must be greater than zero");
}
```

### Step 2 — load card

```java
MetroCard card = metroCardRepository.findById(id)
    .orElseThrow(() -> new IllegalArgumentException("Metro card not found"));
```

### Step 3 — ownership

```java
checkOwnership(card, authentication);
```

Passengers can only top up their own cards. Agents/admins can top up any card.

### Step 4 — active and not expired

```java
if (!Boolean.TRUE.equals(card.getIsActive())) {
    throw new IllegalArgumentException("Metro card is inactive");
}
if (card.getExpiryDate() != null
        && card.getExpiryDate().isBefore(LocalDate.now())) {
    throw new IllegalArgumentException("Metro card has expired");
}
```

### Step 5 — add balance

```java
card.setBalance(card.getBalance().add(amount));
return ResponseEntity.ok(metroCardRepository.save(card));
```

Returns `200` with the updated card.

---

## 8. Read card

```java
@GetMapping("/{id}")
@Transactional(readOnly = true)
public ResponseEntity<MetroCard> getMetroCardById(
        @PathVariable String id,
        Authentication authentication) {
    MetroCard card = metroCardRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Metro card not found"));
    checkOwnership(card, authentication);
    return ResponseEntity.ok(card);
}
```

Ownership:

```java
if (isPassenger(authentication)
        && !card.getPassenger().getUserId()
                .equals(currentUser(authentication).getUserId())) {
    throw new AccessDeniedException(
        "You can only access your own metro cards");
}
```

---

## 9. List all cards

```java
@GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'TICKET_AGENT')")
public ResponseEntity<List<MetroCard>> getAllMetroCards() {
    return ResponseEntity.ok(metroCardRepository.findAll());
}
```

Passengers get `403`.

---

## 10. Cards by passenger

```java
@GetMapping("/passenger/{passengerId}")
public ResponseEntity<List<MetroCard>> getMetroCardsByPassenger(
        @PathVariable String passengerId,
        Authentication authentication) {
    if (isPassenger(authentication)) {
        passengerId = currentUser(authentication).getUserId();
    }
    return ResponseEntity.ok(metroCardRepository.findByPassenger_UserId(passengerId));
}
```

A passenger who asks for another passenger's ID is silently redirected to their own ID.

---

## 11. Deactivate

```java
@PatchMapping("/{id}/deactivate")
@PreAuthorize("hasAnyRole('ADMIN', 'TICKET_AGENT')")
@Transactional
public ResponseEntity<MetroCard> deactivateMetroCard(@PathVariable String id) {
    MetroCard card = metroCardRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Metro card not found"));
    card.setIsActive(false);
    return ResponseEntity.ok(metroCardRepository.save(card));
}
```

Only admin/agent. The card still exists, but:

- top-up rejects it as inactive
- it no longer counts as the passenger's active card

---

## 12. Delete

```java
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public ResponseEntity<Void> deleteMetroCard(@PathVariable String id) {
    if (!metroCardRepository.existsById(id)) {
        throw new IllegalArgumentException("Metro card not found");
    }
    metroCardRepository.deleteById(id);
    return ResponseEntity.noContent().build();
}
```

Admin only. No other table references a metro card, so deletion is safe.

---

## 13. Request and response examples

### Issue

```http
POST /api/cards
Authorization: Basic priya:Passw0rd!
Content-Type: application/json
```

```json
{
  "balance": 100.00,
  "expiryDate": "2031-10-25"
}
```

A passenger can omit `passengerId`; it is taken from the login.

Response `201`:

```json
{
  "cardId": "CARD-<uuid>",
  "balance": 100.00,
  "issueDate": "2026-10-25",
  "expiryDate": "2031-10-25",
  "createdAt": "2026-10-25T...",
  "isActive": true,
  "passenger": {
    "userId": "PASS-SEED1",
    "contact": "priya"
  }
}
```

### Issue for a passenger as an agent

```json
{
  "passengerId": "PASS-SEED2",
  "balance": 50.00
}
```

### Top up

```http
POST /api/cards/CARD-SEED1/topup?amount=100
Authorization: Basic priya:Passw0rd!
```

Response `200`:

```json
{
  "cardId": "CARD-SEED1",
  "balance": 350.00,
  "...": "..."
}
```

### Read

```http
GET /api/cards/CARD-SEED1
Authorization: Basic priya:Passw0rd!
```

### Deactivate

```http
PATCH /api/cards/CARD-SEED1/deactivate
Authorization: Basic agent1:Passw0rd!
```

---

## 14. Relations

### User

`MetroCard.passenger -> User`.

A user can have several card rows over time, but only one active card.

### Tickets and payments

Metro cards are independent of tickets and payments in this schema. A card is not automatically used to pay for a ticket. This is a possible future enhancement.

---

## 15. Seed data

`DataSeeder.seedCards()` creates 4 cards:

| Card | Passenger | Balance | Active | Expiry |
|---|---|---:|---|---|
| `CARD-SEED1` | Priya | 250.00 | true | +3 years |
| `CARD-SEED2` | Priya | 0.00 | false | +2 months |
| `CARD-SEED3` | Arjun | 120.00 | true | +2 years |
| `CARD-SEED4` | Meera | 75.50 | true | +4 years |

Priya has two cards, but only one is active. This demonstrates the rule.

---

## 16. Errors

| Situation | Status | Message |
|---|---|---|
| Unauthorized role for issue | 403 | permission error |
| Passenger ID missing | 400 | `Passenger ID is required` |
| Passenger not found | 400 | `Passenger not found` |
| Already has active card | 400 | `Passenger already has an active metro card` |
| Card not found | 400 | `Metro card not found` |
| Top-up amount missing | 400 | missing `amount` parameter |
| Top-up amount <= 0 | 400 | `Top-up amount must be greater than zero` |
| Passenger tops up another card | 403 | `You can only access your own metro cards` |
| Inactive card top-up | 400 | `Metro card is inactive` |
| Expired card top-up | 400 | `Metro card has expired` |
| Passenger lists all cards | 403 | permission error |
| Passenger reads another card | 403 | ownership error |

---

## 17. Swagger test

1. Authorize as `priya/Passw0rd!`.
2. `POST /api/cards/CARD-SEED1/topup?amount=100` -> `200`, balance +100.
3. `GET /api/cards/CARD-SEED1` -> `200`.
4. Try `GET /api/cards` -> `403`.
5. Authorize as `agent1/Passw0rd!`.
6. `GET /api/cards` -> `200`.
7. `PATCH /api/cards/CARD-SEED1/deactivate` -> `200`.
8. Try top-up again -> `400 "Metro card is inactive"`.

---

## 18. Viva questions

**Q: Why is balance `BigDecimal`?**
A: Money must be exact; `double` can introduce rounding errors.

**Q: How is the one-active-card rule enforced?**
A: `findByPassenger_UserIdAndIsActiveTrue` checks before creating a new card.

**Q: Is that rule enforced by the database?**
A: No. It is enforced in the controller. A database partial unique index would be stronger.

**Q: Why can Priya have two cards in the seed data?**
A: One is inactive. The rule is one **active** card.

**Q: Can a passenger top up another passenger's card?**
A: No. `checkOwnership` rejects it with 403.

**Q: Can an agent top up any card?**
A: Yes, agents and admins are not subject to the ownership check.

**Q: Why is top-up a query parameter?**
A: It is a simple single value; the API uses `?amount=100` instead of a body.

**Q: What happens to an inactive card?**
A: It remains in the database but cannot be topped up. It can be reactivated only by issuing a new active card (the old one stays inactive).

**Q: What happens to an expired card?**
A: Top-up is rejected with `Metro card has expired`.

**Q: Does the card pay for tickets automatically?**
A: No. Cards and tickets are separate features in the current design.

**Q: Why is `passengerId` transient?**
A: Requests send it, but the database stores the `passenger` relation.

**Q: Why is `passenger` read-only in JSON?**
A: To prevent request JSON from overwriting the relation with a nested user object.

**Q: What is the card ID format?**
A: `CARD-<uuid>`.

**Q: Why can only admins delete a card?**
A: Deletion is destructive. Agents can deactivate instead.

---

## 19. One-line summary

`MetroCard` stores a passenger's balance and expiry; the controller issues one active card per passenger, allows top-ups on valid cards, and restricts passengers to their own cards.
