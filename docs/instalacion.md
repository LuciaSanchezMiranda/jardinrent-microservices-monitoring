# Instalación desde cero (Windows 11 + WSL 2)

No se utiliza código ni manifiestos de BerryControl ni del taller. Estos comandos preparan una máquina nueva; si las herramientas ya existen, se comprueban sus versiones sin reinstalarlas.

## Windows, PowerShell como administrador

```powershell
wsl --install -d Ubuntu
winget install --exact --id Docker.DockerDesktop
```

Reiniciar si se solicita y crear el usuario Ubuntu. Abrir Docker Desktop, usar contenedores Linux y habilitar **Settings → Resources → WSL Integration → Ubuntu**. No es necesario habilitar Kubernetes de Docker Desktop.

## Ubuntu / WSL

```bash
sudo apt update
sudo apt install -y git curl ca-certificates python3 openjdk-17-jdk maven
git clone https://github.com/LuciaSanchezMiranda/jardinrent-microservices-monitoring.git
cd jardinrent-microservices-monitoring
git switch develop
bash scripts/install-tools.sh
export PATH="$HOME/.local/share/jardinrent/bin:$PATH"
java -version
mvn -version
docker version
docker ps
minikube version
kubectl version --client
helm version
```

`install-tools.sh` instala Minikube 1.38.1, kubectl 1.34.0 y Helm 3.17.3 en un directorio propio del usuario. El clúster utiliza Kubernetes 1.34.0. Para las operaciones del laboratorio, `common.sh` también usa el kubectl correspondiente al nodo y no modifica el contexto activo. Docker Desktop debe tener recursos suficientes; configuración del laboratorio: cuatro CPU y 6 GiB para Minikube, además de los recursos de Windows y otras aplicaciones.

## Ejecución completa

```bash
export GRAFANA_ADMIN_PASSWORD='TuClavePropiaDeLaboratorio'
bash scripts/setup.sh
```

La variable se guarda en un Secret de Kubernetes. Si no se define se usa la clave demo documentada en README; es deliberadamente una cuenta local de laboratorio. No guardes una contraseña personal en Git.

El instalador de herramientas requiere Internet; `setup.sh` verifica las herramientas y Docker, inicia el perfil exclusivo, instala el chart fijado en `k8s/monitoring/chart-version.txt`, construye ambas imágenes con pruebas Java 17, despliega y verifica. No requiere Maven ni Java en el host si únicamente se ejecuta desde Docker.

Los accesos `localhost` son port-forwards para que funcionen en Windows/WSL. Los cuatro Services siguen siendo NodePort, como exige la actividad. Si se reinicia WSL, ejecutar `bash scripts/access.sh`. Como alternativa: `minikube -p jardinrent service herramientas -n jardinrent --url` y mantener abierto el túnel cuando lo requiera el driver.

## Referencias oficiales consultadas

- https://docs.docker.com/desktop/features/wsl/
- https://minikube.sigs.k8s.io/docs/start/
- https://minikube.sigs.k8s.io/docs/tasks/docker_daemon/
- https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/
- https://helm.sh/docs/intro/install/
- https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack
