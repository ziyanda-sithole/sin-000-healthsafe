# WardServiceApp

## Overview

Provides lists of wards and departments.

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

Data: on startup it fetches the cleaned ward records from `ingestion-service`
(`GET /wards`) and keeps them in memory. It retries up to 5 times, 2 seconds apart,
so start order is forgiving, and it refuses to start if ingestion never answers,
because an empty ward list would make every valid ward look unknown.

MQ: **planned (stages 3-4), not implemented yet.** This service will subscribe to the ActiveMQ topic `staffing-events-topic` — see [`../common/`](../common) — and publish to the ActiveMQ queue `equipment-failure-queue` when it detects an equipment failure on one of its wards, consumed by [`../equipment-alert-service`](../equipment-alert-service). Broker URL, topic name, and queue name come from the common `co.wethinkcode.healthsafe.mq.MqConfig` class alongside it in this module.

REST: called by `staffing-service` (`../staffing-service`) to check that a ward exists — see [Integration contracts](../README.md#integration-contracts) in the root README for the endpoint shapes.

## Project structure

```
ward-service/
├── pom.xml
└── src/
    ├── main/java/co/wethinkcode/healthsafe/
    │   ├── WardServiceApp.java      (routes + startup)
    │   ├── Ward.java                (record mirroring ingestion's JSON)
    │   ├── WardRepository.java      (in-memory store and lookups)
    │   ├── IngestionClient.java     (HTTP client for ingestion-service)
    │   └── mq/
    │       └── MqConfig.java
    └── test/java/co/wethinkcode/healthsafe/
        ├── IngestionClientTest.java
        └── WardRepositoryTest.java
```

## Build

```
mvn package
```

## Run

Start `ingestion-service` first (port 7030), then:

```
java -jar target/ward-service.jar
```

Listens on port `7031`.

## Endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `OK` |
| GET | `/wards` | JSON array of all wards (`wardId`, `wing`, `department`, `bedsAvailable`, `notes`) |
| GET | `/wards/{id}` | One ward. The id ignores case and padding (`w-05` finds `W-05`). `404` with `{"error": "Unknown ward: ..."}` if it does not exist |
| GET | `/departments` | Sorted, de-duplicated list of department names (wards with no department are skipped) |

## Configuration

| Environment variable | Default | Meaning |
|---|---|---|
| `INGESTION_URL` | `http://localhost:7030` | Where to fetch the cleaned wards from |

## Test

Unit tests (JUnit 5) need nothing else running; they use the JDK's built-in HTTP
server as a fake `ingestion-service`:

```
mvn test
```

To check the running service by hand (with `ingestion-service` up):

```
curl http://localhost:7031/health         # -> OK
curl http://localhost:7031/wards/w-05     # -> the merged W-05 record
curl -i http://localhost:7031/wards/W-99  # -> 404
```

The whole chain is checked end to end by `scripts/smoke-test.sh` in the project root.