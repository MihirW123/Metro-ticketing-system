# 10 — Payment Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/Payment.java` |
| Controller | `src/main/java/com/metro/ticketing/controller/PaymentController.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/PaymentRepository.java` |
| Table | `V1__create_metro_ticketing_schema.sql` |
| Related | Ticket, User |

---

## 1. What a Payment represents

A payment is a record that money was paid for a ticket:

```text
Payment PAY-<uuid>
Ticket  TKT-<uuid>
Amount  40.00
Time    now
```

It does not integrate with a real payment gateway. It is a database record used to demonstrate the ticket → payment relationship.

A payment belongs to exactly one ticket.

---

## 2. Database table

```sql
CREATE TABLE "Payment" (
    "PaymentID" VARCHAR(50) PRIMARY KEY,
    "TicketID" VARCHAR(50) NOT NULL,
    "Amount" NUMERIC(10,2) NOT NULL,
    "CreatedAt" TIMESTAMP,
    CONSTRAINT "FK_Payment_Ticket"
        FOREIGN KEY ("TicketID") REFERENCES "Tickets" ("TicketID")
);
```

| Column | Meaning |
|---|---|
| `PaymentID` | Primary key, `PAY-<uuid>` |
| `TicketID` | FK to `Tickets`, required |
| `Amount` | Paid amount, `NUMERIC(10,2)` |
| `CreatedAt` | Payment time |

There is no unique constraint on `TicketID`, so the schema currently allows multiple payments for one ticket. The controller does not prevent it either.

---

## 3. Entity walkthrough

```java
@Entity
@Table(name = "\"Payment\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Payment {

    @Id
    @Column(name = "\"PaymentID\"", length = 50)
    private String paymentId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"TicketID\"", referencedColumnName = "\"TicketID\"", nullable = false)
    private Ticket ticket;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String ticketId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Column(name = "\"Amount\"", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "\"CreatedAt\"")
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### Ticket relation

`ticket` is `@ManyToOne` to `Ticket`.

It is `READ_ONLY` in JSON: responses show the ticket, but requests send `ticketId`.

### Transient ticket ID

```java
@Transient
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String ticketId;
```

Request:

```json
{"ticketId": "TKT-<uuid>", "amount": 40.00}
```

The controller loads the real ticket.

### Amount

- `BigDecimal`
- `@NotNull`
- `@DecimalMin("0.01")`
- maps to `NUMERIC(10,2)`

### Created time

Defaults to `LocalDateTime.now()`; the controller also sets it explicitly.

---

## 4. Repository

```java
@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    List<Payment> findByTicket_TicketId(String ticketId);
}
```

Derived query:

```sql
SELECT * FROM "Payment" WHERE "TicketID" = ?
```

Used by `GET /api/payments/ticket/{ticketId}`.

---

## 5. Controller endpoints

Base path: `/api/payments`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/payments` | PASSENGER, TICKET_AGENT | Create payment |
| GET | `/api/payments/{id}` | authenticated + ownership | Read one |
| GET | `/api/payments` | ADMIN, TICKET_AGENT | List all |
| GET | `/api/payments/ticket/{ticketId}` | authenticated + ownership | Payments for a ticket |
| DELETE | `/api/payments/{id}` | ADMIN | Delete |

Notice: `ADMIN` cannot create a payment. Only `PASSENGER` and `TICKET_AGENT` can.

---

## 6. Create payment — complete flow

```java
@PostMapping
@PreAuthorize("hasAnyRole('PASSENGER', 'TICKET_AGENT')")
@Transactional
public ResponseEntity<Payment> createPayment(
        @Valid @RequestBody Payment request,
        Authentication authentication) {
```

### Step 1 — require ticket ID

```java
if (request.getTicketId() == null) {
    throw new IllegalArgumentException("Ticket ID is required");
}
```

### Step 2 — load ticket

```java
Ticket ticket = ticketRepository.findById(request.getTicketId())
    .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
```

### Step 3 — ownership

```java
if (isPassenger(authentication)
        && !ticket.getPassenger().getUserId()
                .equals(currentUser(authentication).getUserId())) {
    throw new AccessDeniedException(
        "You can only make payments for your own tickets");
}
```

- Passenger: only their own ticket.
- Agent: any ticket.

### Step 4 — amount must match the fare

```java
if (ticket.getFare() != null
        && request.getAmount().compareTo(ticket.getFare().getBaseFare()) != 0) {
    throw new IllegalArgumentException(
        "Payment amount does not match the ticket fare");
}
```

`compareTo` is used instead of `equals` because `BigDecimal.equals` also compares scale:

```text
40.0 != 40.00 with equals
40.0 == 40.00 with compareTo
```

### Step 5 — build and save

```java
Payment payment = new Payment();
payment.setPaymentId("PAY-" + UUID.randomUUID());
payment.setTicket(ticket);
payment.setAmount(request.getAmount());
payment.setCreatedAt(LocalDateTime.now());

return ResponseEntity.status(HttpStatus.CREATED)
    .body(paymentRepository.save(payment));
```

Returns `201`.

---

## 7. Read payment

```java
@GetMapping("/{id}")
@Transactional(readOnly = true)
public ResponseEntity<Payment> getPaymentById(
        @PathVariable String id,
        Authentication authentication) {
    Payment payment = paymentRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
    checkOwnership(payment, authentication);
    return ResponseEntity.ok(payment);
}
```

Ownership is indirect:

```text
Payment -> Ticket -> Passenger -> UserId
```

```java
private void checkOwnership(Payment payment, Authentication authentication) {
    if (isPassenger(authentication)
            && !payment.getTicket().getPassenger().getUserId()
                    .equals(currentUser(authentication).getUserId())) {
        throw new AccessDeniedException(
            "You can only access payments for your own tickets");
    }
}
```

---

## 8. List all payments

```java
@GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'TICKET_AGENT')")
public ResponseEntity<List<Payment>> getAllPayments() {
    return ResponseEntity.ok(paymentRepository.findAll());
}
```

Passengers get `403`.

---

## 9. Payments for one ticket

```java
@GetMapping("/ticket/{ticketId}")
public ResponseEntity<List<Payment>> getPaymentsByTicket(
        @PathVariable String ticketId,
        Authentication authentication) {
    Ticket ticket = ticketRepository.findById(ticketId)
        .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

    if (isPassenger(authentication)
            && !ticket.getPassenger().getUserId()
                    .equals(currentUser(authentication).getUserId())) {
        throw new AccessDeniedException(
            "You can only view payments for your own tickets");
    }

    return ResponseEntity.ok(paymentRepository.findByTicket_TicketId(ticketId));
}
```

A passenger can only list payments for their own ticket. An agent can list any.

---

## 10. Delete payment

```java
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public ResponseEntity<Void> deletePayment(@PathVariable String id) {
    if (!paymentRepository.existsById(id)) {
        throw new IllegalArgumentException("Payment not found");
    }
    paymentRepository.deleteById(id);
    return ResponseEntity.noContent().build();
}
```

Admin only. `204 No Content`.

---

## 11. Request and response examples

### Create

```http
POST /api/payments
Authorization: Basic priya:Passw0rd!
Content-Type: application/json
```

```json
{
  "ticketId": "TKT-SEED1",
  "amount": 20.00
}
```

The amount must match `TKT-SEED1.fare.baseFare`.

Response `201`:

```json
{
  "paymentId": "PAY-<uuid>",
  "amount": 20.00,
  "createdAt": "2026-10-25T...",
  "ticket": {
    "ticketId": "TKT-SEED1",
    "ticketType": "SINGLE",
    "passenger": {
      "userId": "PASS-SEED1",
      "contact": "priya"
    },
    "fare": {
      "fareId": "FARE-STN-VSV-STN-WEH",
      "baseFare": 20.00
    }
  }
}
```

### Read

```http
GET /api/payments/PAY-<uuid>
Authorization: Basic priya:Passw0rd!
```

### List all

```http
GET /api/payments
Authorization: Basic agent1:Passw0rd!
```

### By ticket

```http
GET /api/payments/ticket/TKT-SEED1
Authorization: Basic priya:Passw0rd!
```

---

## 12. Relations

### Ticket

`Payment.ticket -> Ticket` is the only relation.

### User

There is no direct `User` relation. Ownership goes through the ticket.

### Ticket deletion

If a payment references a ticket, deleting the ticket fails due to the FK. Delete payments first or keep the history.

---

## 13. Seed data

`DataSeeder.seedPayments()` creates 3 payments:

| Payment | Ticket | Amount |
|---|---|---:|
| `PAY-SEED1` | `TKT-SEED1` | 20.00 |
| `PAY-SEED2` | `TKT-SEED2` | 50.00 |
| `PAY-SEED3` | `TKT-SEED3` | 10.00 |

---

## 14. Errors

| Situation | Status | Message |
|---|---|---|
| Admin tries to create | 403 | permission error |
| Missing ticket ID | 400 | `Ticket ID is required` |
| Ticket not found | 400 | `Ticket not found` |
| Passenger pays another's ticket | 403 | `You can only make payments for your own tickets` |
| Amount missing | 400 | `amount: Amount is required` |
| Amount <= 0 | 400 | `amount: Amount must be greater than zero` |
| Amount != fare | 400 | `Payment amount does not match the ticket fare` |
| Payment not found | 400 | `Payment not found` |
| Passenger reads another's payment | 403 | `You can only access payments for your own tickets` |
| Passenger lists all payments | 403 | permission error |
| Passenger lists another ticket's payments | 403 | `You can only view payments for your own tickets` |

---

## 15. Swagger test

1. Authorize as `priya/Passw0rd!`.
2. `POST /api/payments` with `TKT-SEED1`, amount `20.00` -> `201`.
3. Try a wrong amount, e.g. `10.00` -> `400`.
4. `GET /api/payments/{id}` -> `200`.
5. Try `GET /api/payments` -> `403`.
6. Authorize as `agent1/Passw0rd!`, `GET /api/payments` -> `200`.
7. As `admin`, `DELETE /api/payments/{id}` -> `204`.

---

## 16. Viva questions

**Q: Why does payment go through a ticket instead of a user?**
A: A payment is for a ticket. The ticket already identifies the passenger, so the payment only needs `TicketID`.

**Q: How is ownership checked?**
A: `Payment -> Ticket -> Passenger -> UserId`, compared with the authenticated user.

**Q: Why compare amounts with `compareTo`?**
A: `BigDecimal.equals` also compares scale. `40.0` and `40.00` are different for `equals` but equal for `compareTo`.

**Q: Can a passenger pay for someone else's ticket?**
A: No, the controller rejects it with 403.

**Q: Can an agent pay for any ticket?**
A: Yes, ticket agents can create payments for any ticket.

**Q: Can an admin create a payment?**
A: Not with the current `@PreAuthorize`; only passenger and ticket agent.

**Q: Can one ticket have multiple payments?**
A: The schema allows it; there is no unique constraint on `TicketID`.

**Q: Does the payment call a real gateway?**
A: No. It only saves a database row.

**Q: What happens if the ticket is deleted?**
A: The database blocks it if a payment references it.

**Q: Why is `ticketId` transient?**
A: The database stores the ticket relation; the request sends an ID.

**Q: Why is `ticket` read-only in JSON?**
A: Requests should not overwrite the relation with nested objects.

**Q: Why `@Transactional` on create/delete?**
A: They write data and must commit or roll back atomically.

**Q: What is the payment ID format?**
A: `PAY-<uuid>`.

---

## 17. One-line summary

`Payment` records that a specific amount was paid for a specific ticket; it enforces ownership through the ticket and requires the amount to match the fare.
