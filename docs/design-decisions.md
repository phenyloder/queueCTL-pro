# Design Decisions

## Dependencies

- `picocli`: Required for CLI command parsing and subcommands.
- `Jakarta Persistence API` + `Hibernate ORM`: Required for entity-based persistence and JPA implementation.
- `HikariCP`: Required for production-grade DB connection pooling.
- `Flyway`: Required for schema migrations.
- `Micrometer` + `Prometheus`: Required for metrics emission and scraping endpoint.
- `SLF4J` + `Logback`: Required for structured logging.
- `JUnit 5`, `Testcontainers`, `Awaitility`: Required for integration and async behavior verification.
- `Spotless` + `Checkstyle`: Required for formatting and static style checks.
