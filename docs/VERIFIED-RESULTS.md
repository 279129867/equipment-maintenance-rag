# 真机验证结果

以下都是本机实际跑出来的输出，不是设计稿。标注 ⚠️ 的是**接入大模型之前**（哈希向量 + 抽取式）的数字，接入 bge-m3 + Qwen 之后的对比见根目录 README。

---

## 1. 点检 → 自动建单 → 关单（业务闭环）

```
点检提交（主轴油温 48.5℃ 超标 + 主轴异响）
  → recordId=1 result=2 abnormalCount=2 orderId=2 WO20261119121710248
  → 工单 sourceType=2 sourceRefId=1 urgency=1 status=10 faultType=点检异常
  → 设备 run_status 1(运行) → 3(维修中)

工单 DISPATCH → ACCEPT → FINISH → PASS
  → 20 → 30 → 40 → 50，事件 5 条
  → 设备 run_status 自动恢复 1(运行)
```

另一条容器环境重跑的端到端：

```
POST /api/inspection/records  主轴油温实测 52℃（上限 45）
  → recordId=1 result=2 abnormalCount=1
  → 自动建单 WO20261119151731815  sourceType=2(点检异常) urgency=1(A类设备) status=10
  → faultDesc="点检异常：主轴油温 实测52℃（标准 油温不超过 45℃）"
POST /api/workorders/{id}/action DISPATCH
  → status 20，事件 2 条（CREATE / DISPATCH）
```

## 2. 停机统计（14 单，含 12 条演示数据）

```
OVERVIEW  orderCount=14 closedCount=11 openCount=3
          downtimeMinutes=920 mttrMinutes=77 responseMinutes=16
          abnormalRecordCount=1 inspectionCount=2

ASSETS    IJM-B-02 注塑机       工单4 停机480min MTTR160 响应17
          CNC-A-01 立式加工中心 工单7 停机335min MTTR 56 响应16
          AGV-C-03 搬运AGV      工单3 停机105min MTTR 35 响应15

WORKSHOPS 二号车间 4单/480min   一号车间 7单/335min   立体库 3单/105min

TREND     11-12 75min  11-13 35  11-14 120  11-15 180
          11-16 145    11-17 60  11-18 65   11-19 240   （合计 920，与总览一致）

时间窗    2026-11-18~19：7 单 / 305min / MTTR 61
```

**交叉校验**：按设备停机之和 480+335+105=920，按车间之和亦为 920，按天趋势之和亦为 920 —— 三个维度自洽。

## 3. RAG 问答

⚠️ 以下为哈希向量 + 抽取式时期的输出（示例手册 3 块，`samples/CNC-A-01维修手册.txt`）：

```
Q: 主轴油温超过45度应该怎么处理
A: 主轴油温超过 45℃ 时系统报警；先检查冷却机冷媒压力与过滤网是否堵塞，过滤网堵塞是最常见原因，
   清洗过滤网后油温通常在 15 分钟内回落至 40℃ 以下。
   （来源：《CNC-A-01维修手册.txt》§1.2 主轴油温过高 第1页）  top=0.512  引用2条

Q: 气压低于0.5MPa还能继续加工吗
A: 气压低于 0.5 MPa 时夹具夹紧力不足，禁止继续加工。（来源：…§1.2 …第1页）  top=0.561

Q: 今天食堂几点开饭
A: 知识库中没有找到足够相关的内容（最高相关度 0.095，阈值 0.22）。建议补充手册或转人工判断。
   引用 0 条   ← 阈值降级生效，不编答案

Q: 维修前需要做哪些安全措施
A: 维修前必须执行停机、断电、挂牌上锁，等待主轴完全静止并泄压后方可拆装。（§第三章 导轨与进给 第1页）
```

接入 bge-m3 + Qwen2.5-7B 之后（compose 与 K3s 各跑一次）：

```
mode=llm  topScore=0.818  引用 2 条  latency 4.6~6.5s
答案：归纳成 5 条处置清单，带 [1][2] 编号，安全提示单独一行

库外问题「注塑机模具冷却水路结垢怎么清洗」
  → "知识库中未找到相关内容，缺少清洗方法和步骤"（检索仍给出 0.695 的块，被生成层挡住）
```

## 4. 相似历史工单

⚠️ 哈希向量时期（索引 14 条后）：

```
GET /api/rag/similar-orders?orderId=2&topK=3
WO-DEMO-01       机械  主轴运转周期性异响，转速升高后明显        score=0.245
WO20261119120902 机械  主轴运转时有周期性异响，伴随轻微振动      score=0.222
WO-DEMO-07       机械  顶针机构复位异响，顶出不到位              score=0.180
```

bge-m3 之后同一请求：`0.627 / 0.585 / 0.566`，同类故障仍然排在前。

索引时机：`PASS` 关闭 → 事务提交后 `OrderClosedEvent` 触发索引；另有 `POST /api/rag/index-orders` 做全量重建。

## 5. 容器化（compose 两个容器）

```
eqmaint-mysql  Up (healthy)  0.0.0.0:3307->3306
eqmaint-app    Up (healthy)  0.0.0.0:8080->8080

GET /api/health           {"app":"eqmaint-backend","dbPing":1,"schema":"repair_order"}
GET /api/assets           3 台（种子设备）
GET /api/stats/overview   orderCount=12 closedCount=9 openCount=3 downtime=920 mttr=92
上传 samples/CNC-A-01维修手册.txt → id=1 chunks=3 status=2
POST /api/rag/ask「主轴油温超过45度应该怎么处理」→ 命中 §1.2
```

注：容器里的库是全新初始化的，只有 12 条演示工单，没有本机手工建的两单，所以 MTTR 92 与本机 77 不同 —— 口径没变，是样本不同。

## 6. 前端 6 视图（无头渲染验证，未开浏览器）

```
GET /                            200  30,164 B
GET /app.js                      200  20,851 B
GET /style.css                   200   6,399 B
GET /vendor/vue.global.prod.js   200 157,924 B

用 jsdom 加载真实的 index.html + vue.global.prod.js + app.js，fetch 指向 8080：
工单列表   列表渲染 Ok / 中文故障描述 Ok / 无乱码字符
工单详情   流转记录 Ok（CREATE→DISPATCH…）/ 相似历史工单 Ok / 页内表格 3 行
统计页     MTTR Ok / 车间中文 Ok
点检页     计划 IP-CNC-01 Ok / 点检项 主轴油温 Ok
知识问答   文档 CNC-A-01维修手册 Ok
运行期错误 无
```

模板另外用 `@vue/compiler-dom` 单独编译过一遍（0 error 0 warn），`node --check app.js` 也通过 —— 这两步能在不开浏览器的情况下挡住绝大多数模板/语法错误。

## 7. 演示数据

`03-演示数据.sql` 写入 12 条虚拟工单（`WO-DEMO-xx`），`db-init/02-demo-orders.sql` 是容器初始化用的同内容副本，且会按每条工单的 `report_time / dispatch_time / accept_time / finish_time` 反推出 `wo_order_event` 流水（CREATE / DISPATCH / ACCEPT / FINISH / PASS），否则工单详情页的时间轴是空的 —— 演示页面上最丢分的往往是"数据不完整"，不是"代码不漂亮"。

```sql
-- 清除演示数据
DELETE FROM repair_order.wo_order WHERE order_no LIKE 'WO-DEMO-%';
```

## 8. 几个和编排无关、但演示时会咬人的坑

* **容器首次初始化中文会乱码**。`db-init/*.sql` 是 UTF-8，但 MySQL 官方镜像初始化时用的 `mysql` 客户端字符集不保证 utf8mb4，中文被按 latin1 解码入库，读出来是 `ç«‹å¼å·¥ä¸å¿ƒ`。修法是每个脚本开头 `SET NAMES utf8mb4;`（已加）。这类"看起来像乱码的乱码"入库后再怎么查都查不出来。
* **MySQL 冷启动 5~7 分钟**，期间 `mysqladmin ping` 用的 root 密码还没生效，健康检查一直 unhealthy，`depends_on: condition: service_healthy` 会把应用容器拦住。正常现象，不是故障。
* **宿主机端口用 3307 而不是 3306**，因为开发机上通常已装 MySQL 服务；容器化不是为了在开发机上抢端口。
* **换向量模型后每套环境都要 reindex 一次**：compose 与 K3s 各自有独立 MySQL，向量不共享。
