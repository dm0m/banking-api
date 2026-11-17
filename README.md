# Banking API

A REST API for a simple bank, built with Java 21 and Spring Boot 3. It manages customers, accounts and money transfers, with an emphasis on correctness: money is never lost, transfers are atomic, and every endpoint is secured.

> **Status:** work in progress. See the [roadmap](#roadmap) for what's done and what's next.

## Features

- **Customers**: create, fetch and update customer profiles
- **Accounts**: open and close accounts, check balances
- **Transfers**: move money between accounts atomically
- **Transactions**: paginated transaction history per account
- **Auth**: customers activate their login with a one-time code, then log in for a JWT; `ADMIN` manages everything, `USER` only their own data

## Tech stack

| Area        | Tools                                         |
|-------------|-----------------------------------------------|
| Language    | Java 21                                       |
| Framework   | Spring Boot 3 (Web, Data JPA, Validation, Security) |
| Database    | PostgreSQL, Hibernate/JPA, Flyway migrations  |
| Testing     | JUnit 5, Mockito, Testcontainers              |
| API docs    | SpringDoc OpenAPI (Swagger UI)                |
| Messaging   | Apache Kafka                                  |
| Tooling     | Maven, Docker Compose, GitHub Actions         |

## Getting started

### Prerequisites

- Java 21
- Docker and Docker Compose

### 1. Configure the environment

Copy the example and fill in your own values:

```bash
cp .env.example .env
```

| Variable | Used for | Required |
|----------|----------|----------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_SCHEMA` | Where PostgreSQL runs | Yes (`DB_PORT` defaults to 5432) |
| `DB_USER`, `DB_PASSWORD` | Database login, also used by `compose.yaml` to create it | Yes |
| `JWT_SECRET` | Key that signs access tokens (HS256): at least 32 bytes, e.g. `openssl rand -base64 48` | Yes, except with the `dev` profile, which sets its own |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | First admin login, created at startup if no admin exists yet. Password: 12–72 characters | No: leave both empty to skip. Setting only one stops startup |

`.env` is git-ignored: never commit real values.

### 2. Run the app

```bash
set -a; source .env; set +a   # Spring Boot doesn't read .env (Docker Compose does), so export it first
./mvnw spring-boot:run
```

Spring Boot starts the PostgreSQL container from `compose.yaml` automatically. To run the database on its own:

```bash
docker compose up -d
```

API docs: Swagger UI at http://localhost:8080/swagger-ui.html, OpenAPI spec at http://localhost:8080/api-docs. They are only served with the `dev` profile, which `./mvnw spring-boot:run` activates; the packaged jar runs without it, so the docs are off in production.

### 3. Get a token

Every endpoint except `/auth/**` needs a bearer token. Log in as the admin from `.env`:

```bash
curl -s localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email": "'"$ADMIN_EMAIL"'", "password": "'"$ADMIN_PASSWORD"'"}'
# {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":900}

curl -s localhost:8080/customers/1 -H "Authorization: Bearer <accessToken>"
```

In Swagger UI, click **Authorize** and paste the `accessToken`. See [Authentication](#authentication) for how customers get a login.

### 4. Try it with Postman

Import [`postman/banking-api.postman_collection.json`](postman/banking-api.postman_collection.json) and set the `adminEmail` and `adminPassword` variables, as an environment or as each variable's *current value*, so they're never exported. The collection logs in by itself before each request when needed. Every request creates the data it needs, so requests can run on their own or in any order. The `Security` folder logs in as a customer to show the 401 and 403 cases.

### 5. Run the tests

```bash
./mvnw test     # unit tests only: fast, no Docker needed
./mvnw verify   # unit + integration tests, coverage report and coverage check
```

Integration tests are the classes named `*IntegrationTest`: they start the whole app against a PostgreSQL container (Testcontainers), so Docker must be running. Maven Surefire runs the unit tests, Maven Failsafe the integration tests.

`verify` merges coverage from both into one JaCoCo report at `target/site/jacoco/index.html`, and fails if line coverage drops below 95% or branch coverage below 85%.

## Authentication

Customers don't sign up on their own: the bank creates the customer, then hands them a one-time code to activate their login.

1. **First admin**: created at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD`, only when no admin exists yet. Restarting never changes or duplicates it.
2. **Admin creates the customer**: `POST /customers`.
3. **Admin issues an activation code**: `POST /customers/{id}/activation-code` returns a one-time code, valid for 72 hours. Issuing a new one replaces the old one.
4. **Customer activates their login**: `POST /auth/register` with their email, the code and a password (12–72 characters).
5. **Customer logs in**: `POST /auth/login` returns a JWT valid for 15 minutes, sent as `Authorization: Bearer <token>`.

| Role | Can do |
|------|--------|
| `ADMIN` | Everything, including creating customers, issuing activation codes and deposits |
| `USER` | Read and update their own customer; open, read and close their own accounts; transfer from their own accounts; read transfers they're part of |

Errors use the same ProblemDetail shape as the rest of the API:

| Status | When | `WWW-Authenticate` |
|--------|------|--------------------|
| 401 | No token | `Bearer` |
| 401 | Malformed, expired or wrongly signed token | `Bearer error="invalid_token", ...` |
| 403 | Valid token, but not their data or an admin-only action | none |

## Project structure

Code is grouped by feature, not by layer: each feature package holds its controller, OpenAPI contract, service, repository, entities, DTOs, mapper and exceptions, so a change to one feature stays in one folder.

```
src/main/java/io/github/dm0m/bankingapi/
├── customer/     # Customer profiles and their address
├── account/      # Opening, closing and funding accounts; IBAN generation
├── transfer/     # Atomic transfers and transfer history
├── auth/         # Logins: activation codes, register, login, first admin
├── security/     # Filter chain, ownership checks, role annotations, 401/403 handler
└── common/       # Shared: ProblemDetail error handler, auditing base entity, email helper
    ├── config/      # Clock, JPA auditing, paging, OpenAPI
    └── validation/  # Custom Bean Validation constraints
src/main/resources/
└── db/migration/ # Flyway SQL migrations
src/test/java/...  # Same packages: unit tests, plus Testcontainers integration tests
```

## Design decisions

- **`BigDecimal` for money**: floating-point types like `double` can't represent decimal amounts exactly, which leads to rounding errors.
- **`@Transactional` transfers**: the debit and the credit succeed or fail together, so a failure halfway through leaves both balances untouched.
- **Row locks in a fixed order**: a transfer locks both accounts (`SELECT ... FOR UPDATE`), lower id first, so concurrent transfers can't overdraw an account or deadlock. `@Version` (optimistic locking) is the safety net for any write that skips the lock.
- **DTOs separate from entities**: the API contract stays stable when the database model changes, and internal fields are never exposed by accident.
- **Stateless JWT instead of sessions**: the server keeps no login state, so any instance can check a token on its own. The price is that a token can't be revoked, so it only lives 15 minutes.
- **Passwords and activation codes hashed with BCrypt**: a database leak exposes neither. Login compares against a dummy hash when the email is unknown, so response times don't reveal which emails exist, and every login failure returns the same message.
- **Ownership checked before the controller runs**: `@PreAuthorize` rules such as `@IsAdminOrAccountOwner` keep the access rules next to each endpoint. A `USER` asking for an id that doesn't exist gets 403, not 404, so they can't probe which ids exist.
- **Activation codes instead of open sign-up**: only someone the bank gave a code to can create a login for a customer.
- **Kafka events** (planned, Week 7): a `TransferCompleted` event lets other services (audit, notifications) react to transfers without coupling them to the transfer code.

## Roadmap

- [x] Customers: entity, CRUD endpoints, first Flyway migration
- [x] Accounts: customer–account relationship, DTOs, validation
- [x] Transfers: atomic transfers, optimistic locking, paginated history
- [x] Error handling: consistent JSON errors, Swagger UI
- [x] Security: JWT auth, BCrypt, role-based access
- [ ] Testing: unit tests and Testcontainers integration tests
- [ ] Kafka: publish and consume transfer events
- [ ] Shipping: Dockerfile, full Docker Compose setup, CI with GitHub Actions

### Known gaps

- No refresh tokens or logout: a token stays valid until it expires (15 minutes).
- No rate limiting or lockout on login, so passwords can be brute-forced.
- Activation codes are returned to the admin, who passes them on by hand. There's no email delivery.
- No password change or reset, and no way to create more admins than the first one.
- HS256 uses one shared secret. Several services verifying tokens would need RS256 with a public key.
