# WardServiceApp

## Overview

Provides lists of wards and departments.

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

Data: on startup it fetches the cleaned ward records from `ingestion-service`
(`GET /wards`) and keeps them in memory. It retries up to 5 times, 2 seconds apart,
so start order is forgiving, and it refuses to start if ingestion never answers,
because an empty ward list would make every valid ward look unknown.

MQ: subscribes to the ActiveMQ topic `staffing-events-topic` (see [`../common/`](../common)) and keeps the newest staffing update per ward (**stage 3, implemented**). It connects on a background thread and retries, so the service starts and serves wards even while the broker is down, and it re-subscribes by itself if the broker restarts. **Stage 4 (implemented):** queues critical equipment failure alerts on the queue equipment-failure-queue (persistent messages), consumed by [../equipment-alert-service](../equipment-alert-service). Broker URL and names come from the common `co.wethinkcode.healthsafe.mq.MqConfig` class alongside it in this module.
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
    │   ├── ScheduleUpdatedEvent.java    (topic message, mirrors staffing's)
    │   ├── ScheduleEventParser.java     (reads and validates topic messages)
    │   ├── StaffingStore.java           (newest staffing event per ward)
    │   ├── StaffingEventSubscriber.java (background topic listener with retry)
    │   ├── WardStaffing.java            (response for /wards/{id}/staffing)    
    │   ├── EquipmentFailureAlert.java
    │   ├── EquipmentAlertPublisher.java
    │   └── mq/
    │       └── MqConfig.java
    └── test/java/co/wethinkcode/healthsafe/
        ├── IngestionClientTest.java
        └── WardRepositoryTest.java
        ├── StaffingStoreTest.java
        ├── ScheduleEventParserTest.java
        ├── StaffingEventSubscriberTest.java
        ├── EquipmentAlertPublisherTest.java
        └── WardStaffingTest.java
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
| GET | `/wards/{id}/staffing` | The latest staffing update received over the topic: `wardId`, `alertLevel`, `status`, `doctorsOnCall`, `issuedAtMillis`. `404` if the ward is unknown, or if no update has arrived yet (never zeros) |
| GET | `/departments` | Sorted, de-duplicated list of department names (wards with no department are skipped) |
| POST | `/wards/{id}/equipment-failure` | Body `{"equipment": "ventilator", "details": "no airflow"}` (`details` optional). Queues an alert for equipment-alert-service and answers `202` with the alert. `404` unknown ward; `400` missing `equipment` or malformed body; `502` with "Alert NOT sent" if the broker did not take it |

## Configuration

| Environment variable | Default | Meaning |
|---|---|---|
| `INGESTION_URL` | `http://localhost:7030` | Where to fetch the cleaned wards from |

## Limitations

- Staffing updates are held in memory: after a restart there is nothing at `/wards/{id}/staffing`
  until staffing-service publishes again.
- A topic does not remember messages for a subscriber that was offline, so updates published
  while this service was disconnected are lost. Each update carries the full state, so the next
  one repairs the gap.
- The data is eventually consistent: it reflects what the subscriber has stored, not a live
  call to staffing-service.
- If the broker is down when an equipment failure is reported, the alert is **not** stored here:
  the endpoint answers `502` ("Alert NOT sent") and the caller must retry or escalate. A
  production system would keep a local outbox and retry.

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