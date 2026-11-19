# 在 Windows 本机起一个单节点 K3s，并把本项目部署上去（可重复执行）
#
# 背景：Windows 没有原生 K3s；通用做法是 k3d（K3s in Docker），但 k3d.exe 要从 GitHub 下载，
#       本机网络拿不到，所以直接用官方 rancher/k3s 镜像起单节点 —— 与 k3d 本质相同。
# 用法：pwsh -File k8s/deploy-k3s-local.ps1
#       可选参数：-HostPort 8081（宿主映射端口，默认 8081，避让 docker compose 的 8080）

param(
    [string]$ClusterName = "k3s-eqmaint",
    [int]$HostPort = 8081,
    [string]$Image = "rancher/k3s:v1.30.4-k3s1",
    [string]$Mirror = "https://docker.m.daocloud.io"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Kubeconfig = Join-Path $env:USERPROFILE "k3s-eqmaint.yaml"

function Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }

Step 1 "启动 K3s 容器（$HostPort -> 80，6443 暴露 API）"
$exists = docker ps -a --filter "name=^$ClusterName$" --format "{{.Names}}"
if ($exists) {
    docker start $ClusterName | Out-Null
} else {
    docker run -d --name $ClusterName --privileged --restart=unless-stopped `
        -p 6443:6443 -p "$($HostPort):80" `
        -e TZ=Asia/Shanghai `
        -v "$($ClusterName)-data:/var/lib/rancher/k3s" `
        $Image server --write-kubeconfig-mode=644 | Out-Null
}

Step 2 "配置国内镜像源（不配的话 k3s 内部 containerd 直连 Docker Hub 会被拒）"
$registries = @"
mirrors:
  docker.io:
    endpoint:
      - "$Mirror"
"@
docker exec $ClusterName sh -c "mkdir -p /etc/rancher/k3s && cat > /etc/rancher/k3s/registries.yaml <<'EOF'`n$registries`nEOF"

Step 3 "导出 kubeconfig 并等待节点 Ready"
cmd /c "docker exec $ClusterName cat /etc/rancher/k3s/k3s.yaml > `"$Kubeconfig`""
$env:KUBECONFIG = $Kubeconfig
kubectl wait --for=condition=Ready node --all --timeout=180s

Step 4 "把本地镜像导入集群（k3s 用独立 containerd，看不到 docker 的镜像）"
foreach ($img in @("mysql:8.0", "eqmaint/backend:1.0.0")) {
    $tar = Join-Path $env:TEMP "k3s-import.tar"
    docker save -o $tar $img
    docker cp $tar "$($ClusterName):/tmp/img.tar" | Out-Null
    docker exec $ClusterName ctr -n k8s.io images import /tmp/img.tar | Select-Object -Last 1
    docker exec $ClusterName rm -f /tmp/img.tar
    Remove-Item $tar -Force
}

Step 5 "应用清单 + 建库脚本 ConfigMap"
Push-Location $Root
try {
    kubectl apply -f k8s/app.yaml
    kubectl create configmap eqmaint-db-init -n eqmaint --from-file=db-init/ --dry-run=client -o yaml |
        kubectl apply -f -
    kubectl apply -f k8s/mysql.yaml
} finally {
    Pop-Location
}

Step 6 "等待滚动完成（MySQL 首次初始化约 7 分钟，期间不要重启集群）"
kubectl -n eqmaint rollout status deployment/mysql --timeout=900s
kubectl -n eqmaint rollout status deployment/eqmaint-app --timeout=300s

Step 7 "冒烟"
kubectl -n eqmaint get pods,pvc
$health = Invoke-RestMethod "http://localhost:$HostPort/api/health"
Write-Host "health = $($health | ConvertTo-Json -Compress)"
Write-Host "打开 http://localhost:$HostPort/ 即为完整前端" -ForegroundColor Green
