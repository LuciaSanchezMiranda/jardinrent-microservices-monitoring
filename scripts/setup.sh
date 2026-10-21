#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
trap 'echo "Falló setup en la línea $LINENO. Revisa el mensaje anterior." >&2' ERR
require_tools
cd "$ROOT"
echo "1/6 · Cluster exclusivo $PROFILE"
if ! minikube -p "$PROFILE" status >/dev/null 2>&1; then
  minikube start -p "$PROFILE" --driver=docker --container-runtime=docker --kubernetes-version=v1.34.0 \
    --cpus="${CLUSTER_CPUS:-4}" --memory="${CLUSTER_MEMORY:-6144}" --keep-context
else
  echo "Perfil $PROFILE ya está ejecutándose; no se reinicia el nodo."
fi
minikube -p "$PROFILE" addons enable metrics-server
kc create namespace monitoring --dry-run=client -o yaml | kc apply -f -
kc apply -f k8s/namespace.yaml
echo "2/6 · Stack de monitoreo"
kc -n monitoring create secret generic jardinrent-grafana-admin \
  --from-literal=admin-user=admin \
  --from-literal=admin-password="${GRAFANA_ADMIN_PASSWORD:-JardinRent-Lab2026!}" \
  --dry-run=client -o yaml | kc apply -f -
kc -n monitoring create configmap jardinrent-dashboard --from-file=jardinrent.json=docs/dashboard.json \
  --dry-run=client -o yaml | kc apply -f -
kc -n monitoring label configmap jardinrent-dashboard grafana_dashboard=1 --overwrite
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
helm repo update prometheus-community
helm upgrade --install observabilidad prometheus-community/kube-prometheus-stack \
  --version "$(tr -d '\r\n' < k8s/monitoring/chart-version.txt)" \
  --kube-context "$PROFILE" -n monitoring -f k8s/monitoring/values.yaml --wait --timeout 15m
echo "3/6 · Imágenes locales y pruebas Java 17 (sin reutilizar imágenes del taller)"
(
  eval "$(minikube -p "$PROFILE" docker-env --shell bash)"
  docker build --progress=plain -t jardinrent/herramientas:1.0.0 -f microservices/herramientas-service/Dockerfile .
  docker build --progress=plain -t jardinrent/cotizaciones:1.0.0 -f microservices/cotizaciones-service/Dockerfile .
)
echo "4/6 · Despliegues, monitores y reglas"
kc apply -f k8s/herramientas/ -f k8s/cotizaciones/
kc -n jardinrent rollout restart deployment/herramientas deployment/cotizaciones
kc apply -f k8s/monitoring/herramientas-servicemonitor.yaml -f k8s/monitoring/cotizaciones-servicemonitor.yaml -f k8s/monitoring/alerts.yaml
kc -n jardinrent rollout status deployment/herramientas --timeout=180s
kc -n jardinrent rollout status deployment/cotizaciones --timeout=180s
echo "5/6 · Acceso desde navegador (también en Windows con WSL)"
bash scripts/access.sh
echo "6/6 · Tráfico inicial y verificación"
bash load-testing/stress.sh http://localhost:18082 10 2
bash scripts/verify.sh
echo "Sistema listo. Contraseña Grafana: la variable GRAFANA_ADMIN_PASSWORD o la clave demo indicada en README."
