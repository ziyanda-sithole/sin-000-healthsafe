# StaffingServiceApp

## Overview

Provides on-call schedules for doctors based on ward and status.

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

MQ: **planned (stage 3), not implemented yet.** This service will publish to the ActiveMQ topic `staffing-events-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.healthsafe.mq.MqConfig` class alongside it in this module.

REST: calls `ward-service` (`../ward-service`) to validate the ward and
`alert-level-service` (`../alert-level-service`) to read the current Emergency
Status before computing a schedule — see [Integration contracts](../README.md#integration-contracts)
in the root README for the endpoint shapes.

## How a schedule is built

For `GET /schedule/{id}` the service:

1. Normalises the id (trim, upper-case), so `w-05` and `W-05` are the same ward.
2. Asks `ward-service` whether the ward exists. If not, it stops and answers `404`.
3. Asks `alert-level-service` for the current Emergency Status (0-8).
4. Applies the staffing rule below and returns the schedule.

### The staffing rule (an assumption)

The project README says the Emergency Status should "size the on-call schedule" but gives
no formula. **The rule below is my own choice and must be confirmed with the hospital.**
It lives in one class, `StaffingPolicy`, so changing it touches nothing else.

> doctors on call = 1 + alert level

| Level | Status band | Doctors on call |
|---|---|---|
| 0 | NORMAL | 1 |
| 1 | NORMAL | 2 |
| 2 | NORMAL | 3 |
| 3 | ELEVATED | 4 |
| 4 | ELEVATED | 5 |
| 5 | ELEVATED | 6 |
| 6 | CRITICAL | 7 |
| 7 | CRITICAL | 8 |
| 8 | CODE_BLUE | 9 |

Only "8 = full Code Blue" comes from the project README; the other band boundaries
(0-2, 3-5, 6-7) are my own.

## Project structure

```
staffing-service/
├── pom.xml
└── src/
    ├── main/java/co/wethinkcode/healthsafe/
    │   ├── StaffingServiceApp.java   (route + error handling)
    │   ├── StaffingPolicy.java       (the rule: level -> doctors and status band)
    │   ├── OnCallSchedule.java       (the response record)
    │   ├── WardClient.java           (HTTP client for ward-service)
    │   ├── AlertLevelClient.java     (HTTP client for alert-level-service)
    │   └── mq/
    │       └── MqConfig.java
    └── test/java/co/wethinkcode/healthsafe/
        ├── StaffingPolicyTest.java
        ├── WardClientTest.java
        └── AlertLevelClientTest.java
```

## Build

```
mvn package
```

## Run

Start `ingestion-service`, `ward-service` and `alert-level-service` first, then:

```
java -jar target/staffing-service.jar
```

Listens on port `7033`.

## Endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `OK` |
| GET | `/schedule/{id}` | `200` `{"wardId", "alertLevel", "status", "doctorsOnCall"}` |

Example: `GET /schedule/w-05` at level 8 returns

```json
{"wardId":"W-05","alertLevel":8,"status":"CODE_BLUE","doctorsOnCall":9}
```

| Status | Meaning |
|---|---|
| `200` | Schedule built |
| `404` | `ward-service` says the ward does not exist (`{"error": "Unknown ward: ..."}`) |
| `502` | A service this one depends on is unreachable, timed out, or sent an unusable reply. The message names which one |
| `503` | The request was interrupted; try again |

`404` and `502` are deliberately different: "the ward does not exist" is an answer,
"I could not find out" is a failure. If `ward-service` is down, even a nonexistent ward
returns `502`, not `404`.

## Failure behaviour

- **No fallback level.** If `alert-level-service` is down or returns something invalid
  (missing, not an integer, outside 0-8), the request fails with `502`. It does not assume
  level 0, because under-staffing during a real emergency is the dangerous mistake.
- **Timeouts.** Each call to another service waits 3 s to connect and 5 s for a reply, so a
  hung neighbour cannot hang this service.
- The `502` message includes the neighbour's internal URL. That helps while developing; for
  a public-facing API you would log it and return a generic message.

## Configuration

| Environment variable | Default | Meaning |
|---|---|---|
| `WARD_URL` | `http://localhost:7031` | Where `ward-service` is |
| `ALERT_LEVEL_URL` | `http://localhost:7032` | Where `alert-level-service` is |

## Limitations

- **The ward only gates the request; it does not change the size of the schedule.** Two
  different wards at the same alert level get the same number of doctors. The project
  README says "based on ward and status", so a fuller version would use ward data too
  (for example the department or bed count from `ward-service`).
- **`doctorsOnCall` is a headcount, not a named roster.** There is no doctor data in the
  project, so the service does not say *who* is on call.
- **The staffing rule is an assumption** (see above).
- **No automated test covers the endpoint's error mapping** (404/502/503). The rule and both
  clients are unit tested; the error mapping is checked by hand and, for 404, by the smoke test.

## Test

Unit tests (JUnit 5). The clients are tested against fake servers using the JDK's built-in
HTTP server, so nothing else needs to be running:

```
mvn test
```

To check the running service by hand (all four services up):

```
curl http://localhost:7033/health            # -> OK
curl http://localhost:7033/schedule/W-05
curl -i http://localhost:7033/schedule/W-99  # -> 404
```

To see the failure handling, stop `alert-level-service` and request a schedule: you should
get `502` with `could not reach alert-level-service at http://localhost:7032 (ConnectException)`.

The whole chain is checked end to end by `scripts/smoke-test.sh` in the project root.