# HealthSafe

## Overview

Hospital ward status and emergency staffing schedules.

Domain entities: wards, wings, specialist departments.

Every class in this repo lives in a single flat package, `co.wethinkcode.healthsafe`. HealthSafe is built
as a small set of independent services, following a growth path from simple data
cleanup through synchronous REST calls to asynchronous MQ decoupling and alerting:

1. clean a messy legacy CSV export (`wards-outdated.csv`) — handled by **IngestionServiceApp**
2. serve it up and act on it, via three REST services calling each other directly
   over HTTP
3. decouple the relevant services with an ActiveMQ topic (`staffing-events-topic`) instead of
   direct calls — shared broker setup lives in [`common/`](common)
4. raise the alarm on failure — handled by **EquipmentAlertServiceApp**

| Service | Folder | Port | Role |
|---|---|---|---|
| IngestionServiceApp | [`ingestion-service/`](ingestion-service) | 7030 | Parses and cleans `wards-outdated.csv` |
| WardServiceApp | [`ward-service/`](ward-service) | 7031 | Provides lists of wards and departments. |
| AlertLevelServiceApp | [`alert-level-service/`](alert-level-service) | 7032 | Tracks the hospital Emergency Status (0-8, 8 = full Code Blue). |
| StaffingServiceApp | [`staffing-service/`](staffing-service) | 7033 | Provides on-call schedules for doctors based on ward and status. |
| EquipmentAlertServiceApp | [`equipment-alert-service/`](equipment-alert-service) | 7034 | uses a Queue to guarantee delivery of critical medical equipment failure alerts. |

Plus [`common/`](common) (no port) — the shared ActiveMQ broker and MQ config notes
for `staffing-events-topic`: Staffing updates are broadcast as Events via the broker to decouple the frontend from the Staffing Service.

## Status

| Stage | State |
|---|---|
| 1. Ingestion | **Done**: CSV cleaned and served at `GET /wards` |
| 2. REST services | **Done**: ward, alert-level and staffing services working end to end |
| 3. MQ decoupling (`staffing-events-topic`) | Not started |
| 4. Alerting (`equipment-failure-queue`) | Not started |

The required core (stages 1-2) is complete and checked by unit tests plus an end-to-end
script (see [Verify the core](#verify-the-core)). `equipment-alert-service` and the broker
in `common/` are still scaffold only.

## Your task

Each stage below builds on the last — do them in order. Every service already builds
and runs (`/health` returns `OK`); your job is to fill in the `TODO`s.

1. **Ingestion** (required) — in `IngestionServiceApp`, read and clean
   `wards-outdated.csv` (see [ingestion-service/README.md](ingestion-service/README.md)
   for the known data issues and a worked example) and expose the cleaned records
   over REST for `ward-service` to consume.
2. **REST services** (required) — implement `ward-service`, `alert-level-service`,
   and `staffing-service` per the [Integration contracts](#integration-contracts)
   below: wards/departments lookup, Emergency Status tracking, and on-call
   scheduling that calls the other two services synchronously over HTTP.
3. **MQ decoupling** (stretch) — replace the synchronous call from `ward-service`
   to `staffing-service` with the `staffing-events-topic` broadcast described in
   [common/README.md](common/README.md), so staffing updates reach `ward-service`
   asynchronously instead.
4. **Alerting** (stretch) — have `ward-service` publish to the
   `equipment-failure-queue` when it detects an equipment failure, and implement
   `equipment-alert-service` as the guaranteed-delivery consumer (see
   [equipment-alert-service/README.md](equipment-alert-service/README.md)).

Stage 1-2 are the required core; stages 3-4 are where you can show judgment about
when to reach for a queue/topic instead of a direct call. There's no fixed time
limit, but budget your effort so you have a working stage 1-2 before spending time
on 3-4 — a complete core beats a half-done everything.

Automated tests aren't required, but are a good way to show your work — see each
service's `## Test` section for how to add JUnit 5.

## Integration contracts

Endpoint shapes below are illustrative, not a fixed spec to match byte-for-byte —
reasonable field names/status codes are fine as long as the calling service can
consume them.

| From | To | Call | Purpose |
|---|---|---|---|
| `ward-service` | `ingestion-service` | `GET /wards` → cleaned ward records | Populate its own ward/department list |
| `staffing-service` | `ward-service` | `GET /wards/{id}` → `404` if unknown | Validate the ward before scheduling |
| `staffing-service` | `alert-level-service` | `GET /alert-level` → `{ "level": 0-8 }` | Read current Emergency Status to size the on-call schedule |
| `staffing-service` | `ward-service` (topic, stage 3) | publish to `staffing-events-topic` | Broadcast a schedule/status change |
| `ward-service` (topic, stage 3) | — | subscribe to `staffing-events-topic` | React to staffing updates without polling |
| `ward-service` (queue, stage 4) | `equipment-alert-service` | publish to `equipment-failure-queue` | Guarantee delivery of an equipment failure alert |

## Project structure

```
healthsafe/
├── README.md
├── .gitignore
├── scripts/
│   └── smoke-test.sh           (end-to-end check of stages 1-2)
├── ingestion-service/          (port 7030)
│   ├── pom.xml
│   ├── README.md
│   └── src/main/
│       ├── java/co/wethinkcode/healthsafe
│       └── resources/wards-outdated.csv
├── ward-service/          (port 7031)
├── alert-level-service/          (port 7032)
├── staffing-service/          (port 7033)
├── common/
│   ├── docker-compose.yml
│   └── README.md
└── equipment-alert-service/          (port 7034)
```

## Build

Requirements: Java 17+, Maven 3.8+, Docker (for the broker in `common/`).

Every folder here (`ingestion-service/`, each domain service, and `equipment-alert-service/`) is
an **independent** Maven project — there is no parent/aggregator pom. Build one at a
time, e.g.:

```
cd ward-service
mvn package
```

...or build every module in the repo in one pass from the project root:

```
find . -name pom.xml -execdir mvn package \;
```

## Run

```
# ingestion
cd ingestion-service && mvn package && java -jar target/ingestion-service.jar

# domain services, each in its own terminal
# terminal 1
cd ward-service && mvn package && java -jar target/ward-service.jar
# terminal 2
cd alert-level-service && mvn package && java -jar target/alert-level-service.jar
# terminal 3
cd staffing-service && mvn package && java -jar target/staffing-service.jar

# MQ broker (needed once the MQ-aware services above are wired up)
cd common && docker compose up -d

# alerting
cd equipment-alert-service && mvn package && java -jar target/equipment-alert-service.jar
```

| Service | Port |
|---|---|
| IngestionServiceApp (`ingestion-service`) | 7030 |
| WardServiceApp (`ward-service`) | 7031 |
| AlertLevelServiceApp (`alert-level-service`) | 7032 |
| StaffingServiceApp (`staffing-service`) | 7033 |
| EquipmentAlertServiceApp (`equipment-alert-service`) | 7034 |

**For the core (stages 1-2) you only need four services**, started in this order, each
in its own terminal: `ingestion-service`, `ward-service`, `alert-level-service`,
`staffing-service`. The broker in `common/` and `equipment-alert-service` are only for
stages 3-4. `ward-service` retries `ingestion-service` for about 10 seconds at startup, so
a slightly wrong order is forgiven; if ingestion never answers, `ward-service` exits with
a clear message instead of running with an empty list.

## Verify the core

With the four services running:

```
bash scripts/smoke-test.sh
```

It runs 15 checks across the services and ends with `15 passed, 0 failed`. Any failure prints
what was expected and what came back. `HTTP 000` means that service is not running.

> The script temporarily raises the alert level to 8 (to prove the staffing schedule reacts)
> and sets it back to 0 at the end.

## Test

`ingestion-service`, `ward-service`, `alert-level-service` and `staffing-service` have
JUnit 5 unit tests. The HTTP clients are tested against fake servers built on the JDK's
own HTTP server, so no other service needs to be running. Run one module:

```
cd ward-service
mvn test
```

...or every module at once from the project root:

```
find . -name pom.xml -execdir mvn -q test \;
```

`equipment-alert-service` has no tests yet (stage 4). Each running service also exposes
`/health`:

```
curl http://localhost:7030/health   # -> OK
```

For an end-to-end check, use `scripts/smoke-test.sh` (see [Verify the core](#verify-the-core)).

## Design decisions and assumptions

These are choices the project README left open. Each is also explained in the relevant
service's own README.

**Decisions**

- **Services share JSON contracts, not Java classes.** There is no shared module, so `Ward`
  is deliberately duplicated in `ingestion-service` and `ward-service`. Each side ignores
  fields it does not know, so one service adding a field does not break another.
- **Never guess data.** In `ingestion-service`, an untrustworthy value (a bed count of
  `five`, `-1` or `2023`) becomes `null` with a note explaining why, rather than an invented
  number. Duplicates are merged with a stated rule and conflicts are recorded.
- **"No such thing" and "could not find out" are different answers.** `staffing-service`
  returns `404` when `ward-service` says a ward does not exist, and `502` when a service it
  depends on is down or misbehaving. A dead dependency is never reported as "not found".
- **No fallback alert level.** If `alert-level-service` cannot be read, `staffing-service`
  fails rather than assuming level 0, because under-staffing in a real emergency is the
  dangerous mistake.
- **Fail fast at startup.** `ward-service` will not start with an empty ward list, since
  every valid ward would then look unknown.

**Assumptions to confirm**

- Maximum **100 beds** per ward (`ingestion-service`), so values like `2023` are rejected.
- Staffing rule **doctors on call = 1 + alert level**, with status bands NORMAL 0-2,
  ELEVATED 3-5, CRITICAL 6-7, CODE_BLUE 8 (`staffing-service`). Only "8 = Code Blue" comes
  from the project README.
- `Paediatrics` is the canonical spelling over `Pediatrics`, because it is the majority
  spelling in the data.

**Known limitations**

- The alert level is held in memory and **resets to 0 when `alert-level-service` restarts**.
- A schedule depends only on the alert level; the ward is validated but does not change the
  headcount, and the result is a number of doctors, not a named roster.
- The date and boolean cleaning rules from the ingestion README are not implemented, because
  `wards-outdated.csv` has no date or flag columns.
- The HTTP endpoints' error mapping is covered by the smoke test and manual checks, not by
  automated endpoint tests.

**Open question for stage 3.** The text of stage 3 says to replace the call "from
`ward-service` to `staffing-service`", but the integration table shows `staffing-service`
*publishing* to `staffing-events-topic` and `ward-service` *subscribing*. The only
synchronous call that exists today goes the other way (`staffing-service` calls
`ward-service` to validate a ward). This needs settling before stage 3 is built.