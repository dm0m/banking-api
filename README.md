# Banking API

A REST API for a simple bank, built with Java 21 and Spring Boot 3. It manages customers, accounts and money transfers, with an emphasis on correctness: money is never lost, transfers are atomic, and every endpoint is secured.

> **Status:** work in progress. See the [roadmap](#roadmap) for what's done and what's next.

## Features

- **Customers**: create, fetch and update customer profiles
- **Accounts**: open and close accounts, check balances
- **Transfers**: move money between accounts atomically
- **Transactions**: paginated transaction history per account
- **Auth**: register and log in with JWT, using `USER` and `ADMIN` roles

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

Create a `.env` file in the project root:

```env
DB_USER=banking
DB_PASSWORD=change-me
DB_NAME=banking
DB_PORT=5432
```

### 2. Run the app

```bash
./mvnw spring-boot:run
```

Spring Boot starts the PostgreSQL container from `compose.yaml` automatically. To run the database on its own:

```bash
docker compose up -d
```

### 3. Run the tests

```bash
./mvnw verify
```

## Project structure

```
src/main/java/io/github/dm0m/bankingapi/
├── controller/   # REST endpoints
├── service/      # Business logic
├── repository/   # Data access (Spring Data JPA)
└── model/        # JPA entities and enums
src/main/resources/
└── db/migration/ # Flyway SQL migrations
```

## Design decisions

- **`BigDecimal` for money**: floating-point types like `double` can't represent decimal amounts exactly, which leads to rounding errors.
- **`@Transactional` transfers**: the debit and the credit succeed or fail together, so a failure halfway through leaves both balances untouched.
- **Optimistic locking (`@Version`)**: prevents two concurrent transfers from overwriting each other's balance updates.
- **DTOs separate from entities**: the API contract stays stable when the database model changes, and internal fields are never exposed by accident.
- **Kafka events**: a `TransferCompleted` event lets other services (audit, notifications) react to transfers without coupling them to the transfer code.

## Roadmap

- [x] Customers: entity, CRUD endpoints, first Flyway migration
- [x] Accounts: customer–account relationship, DTOs, validation
- [ ] Transfers: atomic transfers, optimistic locking, paginated history
- [ ] Error handling: consistent JSON errors, Swagger UI
- [ ] Security: JWT auth, BCrypt, role-based access
- [ ] Testing: unit tests and Testcontainers integration tests
- [ ] Kafka: publish and consume transfer events
- [ ] Shipping: Dockerfile, full Docker Compose setup, CI with GitHub Actions
