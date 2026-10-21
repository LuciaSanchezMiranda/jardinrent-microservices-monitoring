#!/usr/bin/env bash
set -Eeuo pipefail
BASE="${1:-http://localhost:18082}"
DURATION="${2:-60}"
CONCURRENCY="${3:-4}"
[[ "$DURATION" =~ ^[1-9][0-9]*$ && "$CONCURRENCY" =~ ^[1-9][0-9]*$ ]] || { echo "Uso: stress.sh URL segundos concurrencia (enteros positivos)" >&2; exit 1; }
(( CONCURRENCY <= 32 )) || { echo "Máximo 32 workers" >&2; exit 1; }
worker() {
  local until=$((SECONDS + DURATION)) response body code id
  while (( SECONDS < until )); do
    response="$(curl -sS --max-time 10 -w $'\n%{http_code} %{time_total}' -H 'Content-Type: application/json' \
      -d '{"herramientaId":"1","dias":3}' "$BASE/cotizaciones" || true)"
    body="${response%$'\n'*}"; code="${response##*$'\n'}"
    printf '%s worker=%s http_seconds=%s\n' "$(date -u +%FT%TZ)" "$1" "$code"
    # Cotizaciones no son reservas: se eliminan los datos sintéticos para no llenar memoria.
    if [[ "$code" == 201* ]]; then
      id="$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
      curl -fsS --max-time 5 -X DELETE "$BASE/cotizaciones/$id" >/dev/null || true
    fi
    sleep 0.2
  done
}
pids=()
trap 'for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done' EXIT INT TERM
for ((n=1;n<=CONCURRENCY;n++)); do worker "$n" & pids+=("$!"); done
for pid in "${pids[@]}"; do wait "$pid"; done
