# AlertLevelServiceApp

## Overview

Tracks the hospital Emergency Status (0-8, 8 = full Code Blue).

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

REST: called by `staffing-service` (`../staffing-service`) to read the current
status when computing on-call schedules — see [Integration contracts](../README.md#integration-contracts)
in the root README for the endpoint shapes.

The level is held in memory only: it starts at `0` (normal) and **resets to `0` when
the service restarts**. Persisting it was outside the scope of the project README.

## Project structure

```
alert-level-service/
├── pom.xml
└── src/
    ├── main/java/co/wethinkcode/healthsafe/
    │   ├── AlertLevelServiceApp.java   (routes)
    │   └── AlertLevelStore.java        (thread-safe level + 0-8 validation)
    └── test/java/co/wethinkcode/healthsafe/
        └── AlertLevelStoreTest.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/alert-level-service.jar
```

Listens on port `7032`.

## Endpoints

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/health` | | `OK` |
| GET | `/alert-level` | | `200` `{"level": 0-8}` |
| PUT | `/alert-level` | `{"level": 0-8}` | `200` `{"level": n}` with the new level |

`PUT` answers `400` with a JSON `{"error": ...}` and leaves the level unchanged when:

- `level` is outside 0-8,
- `level` is missing (`{}`), or
- the body is not valid JSON or `level` is not a number.

## Test

Unit tests (JUnit 5):

```
mvn test
```

To check the running service by hand:

```
curl http://localhost:7032/alert-level
curl -X PUT -H "Content-Type: application/json" -d '{"level":8}' http://localhost:7032/alert-level
curl -i -X PUT -H "Content-Type: application/json" -d '{"level":9}' http://localhost:7032/alert-level   # -> 400
```

The whole chain is checked end to end by `scripts/smoke-test.sh` in the project root.