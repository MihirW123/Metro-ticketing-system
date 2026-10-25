# 01 — User Group

**Files**

| File | Path |
|---|---|
| Entity | `src/main/java/com/metro/ticketing/entity/User.java` |
| Role enum | `src/main/java/com/metro/ticketing/entity/Role.java` |
| Repository | `src/main/java/com/metro/ticketing/repository/UserRepository.java` |
| Created by | `AuthController.java`, `DataSeeder.java` |
| Used by | `DbUserDetailsService`, `TicketController`, `MetroCardController`, `PaymentController`, `MaintenanceIssueController`, `DataSeeder` |

---

## 1. What the User represents

`User` is the identity table. Every person in the system is one row:

- passenger
- ticket agent
- maintenance staff
- admin

A user has exactly one role. The `Contact` field is the username used for Basic auth. Passwords are stored as BCrypt hashes, never plain text.

The user row is also the anchor for ownership:

- tickets belong to a passenger (`Ticket.passenger`)
- metro cards belong to a passenger (`MetroCard.passenger`)
- maintenance issues have a reporter (`MaintenanceIssue.reportedBy`)
- staff can be assigned to a station (`User.station`)

---

## 2. Database table

Created in `V1__create_metro_ticketing_schema.sql`.

```sql
CREATE TABLE "User" (
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
    CONSTRAINT "FK_User_Station"
        FOREIGN KEY ("StationID") REFERENCES "Stations" ("StationID")
);

CREATE UNIQUE INDEX "UX_User_Contact" ON "User" ("Contact");
```

| Column | Type | Meaning |
|---|---|---|
| `UserId` | varchar(50) | Primary key, e.g. `PASS-...`, `EMP-...`, `ADMIN-...` |
| `FirstName` | varchar(100) | Required |
| `LastName` | varchar(100) | Required |
| `Password` | varchar(255) | BCrypt hash |
| `Role` | varchar(50) | `ADMIN`, `TICKET_AGENT`, `MAINTENANCE_STAFF`, `PASSENGER` |
| `Contact` | varchar(50) | Login username; unique index |
| `RegisterationDate` | date | When the account was created |
| `isActive` | boolean | Inactive users cannot log in |
| `HireDate` | date | Staff only |
| `StationID` | varchar(50) | Staff's assigned station, optional |

Note the spelling `RegisterationDate` — it comes from the original schema and is kept so the database column name stays exact.

---

## 3. Entity field-by-field walkthrough

```java
@Entity
@Table(name = "\"User\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {
```

- `@Entity` tells JPA this class is a table.
- `@Table(name = "\"User\"")` uses an escaped quoted identifier, so PostgreSQL keeps the exact name `User`. Without quotes, PostgreSQL would fold it to `user`.
- `@JsonIgnoreProperties` hides Hibernate proxy fields from JSON.

### ID

```java
@Id
@Column(name = "\"UserId\"", length = 50)
private String userId;
```

The ID is generated in the controller:

| Creation path | Prefix |
|---|---|
| Passenger register | `PASS-<uuid>` |
| Employee hire | `EMP-<uuid>` |
| Bootstrap admin | `ADMIN-<uuid>` |

### Names

```java
@NotBlank(message = "First name is required")
private String firstName;

@NotBlank(message = "Last name is required")
private String lastName;
```

`@NotBlank` rejects null, empty, and whitespace-only values.

### Password

```java
@NotBlank(message = "Password is required")
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
@Column(name = "\"Password\"", length = 255, nullable = false)
private String password;
```

- `@JsonProperty(WRITE_ONLY)` means:
  - JSON input: accepted for register/hire
  - JSON output: **never included**
- The value stored is a BCrypt hash, e.g. `$2a$10$...`, not `Passw0rd!`.

### Role

```java
@Enumerated(EnumType.STRING)
@Column(name = "\"Role\"", length = 50, nullable = false)
private Role role;
```

`EnumType.STRING` stores the name `PASSENGER`, not an ordinal number. This keeps the database readable and stable if enum order changes.

### Contact

```java
@NotBlank(message = "Contact is required")
@Column(name = "\"Contact\"", length = 50)
private String contact;
```

This is the login username. `UserRepository.findByContact(contact)` is used by:

- `DbUserDetailsService` during Basic auth
- `AuthController` for duplicate checks and `/api/auth/me`
- ownership helpers in ticket/card/payment controllers

The unique index `UX_User_Contact` prevents duplicate contacts at the database level.

### Dates and active flag

```java
private LocalDate registrationDate = LocalDate.now();
private Boolean isActive = true;
private LocalDate hireDate;
```

- `registrationDate` is set on creation.
- `isActive` defaults to `true`; `DbUserDetailsService` maps it to `disabled(...)`.
- `hireDate` is set only when an employee is hired.

### Station relation

```java
@JsonProperty(access = JsonProperty.Access.READ_ONLY)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "\"StationID\"", referencedColumnName = "\"StationID\"")
private Station station;

@Transient
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String stationId;
```

- `station` is the actual `Station` object. It is read-only in JSON, so responses show the station but requests cannot overwrite it with a nested object.
- `stationId` is transient: it is **not a column**. It lets the hire request send `"stationId": "STN-AND"`.
- `@ManyToOne(LAZY)` means the station is loaded only when needed.
- The FK allows `NULL` because passengers are not assigned to a station.

---

## 4. Repository

```java
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByContact(String contact);
}
```

From `JpaRepository<User, String>`:

| Method | Meaning |
|---|---|
| `save(user)` | insert or update |
| `findById(id)` | lookup by `UserId` |
| `findAll()` | all users |
| `existsById(id)` | existence check |
| `deleteById(id)` | delete |
| `count()` | row count |

Custom:

```java
Optional<User> findByContact(String contact);
```

Spring Data generates:

```sql
SELECT * FROM "User" WHERE "Contact" = ?
```

Why `Optional`: the contact may not exist. Callers decide whether to throw:

```java
userRepository.findByContact(contact)
    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + contact));
```

---

## 5. Where users are created

### Bootstrap admin

`DataSeeder.ensureAdminExists()` runs on every startup:

```java
if (userRepository.findByContact(adminContact).isPresent()) {
    return;
}
```

If missing, it creates:

- ID `ADMIN-<uuid>`
- contact from `app.bootstrap.admin.contact` (`admin`)
- password from `app.bootstrap.admin.password` (`admin123`), BCrypt-encoded
- role `ADMIN`
- active `true`

This is why the app always has an admin on a fresh database.

### Passenger registration

`POST /api/auth/passenger/register`:

- checks contact uniqueness
- creates `PASS-<uuid>`
- encodes password
- forces role `PASSENGER`
- sets registration date and active flag

### Employee hiring

`POST /api/auth/employee/hire` (admin only):

- checks contact uniqueness
- requires role and station ID
- creates `EMP-<uuid>`
- sets role, hire date, registration date, station

### Demo users

`DataSeeder.seedUsers()` creates:

| ID | Name | Role | Contact | Password |
|---|---|---|---|---|
| `STAFF-01` | Rahul Mehta | `TICKET_AGENT` | `agent1` | `Passw0rd!` |
| `STAFF-02` | Suresh Kumar | `MAINTENANCE_STAFF` | `maint1` | `Passw0rd!` |
| `PASS-SEED1` | Priya Sharma | `PASSENGER` | `priya` | `Passw0rd!` |
| `PASS-SEED2` | Arjun Rao | `PASSENGER` | `arjun` | `Passw0rd!` |
| `PASS-SEED3` | Meera Singh | `PASSENGER` | `meera` | `Passw0rd!` |

---

## 6. How other groups use User

### Auth group

- `DbUserDetailsService` loads by contact.
- `AuthController` creates users.
- `SecurityConfig` turns `role` into `ROLE_<role>`.

### Ticket group

`Ticket.passenger` is a `User`.

Creation:

- passenger caller → passenger ID is taken from the authenticated user
- agent/admin caller → `passengerId` must be supplied

Ownership:

```java
ticket.getPassenger().getUserId().equals(currentUser(authentication).getUserId())
```

### MetroCard group

`MetroCard.passenger` is a `User`. A passenger can only see/use their own card.

### Payment group

Payment ownership is indirect:

```text
Payment -> Ticket -> User
```

### Maintenance group

`MaintenanceIssue.reportedBy` is an optional `User`.

### Station group

`User.station` is optional and only used for staff.

---

## 7. API examples

There is no generic `/api/users` controller. Users are created through auth endpoints.

### Register

```http
POST /api/auth/passenger/register
Content-Type: application/json

{
  "firstName": "Priya",
  "lastName": "Sharma",
  "contact": "priya2",
  "password": "Passw0rd!"
}
```

Response `200`:

```json
{
  "userId": "PASS-<uuid>",
  "firstName": "Priya",
  "lastName": "Sharma",
  "contact": "priya2",
  "role": "PASSENGER",
  "registrationDate": "2026-10-25",
  "isActive": true,
  "hireDate": null,
  "station": null
}
```

The password is absent.

### Hire

```http
POST /api/auth/employee/hire
Authorization: Basic admin:admin123
Content-Type: application/json

{
  "firstName": "Rahul",
  "lastName": "Mehta",
  "contact": "agent2",
  "password": "Passw0rd!",
  "role": "TICKET_AGENT",
  "stationId": "STN-AND"
}
```

Response includes `station` and `hireDate`, but no password.

### Current user

```http
GET /api/auth/me
Authorization: Basic priya:Passw0rd!
```

Returns the authenticated user's row.

---

## 8. Validation and errors

| Situation | Status | Message |
|---|---|---|
| Missing first name | 400 | `firstName: First name is required` |
| Missing last name | 400 | `lastName: Last name is required` |
| Missing contact | 400 | `contact: Contact is required` |
| Missing password | 400 | `password: Password is required` |
| Contact already exists | 400 | `User with this contact already exists.` |
| Hire without role | 400 | `Role is required.` |
| Hire without station | 400 | `Station is required.` |
| Station not found | 400 | `Station not found.` |
| Wrong password | 401 | Basic auth entry point |
| Inactive user | 401 | disabled account |

Important: Hibernate also validates entities on insert/update. That is why `@NotBlank` is placed on real columns, not on transient request-only fields.

---

## 9. Swagger test

1. Authorize as `admin` / `admin123`.
2. `POST /api/auth/employee/hire` with the hire body above.
3. Authorize as the new employee.
4. `GET /api/auth/me` -> returns the new employee.
5. Try `POST /api/auth/employee/hire` as the employee -> `403`.

---

## 10. Viva questions

**Q: Why is `Contact` the username instead of a separate `username` column?**
A: The original design uses contact (phone/email) as the login identity. `Custom` lookups use `findByContact`.

**Q: Why is the password field not returned?**
A: `@JsonProperty(access = WRITE_ONLY)` prevents serialization, so the BCrypt hash never leaves the server.

**Q: Is the password encrypted or hashed?**
A: Hashed with BCrypt. It is one-way; the original password cannot be recovered.

**Q: Why `@Enumerated(EnumType.STRING)`?**
A: The database stores `PASSENGER` instead of `3`, which is readable and safe if enum order changes.

**Q: Why does `User` have a `Station` relation?**
A: Staff can be assigned to a station. Passengers leave it null.

**Q: Why is `stationId` transient?**
A: Requests send an ID, but the database stores a foreign key through the `station` relation. The transient field avoids an extra column.

**Q: Why can't a user be deleted?**
A: There is no delete endpoint. Users are referenced by tickets, cards, payments, and maintenance issues; deleting them would break history. Deactivation (`isActive=false`) is the safer action.

**Q: What happens if two users register the same contact?**
A: The controller checks first, and the unique index `UX_User_Contact` is the final guarantee.

**Q: How does `isActive` affect login?**
A: `DbUserDetailsService` calls `.disabled(!isActive)`. Spring Security rejects disabled users.

**Q: Where is the ownership check?**
A: In `TicketController`, `MetroCardController`, and `PaymentController`, using `Authentication.getName()` and `findByContact`.

---

## 11. One-line summary

`User` is the identity row: it stores who the person is, how they log in, what role they have, and whether they are active; every ticket, card, payment, and maintenance report ultimately points back to it.
