#!/usr/bin/env bash
# End-to-end smoke test for the HealthSafe core (stages 1-2).
#
# Start these first, each in its own terminal, in this order:
#   ingestion-service (7030), ward-service (7031),
#   alert-level-service (7032), staffing-service (7033)
# then run:   bash scripts/smoke-test.sh
#
# NOTE: this test CHANGES the hospital alert level (it raises it to 8 to prove the
# schedule reacts) and sets it back to 0 at the end.

set -u

INGESTION=${INGESTION_URL:-http://localhost:7030}
WARD=${WARD_URL:-http://localhost:7031}
ALERT=${ALERT_LEVEL_URL:-http://localhost:7032}
STAFFING=${STAFFING_URL:-http://localhost:7033}

pass=0
fail=0

# check NAME EXPECTED_STATUS EXPECTED_BODY_FRAGMENT [curl arguments...]
# Passes only if the HTTP status matches AND the body contains the fragment.
check() {
  local name=$1 want_status=$2 want_body=$3
  shift 3
  local out status body
  # -w appends the status code on its own last line; curl prints 000 if it cannot connect
  out=$(curl -s -w $'\n%{http_code}' "$@")
  status=${out##*$'\n'}
  body=${out%$'\n'*}
  if [[ "$status" == "$want_status" && "$body" == *"$want_body"* ]]; then
    echo "PASS  $name"
    pass=$((pass + 1))
  else
    echo "FAIL  $name"
    echo "        expected HTTP $want_status with body containing: $want_body"
    echo "        got      HTTP $status with body: $body"
    fail=$((fail + 1))
  fi
}

put_level() {
  curl -s -X PUT -H "Content-Type: application/json" -d "{\"level\":$1}" "$ALERT/alert-level" > /dev/null
}

echo "== health =="
check "ingestion up"   200 "OK" "$INGESTION/health"
check "ward up"        200 "OK" "$WARD/health"
check "alert-level up" 200 "OK" "$ALERT/health"
check "staffing up"    200 "OK" "$STAFFING/health"

echo "== stage 1: ingestion cleans the CSV =="
check "W-05 duplicate merged into one record" 200 "merged duplicate of rows 5 and 6" "$INGESTION/wards"
check "W-11 Pediatrics spelling unified"      200 "\"wardId\":\"W-11\",\"wing\":\"East Wing\",\"department\":\"Paediatrics\"" "$INGESTION/wards"

echo "== stage 2: ward-service =="
check "lookup ignores case"      200 "\"wardId\":\"W-05\"" "$WARD/wards/w-05"
check "unknown ward is 404"      404 "Unknown ward" "$WARD/wards/W-99"
check "departments list"         200 "Paediatrics" "$WARD/departments"

echo "== stage 2: alert-level-service =="
put_level 0
check "level starts at 0"        200 "{\"level\":0}" "$ALERT/alert-level"
check "level 9 rejected"         400 "between 0 and 8" -X PUT -H "Content-Type: application/json" -d '{"level":9}' "$ALERT/alert-level"
check "malformed body rejected"  400 "error" -X PUT -H "Content-Type: application/json" -d 'not json' "$ALERT/alert-level"

echo "== stage 2: staffing-service (calls ward + alert-level) =="
check "normal level schedule"    200 "\"status\":\"NORMAL\",\"doctorsOnCall\":1" "$STAFFING/schedule/W-05"
put_level 8
check "code blue schedule"       200 "\"status\":\"CODE_BLUE\",\"doctorsOnCall\":9" "$STAFFING/schedule/w-05"
check "unknown ward is 404"      404 "Unknown ward" "$STAFFING/schedule/W-99"
put_level 0

echo
echo "$pass passed, $fail failed"
[[ $fail -eq 0 ]]