# 在本机 Jenkins 里创建/更新流水线 job（把仓库里的 Jenkinsfile 注入成 job 定义）
#
# 为什么不是 "Pipeline script from SCM"：本机没有 Git 服务，也没有 git 插件可用；
# 生产环境应改为 SCM 方式，让 Jenkins 直接拉 Jenkinsfile。这里用 Jenkinsfile 的内容注入，
# 保证仓库里这份文件仍是唯一事实来源（改完 Jenkinsfile 重跑本脚本即可同步）。
#
# 用法：pwsh -File scripts/create-jenkins-job.ps1
#       可选 -JenkinsUrl http://localhost:8090 -JobName eqmaint-pipeline

param(
    [string]$JenkinsUrl = "http://localhost:8090",
    [string]$JobName = "eqmaint-pipeline"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$JenkinsfilePath = Join-Path $Root "Jenkinsfile"

function XmlEscape([string]$s) {
    $s.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;").Replace("""", "&quot;")
}

$script_text = XmlEscape (Get-Content -LiteralPath $JenkinsfilePath -Raw)

$config = @"
<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin="workflow-job">
  <description>设备维保工单系统：从提交到部署的流水线（个人练习项目，虚拟产线数据）</description>
  <keepDependencies>false</keepDependencies>
  <properties>
    <hudson.model.ParametersDefinitionProperty>
      <parameterDefinitions>
        <hudson.model.StringParameterDefinition>
          <name>REGISTRY</name>
          <defaultValue></defaultValue>
          <description>镜像仓库地址；留空=本机形态（跳过 push，改为导入本机 K3s）</description>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>K3S_CONTAINER</name>
          <defaultValue>k3s-eqmaint</defaultValue>
          <description>本机 K3s 容器名（仅本机形态使用）</description>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>SMOKE_URL</name>
          <defaultValue>http://host.docker.internal:8081/api/health</defaultValue>
          <description>部署后冒烟检查地址</description>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
      </parameterDefinitions>
    </hudson.model.ParametersDefinitionProperty>
  </properties>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition" plugin="workflow-cps">
    <script>$script_text</script>
    <sandbox>true</sandbox>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>
"@

# Jenkins 未开安全时仍要求 CSRF crumb，取 crumb + cookie 一起提交
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$crumbInfo = Invoke-RestMethod -Uri "$JenkinsUrl/crumbIssuer/api/json" -WebSession $session
$headers = @{ $crumbInfo.crumbRequestField = $crumbInfo.crumb }

$exists = $false
try {
    Invoke-RestMethod -Uri "$JenkinsUrl/job/$JobName/api/json" -WebSession $session -Method Get | Out-Null
    $exists = $true
} catch { $exists = $false }

if ($exists) {
    Invoke-RestMethod -Uri "$JenkinsUrl/job/$JobName/config.xml" -WebSession $session -Method Post `
        -ContentType "application/xml" -Body ([Text.Encoding]::UTF8.GetBytes($config)) -Headers $headers | Out-Null
    Write-Host "已更新 job：$JobName"
} else {
    Invoke-RestMethod -Uri "$JenkinsUrl/createItem?name=$JobName" -WebSession $session -Method Post `
        -ContentType "application/xml" -Body ([Text.Encoding]::UTF8.GetBytes($config)) -Headers $headers | Out-Null
    Write-Host "已创建 job：$JobName"
}

Write-Host "地址：$JenkinsUrl/job/$JobName/"
