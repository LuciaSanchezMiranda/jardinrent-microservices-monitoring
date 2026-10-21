#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
case "${1:-}" in
  crash) kc -n jardinrent set env deployment/herramientas SERVER_PORT=puerto-invalido ;;
  crash-fix) kc -n jardinrent set env deployment/herramientas SERVER_PORT=8080; kc -n jardinrent rollout status deployment/herramientas --timeout=180s; bash "$ROOT/scripts/access.sh" ;;
  slow) kc -n jardinrent set env deployment/herramientas RESPONSE_DELAY_MS=6000; kc -n jardinrent rollout status deployment/herramientas --timeout=180s; bash "$ROOT/scripts/access.sh" ;;
  slow-fix) kc -n jardinrent set env deployment/herramientas RESPONSE_DELAY_MS=0; kc -n jardinrent rollout status deployment/herramientas --timeout=180s; bash "$ROOT/scripts/access.sh" ;;
  stock) curl -fsS -X PATCH -H 'Content-Type: application/json' -d '{"disponibles":0}' http://localhost:18081/herramientas/1/stock ;;
  stock-fix) curl -fsS -X PATCH -H 'Content-Type: application/json' -d '{"disponibles":10}' http://localhost:18081/herramientas/1/stock ;;
  *) echo "Uso: scenario.sh crash|crash-fix|slow|slow-fix|stock|stock-fix"; exit 1 ;;
esac
echo
