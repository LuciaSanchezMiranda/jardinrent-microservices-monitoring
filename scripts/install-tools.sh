#!/usr/bin/env bash
# Instalación independiente para Ubuntu/WSL x86-64. No cambia ~/.bashrc ni proyectos ajenos.
set -Eeuo pipefail
[[ "$(uname -m)" == x86_64 ]] || { echo "Este instalador utiliza binarios amd64"; exit 1; }
BIN="$HOME/.local/share/jardinrent/bin"
mkdir -p "$BIN"
TMP="$(mktemp -d)"
trap 'rm -f "$TMP/minikube" "$TMP/kubectl" "$TMP/kubectl.sha256" "$TMP/helm.tar.gz" "$TMP/helm.tar.gz.sha256sum"; rm -rf -- "$TMP/linux-amd64"; rmdir "$TMP" 2>/dev/null || true' EXIT
curl -fL https://github.com/kubernetes/minikube/releases/download/v1.38.1/minikube-linux-amd64 -o "$TMP/minikube"
install -m 0755 "$TMP/minikube" "$BIN/minikube"
curl -fL https://dl.k8s.io/release/v1.34.0/bin/linux/amd64/kubectl -o "$TMP/kubectl"
curl -fL https://dl.k8s.io/release/v1.34.0/bin/linux/amd64/kubectl.sha256 -o "$TMP/kubectl.sha256"
printf '%s  %s\n' "$(cat "$TMP/kubectl.sha256")" "$TMP/kubectl" | sha256sum --check
install -m 0755 "$TMP/kubectl" "$BIN/kubectl"
curl -fL https://get.helm.sh/helm-v3.17.3-linux-amd64.tar.gz -o "$TMP/helm.tar.gz"
tar -xzf "$TMP/helm.tar.gz" -C "$TMP"
install -m 0755 "$TMP/linux-amd64/helm" "$BIN/helm"
printf 'Ejecuta en esta terminal:\nexport PATH="$HOME/.local/share/jardinrent/bin:$PATH"\n'
