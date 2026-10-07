# IngestionServiceApp

## Overview

Parses and cleans `wards-outdated.csv`, a messy legacy export of wards, wings, and specialist departments data, and is the
first stop in the HealthSafe pipeline. Independent Maven module, no parent pom.

Part of the [HealthSafe](../README.md) project.

REST: exposes the cleaned records for `ward-service` (`../ward-service`) to
consume — see [Integration contracts](../README.md#integration-contracts) in the
root README for the endpoint shape.

The CSV is read and cleaned **once at startup** and the result is held in memory.
On the supplied file, 18 raw rows become 17 wards (W-05 appears twice and is merged).

## Example: one row cleaned

Input (`wards-outdated.csv`, row 6):

```
w-05,east wing ,PAEDIATRICS,five
```

Cleaned on its own, this row becomes:

```json
{
  "wardId": "W-05",
  "wing": "East Wing",
  "department": "Paediatrics",
  "bedsAvailable": null,
  "notes": ["row 6: bedsAvailable was non-numeric ('five') - flagged for follow-up"]
}
```

Row 6 is also a near-duplicate of `W-05` in row 5 (same real ward, different ID casing
and a different bed value). The two rows are merged, so the service actually returns:

```json
{
  "wardId": "W-05",
  "wing": "East Wing",
  "department": "Paediatrics",
  "bedsAvailable": 5,
  "notes": [
    "row 6: bedsAvailable was non-numeric ('five') - flagged for follow-up",
    "merged duplicate of rows 5 and 6"
  ]
}
```

"Row N" always means the N-th **data** row; the header line is not counted.

## Cleaning rules

| Field | Rule |
|---|---|
| `wardId` | Trim, collapse inner spaces, upper-case (`" w-05 "` becomes `W-05`). A row with no usable id is **dropped** (see below). |
| `wing` | Trim, collapse inner spaces (`South  Wing` becomes `South Wing`), title-case. Missing becomes `null` plus a note. |
| `department` | Trim, collapse spaces, title-case, then spelling variants are unified: `Pediatrics` and `Paediatrics` both become `Paediatrics`, and `icu` stays `ICU`. Missing becomes `null` plus a note. |
| `bedsAvailable` | Accepted only if it is a whole number from 0 to 100. Anything else becomes `null` plus a note saying why (missing, non-numeric, negative, or unrealistic). `0` is valid: a full ward is real data. |

**Placeholders.** These count as "no value" in any casing and with any padding:
blank, `N/A`, `NA`, `TBD`, `unknown`, `-`, `NaN`, `null`.

**Never guess.** An untrustworthy value becomes `null` with an explanation in `notes`,
not a made-up number. A wrong bed count in a hospital system is worse than a missing one.

**Duplicates.** Rows are the same ward when their cleaned ids match (so `w-05` and
`W-05` collide). The earlier row wins; the later row is only used to **fill gaps** where
the earlier value is `null`. If both rows have a value and they disagree, the earlier one
is kept and the conflict is recorded in `notes` (`conflict on bedsAvailable: kept '3', ignored '7'`).
Notes from both rows are kept as an audit trail, which is why a merged ward can still carry
a note about a value that was later filled in.

**Dropped rows.** A row with no usable `wardId` cannot be identified, so it is dropped.
The drop is reported in the startup log (`rejected rows: [...]`), never silent. The supplied
file has none.

## Known data issues

`wards-outdated.csv` is deliberately messy — cleaning it is the point of this service.
Status of each issue from the assignment list:

| Issue | Status |
|---|---|
| Inconsistent casing | Handled |
| Padding (leading, trailing, double spaces) | Handled |
| Duplicate records for the same entity | Handled (W-05) |
| Missing / placeholder values | Handled |
| Invalid or non-numeric values in numeric columns | Handled (`-1`, `-2`, `five`, `full`, `2023`, ...) |
| Naming / spelling variants | Handled for `Pediatrics`/`Paediatrics` and `ICU` only; other variants need an entry in `FieldNormalizer.DEPARTMENT_CANONICAL` |
| Inconsistent date formats | **Not implemented.** This CSV has no date column |
| Inconsistent boolean / flag values | **Not implemented.** This CSV has no flag column |

## Assumptions and limitations

- **Maximum 100 beds** per ward (`FieldNormalizer.MAX_REALISTIC_BEDS`). This is an assumption
  made so that a value like `2023` is rejected as a typo; confirm it against the real hospital.
- **Columns are read by position** (`ward_id, wing, department, beds_available`). The header
  names are ignored, so reordering columns would silently misread the data.
- **No quoted CSV fields.** The reader splits on every comma, so a value like `"Ward A, Annex"`
  is not supported. The supplied file has none. If real data does, replace the body of
  `WardCsvReader.readRows` with OpenCSV's `CSVReader` (already in the pom).
- **Data is fixed at startup.** Changing the CSV requires restarting the service.

## Project structure

```
ingestion-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/co/wethinkcode/healthsafe/
    │   │   ├── IngestionServiceApp.java   (startup + routes)
    │   │   ├── Ward.java                  (cleaned record)
    │   │   ├── FieldNormalizer.java       (one small rule per kind of mess)
    │   │   ├── WardCsvReader.java         (file -> raw rows; does no cleaning)
    │   │   └── WardCleaner.java           (rows -> Ward records, merges duplicates)
    │   └── resources/wards-outdated.csv
    └── test/java/co/wethinkcode/healthsafe/
        ├── FieldNormalizerTest.java
        ├── WardCsvReaderTest.java
        └── WardCleanerTest.java
```

## Build

Requires Java 17+ and Maven 3.8+.

```
mvn package
```

## Run

```
java -jar target/ingestion-service.jar
```

Listens on port `7030`. At startup it prints how many wards were loaded and any rejected rows.

## Endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `OK` |
| GET | `/wards` | JSON array of cleaned records: `wardId`, `wing`, `department`, `bedsAvailable` (may be `null`), `notes` |

## Test

Unit tests (JUnit 5) cover each cleaning rule, the CSV reader, the row cleaner, duplicate
merging, and the real `wards-outdated.csv` end to end:

```
mvn test
```

To check the running service by hand:

```
curl http://localhost:7030/health   # -> OK
curl http://localhost:7030/wards    # -> 17 cleaned ward records
```

The whole chain is checked end to end by `scripts/smoke-test.sh` in the project root.