-- =============================================================
-- 增量迁移：历史工单向量表（用于相似工单推荐）
-- 说明：主表结构见 01-库表设计.sql；本表为 RAG 上线后新增
-- =============================================================
USE `repair_order`;

CREATE TABLE IF NOT EXISTS `ai_order_embedding` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`        BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
  `content`         TEXT         NOT NULL COMMENT '参与向量化的文本：故障类别+现象+处置方案',
  `embedding`       BLOB         DEFAULT NULL COMMENT 'float32 小端序向量',
  `embedding_model` VARCHAR(64)  NOT NULL DEFAULT '',
  `dim`             SMALLINT     NOT NULL DEFAULT 0,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`)
) ENGINE=InnoDB COMMENT='历史工单向量';