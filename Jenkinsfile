// Jenkins 声明式流水线：提交 → 构建 → 镜像 → 发布 → 部署 → 冒烟（个人练习项目）
//
// 本文件的两种形态都用这一份定义，靠参数区分：
//   REGISTRY 填仓库地址  → 生产形态：docker build + docker push 到私服（README「方式三」）
//   REGISTRY 留空        → 本机形态：没有私服，改为把镜像直接 import 进本机 K3s 的 containerd
//                          （k3d/rancher/k3s 都是独立 containerd，看不到宿主 docker 的镜像，
//                           所以这一步等价于 k3d image import）
//
// 本机形态额外说明：
//   * 源码来源用挂载目录 /project（本机没有 Git 服务，生产形态是 checkout scm）
//   * 冒烟地址走 host.docker.internal，因为 Jenkins 在容器里，集群端口在宿主上
//   * 强制 IPv4：容器内 IPv6 出网不可达，JVM 默认优先 IPv6 会导致下依赖/插件时"Network is unreachable"

pipeline {
    agent any

    parameters {
        string(name: 'REGISTRY', defaultValue: '', description: '镜像仓库地址；留空=本机形态（跳过 push，改为导入本机 K3s）')
        string(name: 'K3S_CONTAINER', defaultValue: 'k3s-eqmaint', description: '本机 K3s 容器名（仅本机形态使用）')
        string(name: 'SMOKE_URL', defaultValue: 'http://host.docker.internal:8081/api/health', description: '部署后冒烟检查地址')
    }

    environment {
        IMAGE_NAME         = 'eqmaint/backend'
        IMAGE_TAG          = "1.0.${BUILD_NUMBER}"
        K8S_NS             = 'eqmaint'
        JAVA_HOME          = '/opt/java/openjdk'
        JAVA_TOOL_OPTIONS  = '-Djava.net.preferIPv4Stack=true'
        KUBECONFIG         = '/root/.kube/config'
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                sh '''
                    echo "源码来源: 挂载目录 /project（生产形态为 checkout scm）"
                    mkdir -p workspace-src
                    cp -a /project/. . || true
                    rm -rf .git backend/target
                    ls -1 backend/src/main/resources/static | head -5
                '''
            }
        }

        stage('Build & Package') {
            steps {
                dir('backend') {
                    sh 'mvn -B -DskipTests clean package'
                }
            }
            post {
                success {
                    archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true
                }
            }
        }

        stage('Prepare') {
            steps {
                script {
                    env.FULL_IMAGE = params.REGISTRY?.trim() ?
                        "${params.REGISTRY}/${env.IMAGE_NAME}:${env.IMAGE_TAG}" :
                        "${env.IMAGE_NAME}:${env.IMAGE_TAG}"
                    echo "本次构建镜像: ${env.FULL_IMAGE}"
                }
            }
        }

        stage('Docker Image') {
            steps {
                dir('backend') {
                    sh 'docker build -t "$FULL_IMAGE" -t "$IMAGE_NAME:latest" .'
                }
            }
        }

        stage('Publish') {
            steps {
                script {
                    if (params.REGISTRY?.trim()) {
                        sh '''
                            docker push "$FULL_IMAGE"
                            docker push "$IMAGE_NAME:latest"
                        '''
                    } else {
                        sh '''
                            echo "本机形态：无私有仓库，把镜像导入 K3s 容器内 containerd"
                            docker save "$FULL_IMAGE" | docker exec -i "$K3S_CONTAINER" ctr -n k8s.io images import -
                        '''
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    kubectl -n "$K8S_NS" set image deployment/eqmaint-app app="$FULL_IMAGE"
                    kubectl -n "$K8S_NS" rollout status deployment/eqmaint-app --timeout=180s
                '''
            }
        }

        stage('Smoke Test') {
            steps {
                sh '''
                    for i in $(seq 1 10); do
                        if curl -sf "$SMOKE_URL"; then
                            echo "健康检查通过（第 $i 次）"; exit 0
                        fi
                        sleep 3
                    done
                    echo "健康检查失败"; exit 1
                '''
            }
        }
    }

    post {
        success {
            echo "部署成功：${env.FULL_IMAGE}"
        }
        failure {
            echo "流水线失败，查看上方 stage 定位（构建 / 镜像 / 发布 / 部署 / 健康检查）"
        }
        always {
            sh 'rm -rf workspace-src'
            cleanWs()
        }
    }
}
