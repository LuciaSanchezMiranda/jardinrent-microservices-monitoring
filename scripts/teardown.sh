#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
[[ $# -eq 0 || ( $# -eq 1 && "$1" == --delete-cluster ) ]] || { echo "Uso: teardown.sh [--delete-cluster]"; exit 1; }
for name in herramientas cotizaciones grafana prometheus; do stop_forward "$name"; done
if [[ "${1:-}" == --delete-cluster ]]; then
  minikube delete -p "$PROFILE"
else
  kc delete namespace jardinrent --ignore-not-found
  helm uninstall observabilidad --kube-context "$PROFILE" -n monitoring --ignore-not-found
  kc delete namespace monitoring --ignore-not-found
fi
echo "Recursos de $PROFILE eliminados. Otros perfiles y repositorios no se modifican."
