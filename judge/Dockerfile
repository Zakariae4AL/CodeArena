# syntax=docker/dockerfile:1
# One Dockerfile, several images:
#   worker              the judge worker (talks to Redis and the Docker engine)
#   runner-<language>   the sandbox images, one per language, with boxrun as entrypoint

FROM golang:1.26-bookworm AS build
WORKDIR /src
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/judge ./cmd/judge \
 && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/boxrun ./cmd/boxrun

# ---------- Judge worker ----------
FROM docker:29-cli AS worker
COPY --from=build /out/judge /usr/local/bin/judge
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://localhost:8081/healthz || exit 1
ENTRYPOINT ["/usr/local/bin/judge"]

# ---------- Sandbox runners ----------
FROM debian:bookworm-slim AS runner-cpp
RUN apt-get update \
 && apt-get install -y --no-install-recommends g++ libc6-dev \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /out/boxrun /usr/local/bin/boxrun
ENTRYPOINT ["/usr/local/bin/boxrun"]

FROM eclipse-temurin:25-jdk AS runner-java
COPY --from=build /out/boxrun /usr/local/bin/boxrun
ENTRYPOINT ["/usr/local/bin/boxrun"]

FROM python:3.14-slim AS runner-python
COPY --from=build /out/boxrun /usr/local/bin/boxrun
ENTRYPOINT ["/usr/local/bin/boxrun"]

FROM node:24-slim AS runner-javascript
COPY --from=build /out/boxrun /usr/local/bin/boxrun
ENTRYPOINT ["/usr/local/bin/boxrun"]
