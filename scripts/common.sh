#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="$HOME/.local/share/jardinrent/bin:$PATH"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="${PROFILE:-jardinrent}"
# Impide ejecutar scripts del laboratorio sobre un clúster ajeno por un typo.
[[ "$PROFILE" == jardinrent* ]] || { echo "PROFILE debe comenzar con jardinrent" >&2; exit 1; }
RUNTIME="$ROOT/.runtime"
mkdir -p "$RUNTIME"
# Cliente kubectl de la versión del nodo; contexto explícito sin cambiar el actual.
kc() {
  local client="$HOME/.minikube/cache/linux/amd64/v1.34.0/kubectl"
  if [[ -x "$client" ]]; then "$client" --context="$PROFILE" "$@";
  else minikube -p "$PROFILE" kubectl -- --context="$PROFILE" "$@"; fi
}
require_tools() {
  for tool in docker minikube kubectl helm curl python3; do
    command -v "$tool" >/dev/null || { echo "Falta $tool. Consulta docs/instalacion.md" >&2; exit 1; }
  done
  docker info >/dev/null 2>&1 || { echo "Inicia Docker Desktop con integración WSL" >&2; exit 1; }
}
stop_forward() {
  local name="$1" pid args
  if [[ -f "$RUNTIME/$name.pid" ]]; then
    pid="$(cat "$RUNTIME/$name.pid")"
    args="$(ps -p "$pid" -o args= 2>/dev/null || true)"
    if [[ "$args" == *"port-forward"* && "$args" == *"$PROFILE"* ]]; then kill "$pid" 2>/dev/null || true; fi
    rm -f "$RUNTIME/$name.pid"
  fi
}
start_forward() {
  local name="$1" namespace="$2" service="$3" binding="$4"
  stop_forward "$name"
  # nohup conserva el acceso al finalizar setup; los PID quedan sólo en .runtime.
  local kubectl_bin="${HOME}/.minikube/cache/linux/amd64/v1.34.0/kubectl"
  [[ -x "$kubectl_bin" ]] || kubectl_bin="$(command -v kubectl)"
  nohup "$kubectl_bin" --context="$PROFILE" -n "$namespace" port-forward "service/$service" "$binding" \
    >"$RUNTIME/$name.log" 2>&1 < /dev/null &
  echo "$!" > "$RUNTIME/$name.pid"
  sleep 0.2
}
