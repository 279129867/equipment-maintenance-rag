-- =============================================================
-- 设备维保工单系统 + 维修知识 RAG 助手  数据库设计
-- MySQL 8.0.46 / InnoDB / utf8mb4
-- 设计原则：只做一条主线（设备→点检→报修→工单→维修→停机统计→知识沉淀）
-- 表按优先级标注：P0 = 第一周必须，P1 = 演示增强
-- =============================================================

-- 强制客户端连接使用 utf8mb4：容器首次初始化时 mysql 客户端默认字符集不一定是
-- utf8mb4，脚本里的中文会被按 latin1 解码后入库，读出即乱码（如 立式加工中心 → ç«‹å¼åŠ...）
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;

-- 说明：本机 repair 账号没有建库权限（仅被授予 repair_order / repair_kb 两库）
-- 因此全部 14 张表统一建在已授权库 repair_order（utf8mb4 / utf8mb4_0900_ai_ci）
-- repair_kb 暂留空：若日后把知识库独立成库，只需把 km_document / km_chunk 两张表迁过去
USE `repair_order`;

-- -------------------------------------------------------------
-- 1. 设备台账  [P0]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `eq_asset`;
CREATE TABLE `eq_asset` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `asset_code`     VARCHAR(32)  NOT NULL COMMENT '设备编号，如 CNC-A-01',
  `asset_name`     VARCHAR(64)  NOT NULL COMMENT '设备名称',
  `asset_type`     VARCHAR(32)  NOT NULL COMMENT '设备类型：CNC/注塑机/AGV/空压机…（同时作为知识库检索的过滤维度）',
  `workshop`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '车间',
  `line_name`      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '产线',
  `location`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '安装位置',
  `manufacturer`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '制造商',
  `model`          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '型号',
  `serial_no`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '出厂序列号',
  `install_date`   DATE         DEFAULT NULL COMMENT '投用日期',
  `critical_level` TINYINT      NOT NULL DEFAULT 2 COMMENT '关键度 1=A关键 2=B重要 3=C一般（决定报修优先级默认值）',
  `run_status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '运行状态 1=运行 2=停机 3=维修中 4=报废',
  `owner_name`     VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '责任机修/点检员',
  `remark`         VARCHAR(255) NOT NULL DEFAULT '',
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_code` (`asset_code`),
  KEY `idx_type_status` (`asset_type`, `run_status`)
) ENGINE=InnoDB COMMENT='设备台账';

-- -------------------------------------------------------------
-- 2. 点检计划  [P0]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `eq_inspection_plan`;
CREATE TABLE `eq_inspection_plan` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `plan_code`     VARCHAR(32) NOT NULL COMMENT '计划编号',
  `plan_name`     VARCHAR(64) NOT NULL,
  `asset_id`      BIGINT UNSIGNED NOT NULL,
  `cycle_type`    TINYINT     NOT NULL DEFAULT 1 COMMENT '周期类型 1=每班 2=每日 3=每周 4=每月',
  `executor_role` VARCHAR(32) NOT NULL DEFAULT '操作工' COMMENT '执行角色：操作工/机修',
  `enabled`       TINYINT     NOT NULL DEFAULT 1,
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`),
  KEY `idx_asset` (`asset_id`),
  CONSTRAINT `fk_plan_asset` FOREIGN KEY (`asset_id`) REFERENCES `eq_asset` (`id`)
) ENGINE=InnoDB COMMENT='点检计划';

-- -------------------------------------------------------------
-- 3. 点检项（计划的明细，定义检查什么、合格标准）  [P0]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `eq_inspection_item`;
CREATE TABLE `eq_inspection_item` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `plan_id`       BIGINT UNSIGNED NOT NULL,
  `seq_no`        INT          NOT NULL DEFAULT 1 COMMENT '顺序号',
  `item_name`     VARCHAR(64)  NOT NULL COMMENT '检查项，如"主轴油温""气压"',
  `check_method`  TINYINT      NOT NULL DEFAULT 1 COMMENT '方式 1=目视 2=听音 3=测量 4=试运行',
  `standard_text` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '合格标准描述',
  `min_value`     DECIMAL(12,3) DEFAULT NULL COMMENT '测量下限（方式=测量时使用）',
  `max_value`     DECIMAL(12,3) DEFAULT NULL COMMENT '测量上限',
  `unit`          VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '单位：℃/MPa/mm',
  `required_photo` TINYINT     NOT NULL DEFAULT 0 COMMENT '是否必须拍照留证',
  PRIMARY KEY (`id`),
  KEY `idx_plan_seq` (`plan_id`, `seq_no`),
  CONSTRAINT `fk_item_plan` FOREIGN KEY (`plan_id`) REFERENCES `eq_inspection_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='点检项定义';

-- -------------------------------------------------------------
-- 4. 点检记录（一次点检任务的头）  [P0]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `eq_inspection_record`;
CREATE TABLE `eq_inspection_record` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `plan_id`      BIGINT UNSIGNED NOT NULL,
  `asset_id`     BIGINT UNSIGNED NOT NULL COMMENT '冗余存储，方便按设备统计',
  `record_date`  DATE         NOT NULL,
  `inspector`    VARCHAR(32)  NOT NULL DEFAULT '',
  `result`       TINYINT      NOT NULL DEFAULT 1 COMMENT '总体结果 1=正常 2=异常(触发报修)',
  `abnormal_desc` VARCHAR(255) NOT NULL DEFAULT '',
  `cost_minutes` INT          NOT NULL DEFAULT 0 COMMENT '点检耗时',
  `submit_time`  DATETIME     DEFAULT NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_asset_date` (`asset_id`, `record_date`),
  CONSTRAINT `fk_rec_plan` FOREIGN KEY (`plan_id`) REFERENCES `eq_inspection_plan` (`id`),
  CONSTRAINT `fk_rec_asset` FOREIGN KEY (`asset_id`) REFERENCES `eq_asset` (`id`)
) ENGINE=InnoDB COMMENT='点检记录';

-- -------------------------------------------------------------
-- 5. 点检记录明细（逐项结果）  [P0]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `eq_inspection_record_item`;
CREATE TABLE `eq_inspection_record_item` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `record_id`    BIGINT UNSIGNED NOT NULL,
  `item_id`      BIGINT UNSIGNED NOT NULL,
  `result`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 2=异常',
  `measure_value` DECIMAL(12,3) DEFAULT NULL COMMENT '实测值',
  `photo_url`    VARCHAR(255) NOT NULL DEFAULT '',
  `remark`       VARCHAR(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `idx_record` (`record_id`),
  CONSTRAINT `fk_ri_rec` FOREIGN KEY (`record_id`) REFERENCES `eq_inspection_record` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='点检记录明细';

-- -------------------------------------------------------------
-- 6. 工单（核心表，带状态机）  [P0]
--    状态：10待派单 20待接单 30维修中 35挂起 40待验收 50已关闭 60已驳回 70已取消
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `wo_order`;
CREATE TABLE `wo_order` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`         VARCHAR(32)  NOT NULL COMMENT '工单号 WO202611190001',
  `asset_id`         BIGINT UNSIGNED NOT NULL,
  `source_type`      TINYINT      NOT NULL DEFAULT 1 COMMENT '来源 1=故障报修 2=点检异常 3=计划保养',
  `source_ref_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '来源单据ID（点检记录ID）',
  `fault_type`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '故障分类：机械/电气/液压/气动/程序（用于相似工单标签召回）',
  `fault_desc`       VARCHAR(500) NOT NULL DEFAULT '' COMMENT '故障现象描述，RAG 检索的主查询文本',
  `urgency`          TINYINT      NOT NULL DEFAULT 2 COMMENT '优先级 1=紧急(停机) 2=一般 3=可延后',
  `status`           TINYINT      NOT NULL DEFAULT 10 COMMENT '状态机，见上方注释',
  `reporter`         VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '报修人',
  `report_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `dispatcher`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '派单人',
  `dispatch_time`    DATETIME     DEFAULT NULL,
  `assignee`         VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '维修工',
  `accept_time`      DATETIME     DEFAULT NULL COMMENT '接单时间',
  `start_time`       DATETIME     DEFAULT NULL COMMENT '开工时间（扳手时间起点）',
  `finish_time`      DATETIME     DEFAULT NULL COMMENT '完工时间',
  `downtime_minutes` INT          NOT NULL DEFAULT 0 COMMENT '停机时长 = finish_time - start_time（分钟，关闭时回填）',
  `repair_summary`   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '维修小结（AI 辅助生成，人工确认后落库）',
  `accepted_by`      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '验收人',
  `accept_result`    TINYINT      DEFAULT NULL COMMENT '验收结果 1=通过 2=返修',
  `ai_summary_flag`  TINYINT      NOT NULL DEFAULT 0 COMMENT '维修小结是否由 AI 生成 0=否 1=是（用于面试演示区分）',
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防并发抢单',
  `deleted`          TINYINT      NOT NULL DEFAULT 0,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_status_report` (`status`, `report_time`),
  KEY `idx_asset_report` (`asset_id`, `report_time`),
  KEY `idx_assignee_status` (`assignee`, `status`),
  CONSTRAINT `fk_order_asset` FOREIGN KEY (`asset_id`) REFERENCES `eq_asset` (`id`)
) ENGINE=InnoDB COMMENT='维修工单';

-- -------------------------------------------------------------
-- 7. 工单状态流转流水（状态机审计，面试时讲"可追溯"就靠它）  [P0]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `wo_order_event`;
CREATE TABLE `wo_order_event` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT UNSIGNED NOT NULL,
  `from_status` TINYINT      NOT NULL,
  `to_status`   TINYINT      NOT NULL,
  `action`      VARCHAR(32)  NOT NULL COMMENT 'DISPATCH/ACCEPT/START/SUSPEND/RESUME/FINISH/ACCEPT/CLOSE/REJECT/CANCEL',
  `operator`    VARCHAR(32)  NOT NULL DEFAULT '',
  `remark`      VARCHAR(255) NOT NULL DEFAULT '',
  `occur_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_time` (`order_id`, `occur_time`),
  CONSTRAINT `fk_evt_order` FOREIGN KEY (`order_id`) REFERENCES `wo_order` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='工单状态流转流水';

-- -------------------------------------------------------------
-- 8. 维修记录（一个工单可多次上门/多段维修）  [P0]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `wo_repair_record`;
CREATE TABLE `wo_repair_record` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT UNSIGNED NOT NULL,
  `seq_no`       INT          NOT NULL DEFAULT 1,
  `worker`       VARCHAR(32)  NOT NULL DEFAULT '',
  `start_time`   DATETIME     NOT NULL,
  `end_time`     DATETIME     DEFAULT NULL,
  `fault_cause`  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '根因分析（事后归纳）',
  `solution`     VARCHAR(500) NOT NULL DEFAULT '' COMMENT '处置措施，知识库的主要语料来源',
  `part_replaced` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '更换件描述',
  PRIMARY KEY (`id`),
  KEY `idx_order_seq` (`order_id`, `seq_no`),
  CONSTRAINT `fk_rep_order` FOREIGN KEY (`order_id`) REFERENCES `wo_order` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='维修记录';

-- -------------------------------------------------------------
-- 9. 备件领用（只记录用量，不做库存与采购）  [P1]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `wo_part_usage`;
CREATE TABLE `wo_part_usage` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`   BIGINT UNSIGNED NOT NULL,
  `part_code`  VARCHAR(32)  NOT NULL DEFAULT '',
  `part_name`  VARCHAR(64)  NOT NULL DEFAULT '',
  `qty`        DECIMAL(10,2) NOT NULL DEFAULT 1,
  `unit`       VARCHAR(16)  NOT NULL DEFAULT '个',
  `apply_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  CONSTRAINT `fk_part_order` FOREIGN KEY (`order_id`) REFERENCES `wo_order` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='备件领用记录';

-- -------------------------------------------------------------
-- 10. 工单附件（故障照片/现场视频，衔接你已有的音视频能力）  [P1]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `wo_attachment`;
CREATE TABLE `wo_attachment` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`   BIGINT UNSIGNED NOT NULL,
  `file_type`  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=图片 2=视频 3=文档',
  `file_url`   VARCHAR(255) NOT NULL COMMENT 'MinIO/本地静态资源地址',
  `file_name`  VARCHAR(128) NOT NULL DEFAULT '',
  `file_size`  BIGINT       NOT NULL DEFAULT 0,
  `uploader`   VARCHAR(32)  NOT NULL DEFAULT '',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  CONSTRAINT `fk_att_order` FOREIGN KEY (`order_id`) REFERENCES `wo_order` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='工单附件';

-- -------------------------------------------------------------
-- 11. 知识文档（设备手册 / 维修SOP / 历史工单汇总）  [P0-RAG]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `km_document`;
CREATE TABLE `km_document` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `doc_name`     VARCHAR(128) NOT NULL,
  `doc_type`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1=设备手册 2=维修SOP 3=历史工单 4=其他',
  `asset_type`   VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '适用设备类型，检索时用于元数据过滤',
  `file_url`     VARCHAR(255) NOT NULL DEFAULT '',
  `file_hash`    CHAR(64)     NOT NULL DEFAULT '' COMMENT 'SHA-256，幂等去重防重复入库',
  `parse_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待解析 1=解析中 2=成功 3=失败',
  `parse_msg`    VARCHAR(255) NOT NULL DEFAULT '',
  `chunk_count`  INT          NOT NULL DEFAULT 0,
  `version`      INT          NOT NULL DEFAULT 1,
  `uploader`     VARCHAR(32)  NOT NULL DEFAULT '',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_hash` (`file_hash`),
  KEY `idx_type` (`doc_type`, `asset_type`)
) ENGINE=InnoDB COMMENT='知识库文档';

-- -------------------------------------------------------------
-- 12. 文档分块 + 向量  [P0-RAG]
--    向量以 float32 二进制存 BLOB；MVP 阶段 JVM 内暴力余弦检索（几千块足够快）
--    量级上来后换 pgvector / Milvus，接口层预留 EmbeddingStore 抽象
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `km_chunk`;
CREATE TABLE `km_chunk` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `doc_id`          BIGINT UNSIGNED NOT NULL,
  `chunk_no`        INT          NOT NULL COMMENT '块序',
  `section_path`    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '章节路径，如 3.2.1 主轴异响 > 处理步骤（用于引用出处展示）',
  `page_no`         INT          DEFAULT NULL COMMENT '原文档页码（PDF 溯源）',
  `content`         TEXT         NOT NULL COMMENT '块正文',
  `token_count`     INT          NOT NULL DEFAULT 0,
  `embedding`       BLOB         DEFAULT NULL COMMENT 'float32 小端序向量',
  `embedding_model` VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '如 bge-m3 / text-embedding-3-small，换模型需全量重算',
  `dim`             SMALLINT     NOT NULL DEFAULT 0,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc_chunk` (`doc_id`, `chunk_no`),
  FULLTEXT KEY `ft_content` (`content`) WITH PARSER ngram COMMENT '中文关键词召回，与向量召回混合排序',
  CONSTRAINT `fk_chunk_doc` FOREIGN KEY (`doc_id`) REFERENCES `km_document` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='知识库分块';

-- -------------------------------------------------------------
-- 13. 相似历史工单召回留痕（可量化评估，面试能拿数据说话）  [P1]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_similar_ref`;
CREATE TABLE `ai_similar_ref` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT UNSIGNED NOT NULL COMMENT '当前工单',
  `ref_order_id` BIGINT UNSIGNED NOT NULL COMMENT '被召回的历史工单',
  `score`        DECIMAL(6,4) NOT NULL DEFAULT 0 COMMENT '余弦相似度',
  `hit`          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否被维修工采纳 1=是（人工反馈，做评估用）',
  `recall_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_score` (`order_id`, `score`),
  CONSTRAINT `fk_sim_order` FOREIGN KEY (`order_id`) REFERENCES `wo_order` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='相似工单召回记录';

-- -------------------------------------------------------------
-- 14. 知识问答日志（演示回溯 + 延迟/召回质量观测）  [P1]
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_qa_log`;
CREATE TABLE `ai_qa_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id`    VARCHAR(64)  NOT NULL DEFAULT '',
  `question`      VARCHAR(500) NOT NULL,
  `answer`        TEXT         NOT NULL,
  `cited_chunk_ids` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '引用块ID，逗号分隔，支撑"答案可溯源"',
  `latency_ms`    INT          NOT NULL DEFAULT 0,
  `hit_cache`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否命中 Redis 缓存',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB COMMENT='问答日志';

-- =============================================================
-- 演示用最小种子数据（虚拟产线，面试时明确说明非真实工厂数据）
-- =============================================================
INSERT INTO `eq_asset` (`asset_code`,`asset_name`,`asset_type`,`workshop`,`line_name`,`location`,`manufacturer`,`model`,`critical_level`)
VALUES
 ('CNC-A-01','立式加工中心','CNC','一号车间','A线','A区1号位','虚拟机床厂','VMC-850',1),
 ('IJM-B-02','注塑机','注塑机','二号车间','B线','B区3号位','虚拟注塑厂','HT-160',2),
 ('AGV-C-03','搬运AGV','AGV','立体库','C线','库区通道','虚拟物流厂','AGV-500',3);

INSERT INTO `eq_inspection_plan` (`plan_code`,`plan_name`,`asset_id`,`cycle_type`,`executor_role`)
VALUES ('IP-CNC-01','加工中心班前点检',1,1,'操作工');

INSERT INTO `eq_inspection_item` (`plan_id`,`seq_no`,`item_name`,`check_method`,`standard_text`,`min_value`,`max_value`,`unit`,`required_photo`)
VALUES
 (1,1,'主轴油温',3,'油温不超过 45℃',NULL,45,'℃',0),
 (1,2,'气压',3,'0.5~0.7MPa',0.5,0.7,'MPa',0),
 (1,3,'导轨润滑状态',1,'无干摩擦、无渗漏',NULL,NULL,'',1),
 (1,4,'主轴异响',2,'空转无异响',NULL,NULL,'',0);
