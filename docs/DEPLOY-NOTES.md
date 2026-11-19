# 部署实录与踩坑记录

这份文档记录 K3s 与 Jenkins 在本机（Windows + Docker Desktop）真实跑通的过程，以及踩到的坑。**不是抄教程，都是现场输出。**

---

## K3s

Windows 上没有原生 K3s，官方做法是 `k3d`（K3s in Docker）。本机当时 GitHub 下载不通（`k3d.exe` 拉不下来），所以直接用官方 `rancher/k3s` 镜像起单节点 —— k3d 本质也是这件事，只是多了封装。可复现脚本见 `k8s/deploy-k3s-local.ps1`。

### 1. 起集群

```bash
docker run -d --name k3s-eqmaint --privileged --restart=unless-stopped \
  -p 6443:6443 -p 8081:80 \
  -v k3s-eqmaint-data:/var/lib/rancher/k3s \
  rancher/k3s:v1.30.4-k3s1 server --write-kubeconfig-mode=644
```

宿主机 `8081 → 集群 80`，避让 compose 占用的 8080，两套环境可同时演示。

### 2. 让集群里的 containerd 走国内镜像源

```bash
docker exec k3s-eqmaint sh -c 'mkdir -p /etc/rancher/k3s && cat > /etc/rancher/k3s/registries.yaml <<EOF
mirrors:
  docker.io:
    endpoint:
      - "https://docker.m.daocloud.io"
EOF'
```

### 3. 导出 kubeconfig

```bash
docker exec k3s-eqmaint cat /etc/rancher/k3s/k3s.yaml > k3s-eqmaint.yaml
```

`server` 已是 `https://127.0.0.1:6443`，宿主机可直接用（该文件已加入 `.gitignore`，不入库）。

### 4. 镜像进集群

```bash
docker save eqmaint/backend:1.0.3 -o app.tar
docker cp app.tar k3s-eqmaint:/tmp/img.tar
docker exec k3s-eqmaint ctr -n k8s.io images import /tmp/img.tar
```

### 5. 建库初始化 ConfigMap 并部署

```bash
kubectl apply -f k8s/app.yaml
kubectl create configmap eqmaint-db-init -n eqmaint --from-file=db-init/
kubectl apply -f k8s/mysql.yaml
kubectl -n eqmaint rollout status deployment/eqmaint-app
```

### 实测结果

```
NAME                           READY   STATUS    IP
eqmaint-app-79b7ff874b-pd7cb   1/1     Running   10.42.0.x
mysql-66b6b5844d-8ffpd         1/1     Running   10.42.0.x

GET http://localhost:8081/api/health  200  {"app":"eqmaint-backend","dbPing":1,"schema":"repair_order"}
GET http://localhost:8081/            200  SPA 首页（Ingress → Traefik → Service → Pod）
GET http://localhost:8081/api/assets  3 台，中文正常（立式加工中心 / 一号车间）
GET http://localhost:8081/api/stats/overview  orderCount=12 downtime=920 mttr=92
POST /api/rag/documents 上传手册 → 3 块 status=2
POST /api/rag/ask （bge-m3 + Qwen）→ mode=llm topScore=0.793 引用 2 条 延迟 5.3s
GET /api/rag/similar-orders → WO-DEMO-04(0.627) / 09(0.585) / 06(0.566)
kubectl set image → 1.0.1 滚动更新：新 Pod Ready 后旧 Pod 才 Terminating，期间 /api/health 持续 200
kubectl rollout undo → 回到上一个版本，health 仍 200（这是 Jenkinsfile「能回滚」的底气）
```

清单要点：`readinessProbe/livenessProbe` 都打 `/api/health`；DB 口令走 Secret 的 `envFrom`；上传目录挂 PVC；`strategy` 用 `maxUnavailable: 0` 的滚动更新；MySQL 初始化 SQL 用 ConfigMap 挂到 `/docker-entrypoint-initdb.d`。

### 四个真坑

1. **`k8s/mysql.yaml` 原来漏了 `namespace`**
   PVC / Deployment / Service 全落到 `default` 命名空间，应用按短名 `mysql` 去连库时解析不到跨命名空间的 Service —— 现象是 Pod 起来但一直连不上库。所有资源都必须显式写 `namespace: eqmaint`。

2. **k3s 容器里的 containerd 直连 Docker Hub 被拒**
   `dial tcp 202.160.128.40:443: connection refused`，而宿主机 Docker 却能拉。差别在于 Docker Desktop 有自己的代理，k3s 容器是独立进程。解法是 `registries.yaml` 配 `docker.m.daocloud.io`（实测返回 401 属正常的 registry 探测）。

3. **k3s 重启会打断 MySQL 初始化**
   初始化做到一半被 kill，datadir 里留下半截 redo log，再起就是 `Cannot create redo log files because data files are corrupt`，MySQL 直接 CrashLoop。这跟 compose 不一样 —— 命名卷不会因为你重启容器就回滚，只能删 PVC 重来。教训：第一次初始化期间不要去动集群。

4. **镜像必须手动 import**
    k3s 用独立 containerd，`docker images` 里的镜像它看不见，`imagePullPolicy: IfNotPresent` 会让它一直 `ErrImagePull`。`k3d image import` 做的就是这一步（`docker save` + `ctr images import`）。

另外两个如实记录的小现象：MySQL 首次初始化在 k3s 里约 **7 分钟**（和 compose 一样慢，瓶颈是磁盘不是编排）；滚动更新切流量那一瞬间有 **1 次** 请求失败（keep-alive 还连着正在 Terminating 的旧 Pod），对演示无影响，但说明"零停机"是相对的。

---

## Jenkins

`Jenkinsfile` 六段：Checkout → Build & Package（`mvn -B -DskipTests clean package`，产物归档）→ Prepare（解析参数）→ Docker Image → Publish → Deploy（`kubectl set image` + `rollout status`）→ Smoke Test（轮询 `/api/health`）。失败时按 stage 就能定位是构建、镜像、发布还是部署的问题。

同一份 `Jenkinsfile` 覆盖两种环境，靠参数区分：

* `REGISTRY` 填仓库地址 → 生产形态：`docker build` + `docker push`，`Deploy` 拉私有镜像
* `REGISTRY` 留空 → 本机形态：没有私服，`Publish` 改成 `docker save | docker exec -i k3s-eqmaint ctr -n k8s.io images import -`，把镜像直接塞进 K3s 的 containerd

### 本机 Jenkins 怎么搭起来

官方镜像只有 `git` 和 JDK，`mvn` / `kubectl` / `docker` 都要自己补：

```bash
# 1. 起 Jenkins（必须 root，否则 chmod 与 docker.sock 都没权限）
docker run -d --name jenkins --user root -p 8090:8080 \
  -v jenkins-home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v <项目根>:/project \
  -v %USERPROFILE%\k3s-eqmaint.yaml:/var/jenkins_home/.kube/config:ro \
  -e JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Djava.net.preferIPv4Stack=true" \
  jenkins/jenkins:lts-jdk21

# 2. 补三个二进制：kubectl 从 dl.k8s.io 取 linux 版，docker CLI 从 docker:cli 抠，maven 从 maven 镜像抠
docker cp kubectl jenkins:/usr/local/bin/ && docker exec jenkins chmod +x /usr/local/bin/kubectl
docker create --name tmpcli docker:cli && docker cp tmpcli:/usr/local/bin/docker .
docker cp docker jenkins:/usr/local/bin/ && docker exec jenkins chmod +x /usr/local/bin/docker
docker create --name tmpmvn maven:3.9-eclipse-temurin-21 && docker cp tmpmvn:/usr/share/maven .
docker cp maven jenkins:/usr/share/maven && docker exec jenkins ln -s /usr/share/maven/bin/mvn /usr/local/bin/mvn

# 3. 装插件（Pipeline / 归档 / 干净工作区）
docker exec -e JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true" jenkins \
  jenkins-plugin-cli --plugins workflow-aggregator timestamper ws-cleanup
docker exec jenkins sh -c "cp -a /usr/share/jenkins/ref/plugins/. /var/jenkins_home/plugins/ && chown -R jenkins:jenkins /var/jenkins_home/plugins"
docker restart jenkins

# 4. 建 job 并跑一次
pwsh -File scripts/create-jenkins-job.ps1
curl -X POST "http://localhost:8090/job/eqmaint-pipeline/buildWithParameters?REGISTRY="
```

### 实测结果

```
构建 #1：SUCCESS  456s（7m36s）  冷缓存，Maven 首次拉依赖 + 镜像首次构建
构建 #2：SUCCESS  252s（4m12s）  热缓存
构建 #3：SUCCESS  283s（4m43s）  接入 bge-m3 + 对话大模型后的版本，部署为 eqmaint/backend:1.0.3
部署：1.0.1 → 1.0.2 → 1.0.3，冒烟 GET /api/health 200
数据：重新部署后 12 条工单 / 停机 920 / MTTR 92 一条没少（在 PVC 里，不在镜像里）
```

### 四个真坑

1. **容器里 JVM 默认优先 IPv6**，而容器网络只有 IPv4 出口，插件与 Maven 依赖全报 `Network is unreachable`（DNS 把 `updates.jenkins.io` 解析成 `2603:...` 的 AAAA 记录）。加 `-Djava.net.preferIPv4Stack=true` 立刻正常 —— 纯国内网络环境里很常见。
2. **`jenkins-plugin-cli` 默认装到 `/usr/share/jenkins/ref/plugins`** 而不是 `JENKINS_HOME/plugins`：那是给"构建自定义镜像"用的位置，容器已跑起来时装完不生效，必须拷进 home 再重启。
3. **官方镜像默认以 `jenkins`（uid 1000）运行**：`chmod` 自己的二进制报 `Operation not permitted`、`docker.sock` 报 `permission denied`。演示环境 `--user root` 最省事；真实环境应该用 `--group-add` 加 socket 的 gid。
4. **kubeconfig 要改两处才能从容器里连集群**：`server` 从 `127.0.0.1:6443` 换成 `host.docker.internal:6443`（容器里的 127.0.0.1 是它自己）；又因为 k3s 证书 SAN 里没有这个域名，得加 `insecure-skip-tls-verify: true` —— 而 kubectl 不允许它和 `certificate-authority-data` 共存，CA 那行必须删。生产做法是签一个包含正确域名的证书，而不是跳过校验。

### 与生产形态的差距

* job 是用 `Jenkinsfile` 的内容注入的（`scripts/create-jenkins-job.ps1`），不是 `Pipeline script from SCM`：本机没有 Git 服务、也没装 git 插件。生产应改成 SCM 拉取，让 `Checkout` 打出真实 `GIT_COMMIT`。
* 本机 Jenkins 未开安全（`runSetupWizard=false` + 匿名可管理），只适合本机演示。有凭据管理、角色权限要求时必须开 Matrix Authorization，并用 `withCredentials` 注入 kubeconfig。
* 没有私有 registry，所以 `Publish` 走 `docker save | ctr import`；真实环境应走 `docker push` + `imagePullPolicy: Always`，版本由 tag 精确控制。
