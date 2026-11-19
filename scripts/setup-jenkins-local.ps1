# 在本机起一个能跑通本项目的 Jenkins（可重复执行）
#
# 官方镜像只带 git 和 JDK，Pipeline 插件、docker CLI、kubectl、maven 都要自己补；
# 容器内 IPv6 出口不可达，所以所有 JVM（Jenkins 与 Maven）都要强制 IPv4。
#
# 用法：pwsh -File scripts/setup-jenkins-local.ps1
#       前置：Docker Desktop 已启动、K3s 容器（k3s-eqmaint）已在跑、kubeconfig 已导出

param(
    [string]$ContainerName = "jenkins",
    [int]$HostPort = 8090,
    [string]$Kubeconfig = (Join-Path $env:USERPROFILE "k3s-eqmaint.yaml")
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Bin = "C:\Temp\jenkinsbin"

function Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }

Step 1 "拉镜像"
docker pull jenkins/jenkins:lts-jdk21 --quiet | Out-Null

Step 2 "起容器（root + docker.sock + 项目挂载 + kubeconfig）"
$exists = docker ps -a --filter "name=^$ContainerName$" --format "{{.Names}}"
if ($exists) { docker rm -f $ContainerName | Out-Null }
docker run -d --name $ContainerName --user root `
    -p "$($HostPort):8080" -p 50000:50000 `
    -v "$($ContainerName)-home:/var/jenkins_home" `
    -v /var/run/docker.sock:/var/run/docker.sock `
    -v "${Root}:/project" `
    -v "${Kubeconfig}:/var/jenkins_home/.kube/k3s.yaml:ro" `
    -e JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Duser.timezone=Asia/Shanghai" `
    -e TZ=Asia/Shanghai `
    jenkins/jenkins:lts-jdk21 | Out-Null

Start-Sleep -Seconds 40

Step 3 "补 kubectl / docker CLI / maven"
New-Item -ItemType Directory -Force -Path $Bin | Out-Null
curl.exe -sL -o "$Bin\kubectl" "https://dl.k8s.io/release/v1.30.4/bin/linux/amd64/kubectl"
docker create --name tmpcli docker:cli | Out-Null
docker cp tmpcli:/usr/local/bin/docker "$Bin\docker" | Out-Null
docker rm -f tmpcli | Out-Null
docker create --name tmpmvn maven:3.9-eclipse-temurin-21 | Out-Null
docker cp tmpmvn:/usr/share/maven "$Bin\maven" | Out-Null
docker rm -f tmpmvn | Out-Null

docker cp "$Bin\kubectl" "${ContainerName}:/usr/local/bin/kubectl"
docker cp "$Bin\docker"  "${ContainerName}:/usr/local/bin/docker"
docker cp "$Bin\maven"   "${ContainerName}:/usr/share/maven"
docker exec $ContainerName chmod +x /usr/local/bin/kubectl /usr/local/bin/docker
docker exec $ContainerName ln -sf /usr/share/maven/bin/mvn /usr/local/bin/mvn

Step 4 "装插件（强制 IPv4，否则容器内解析到 AAAA 记录会 Network is unreachable）"
docker exec -e JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true" $ContainerName `
    jenkins-plugin-cli --plugins workflow-aggregator timestamper ws-cleanup git
# jenkins-plugin-cli 写的是 /usr/share/jenkins/ref/plugins，容器已运行时需拷进 home
docker exec $ContainerName sh -c "cp -a /usr/share/jenkins/ref/plugins/. /var/jenkins_home/plugins/ && chown -R jenkins:jenkins /var/jenkins_home/plugins"
docker restart $ContainerName | Out-Null
Start-Sleep -Seconds 40

Step 5 "容器内 kubeconfig：server 换成 host.docker.internal，去掉 CA 数据改用跳过校验"
docker exec $ContainerName sh -c "mkdir -p /root/.kube && sed 's#https://127.0.0.1:6443#https://host.docker.internal:6443#; s#    server: https://host.docker.internal:6443#    server: https://host.docker.internal:6443\n    insecure-skip-tls-verify: true#' /var/jenkins_home/.kube/k3s.yaml | grep -v certificate-authority-data > /root/.kube/config && chmod 600 /root/.kube/config"
docker exec $ContainerName kubectl get nodes

Step 6 "建 job"
pwsh -File (Join-Path $Root "scripts\create-jenkins-job.ps1") -JenkinsUrl "http://localhost:$HostPort"

Write-Host "`nJenkins: http://localhost:$HostPort/" -ForegroundColor Green
Write-Host "触发构建：curl -X POST 'http://localhost:$HostPort/job/eqmaint-pipeline/buildWithParameters?REGISTRY='" -ForegroundColor Green
