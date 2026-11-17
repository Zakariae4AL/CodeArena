# Changelog

## 1.0.0 — 2026-11

First release.

### Judge (Go)
- Worker reading jobs from Redis Streams with a consumer group; crashed workers' jobs are claimed again, poison jobs are given up after 3 deliveries.
- Docker sandbox per submission: no network, read-only root, non-root user, dropped capabilities, memory / CPU / process / file limits, optional gVisor.
- `boxrun` runner (PID 1 in the sandbox): compile, run each test, measure CPU time and memory, compare outputs, kill every leftover process.
- C++20, Java 25, Python 3 and JavaScript, with per-language time factors.
- Zero third-party dependencies (own RESP client).

### Core (Spring Boot modular monolith)
- Modules: identity, problems, contests, submissions, leaderboard, realtime, verified by Spring Modulith.
- Transactional outbox through the event publication registry, with periodic retries.
- JWT authentication, rate limits, admin-only editors, hidden tests never exposed.
- ICPC scoreboard built from `SubmissionJudged` events.
- STOMP WebSocket with subscribe-only topics.
- PostgreSQL schema with Flyway; demo accounts, problems and contests.

### Web (React)
- Monaco workspace with drafts, run on samples, submit, live per-test results.
- Contests with countdowns and a live scoreboard; admin problem and contest editors.
- English / French, dark / light theme, responsive.

### Delivery
- Docker Compose for the whole stack, nginx front, GitHub Actions (Go, Docker sandbox, Java, web).
