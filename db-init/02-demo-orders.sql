-- =============================================================
-- 演示工单数据（虚拟产线，用于停机统计演示）
-- 清除方式：DELETE FROM wo_order WHERE order_no LIKE 'WO-DEMO-%';
-- 说明：本脚本写入的是虚构数据，不是真实工厂记录，面试时须明确标注
-- =============================================================
USE `repair_order`;
SET NAMES utf8mb4;

DELETE FROM `wo_order` WHERE `order_no` LIKE 'WO-DEMO-%';

INSERT INTO `wo_order`
(`order_no`,`asset_id`,`source_type`,`fault_type`,`fault_desc`,`urgency`,`status`,
 `reporter`,`report_time`,`dispatcher`,`dispatch_time`,`assignee`,`accept_time`,`start_time`,`finish_time`,
 `downtime_minutes`,`repair_summary`,`accepted_by`,`accept_result`,`ai_summary_flag`,`version`,`deleted`)
VALUES
('WO-DEMO-01',1,1,'机械','主轴运转周期性异响，转速升高后明显',1,50,'王操作','2026-11-14 08:20:00','李调度','2026-11-14 08:35:00','张机修','2026-11-14 08:50:00','2026-11-14 09:00:00','2026-11-14 11:00:00',120,'更换主轴皮带并调整张紧度','李调度',1,0,1,0),
('WO-DEMO-02',1,1,'电气','X轴伺服报警 AL-31，定位漂移',1,50,'王操作','2026-11-16 10:05:00','李调度','2026-11-16 10:12:00','赵电工','2026-11-16 10:30:00','2026-11-16 10:40:00','2026-11-16 12:15:00',95,'更换伺服驱动器散热风扇，复位报警','李调度',1,0,1,0),
('WO-DEMO-03',1,1,'机械','刀库换刀卡滞，偶发不到位',2,50,'刘操作','2026-11-18 14:00:00','李调度','2026-11-18 14:10:00','张机修','2026-11-18 14:20:00','2026-11-18 14:30:00','2026-11-18 15:15:00',45,'清理刀库导轨铁屑，补充润滑脂','李调度',1,0,1,0),
('WO-DEMO-04',2,1,'液压','合模压力不足，产品飞边',1,50,'陈操作','2026-11-15 06:30:00','李调度','2026-11-15 06:40:00','孙机修','2026-11-15 06:50:00','2026-11-15 07:00:00','2026-11-15 10:00:00',180,'更换液压泵密封件，补油至标准液位','李调度',1,0,1,0),
('WO-DEMO-05',2,1,'电气','料筒加热圈温度波动超限',2,50,'陈操作','2026-11-17 09:00:00','李调度','2026-11-17 09:08:00','赵电工','2026-11-17 09:15:00','2026-11-17 09:20:00','2026-11-17 10:20:00',60,'更换一段加热圈及热电偶','李调度',1,0,1,0),
('WO-DEMO-06',3,1,'气动','AGV 举升气缸动作缓慢',2,50,'库管员','2026-11-13 13:10:00','李调度','2026-11-13 13:18:00','孙机修','2026-11-13 13:25:00','2026-11-13 13:30:00','2026-11-13 14:05:00',35,'更换气缸密封圈，清洗气路','李调度',1,0,1,0),
('WO-DEMO-07',2,1,'机械','顶针机构复位异响，顶出不到位',1,40,'陈操作','2026-11-19 07:40:00','李调度','2026-11-19 07:48:00','孙机修','2026-11-19 07:55:00','2026-11-19 08:00:00','2026-11-19 12:00:00',240,'更换顶针导套，调整行程限位','',NULL,0,1,0),
('WO-DEMO-08',1,1,'电气','控制柜散热风扇异响，柜内温度偏高',2,30,'王操作','2026-11-19 11:30:00','李调度','2026-11-19 11:38:00','赵电工','2026-11-19 11:45:00','2026-11-19 11:50:00',NULL,0,'','',NULL,0,1,0),
('WO-DEMO-09',3,1,'程序','路径点偏移，取放料对位不准',2,50,'库管员','2026-11-18 16:00:00','李调度','2026-11-18 16:05:00','钱工控','2026-11-18 16:10:00','2026-11-18 16:15:00','2026-11-18 16:35:00',20,'重新标定取放料点位，更新地图','李调度',1,0,1,0),
('WO-DEMO-10',1,1,'机械','冷却液泵不出液',2,50,'刘操作','2026-11-12 08:00:00','李调度','2026-11-12 08:10:00','张机修','2026-11-12 08:20:00','2026-11-12 08:30:00','2026-11-12 09:45:00',75,'清理泵入口滤网，更换叶轮','李调度',1,0,1,0),
('WO-DEMO-11',2,1,'液压','锁模油缸渗油',2,20,'陈操作','2026-11-19 15:10:00','李调度','2026-11-19 15:18:00','',NULL,NULL,NULL,0,'','',NULL,0,1,0),
('WO-DEMO-12',3,1,'机械','驱动轮打滑，行走偏移',2,50,'库管员','2026-11-16 11:00:00','李调度','2026-11-16 11:10:00','孙机修','2026-11-16 11:20:00','2026-11-16 11:25:00','2026-11-16 12:15:00',50,'更换驱动轮橡胶胎面','李调度',1,0,1,0);

-- 演示点检记录（含异常，用于统计点检异常率）
DELETE FROM `eq_inspection_record` WHERE `abnormal_desc` LIKE '%演示%';

-- -------------------------------------------------------------
-- 演示工单的状态流转流水：没有这段，工单详情页的"流转记录"时间轴是空的
-- 动作名与后端 WorkOrderService.saveEvent 保持一致：CREATE / DISPATCH / ACCEPT / FINISH / PASS
-- wo_order_event 对 wo_order 是 ON DELETE CASCADE，上层 DELETE 会一并清掉，无需单独清理
-- -------------------------------------------------------------
INSERT INTO `wo_order_event` (`order_id`,`from_status`,`to_status`,`action`,`operator`,`remark`,`occur_time`)
SELECT o.id, 10, 10, 'CREATE', o.reporter, '工单创建', o.report_time
FROM `wo_order` o WHERE o.order_no LIKE 'WO-DEMO-%';

INSERT INTO `wo_order_event` (`order_id`,`from_status`,`to_status`,`action`,`operator`,`remark`,`occur_time`)
SELECT o.id, 10, 20, 'DISPATCH', o.dispatcher, '派单给机电维修班组', o.dispatch_time
FROM `wo_order` o WHERE o.order_no LIKE 'WO-DEMO-%' AND o.dispatch_time IS NOT NULL;

INSERT INTO `wo_order_event` (`order_id`,`from_status`,`to_status`,`action`,`operator`,`remark`,`occur_time`)
SELECT o.id, 20, 30, 'ACCEPT', o.assignee, '接单并到场', o.accept_time
FROM `wo_order` o WHERE o.order_no LIKE 'WO-DEMO-%' AND o.accept_time IS NOT NULL
  AND o.status IN (30, 35, 40, 50);

INSERT INTO `wo_order_event` (`order_id`,`from_status`,`to_status`,`action`,`operator`,`remark`,`occur_time`)
SELECT o.id, 30, 40, 'FINISH', o.assignee, '维修完成，待验收', o.finish_time
FROM `wo_order` o WHERE o.order_no LIKE 'WO-DEMO-%' AND o.finish_time IS NOT NULL
  AND o.status IN (40, 50);

INSERT INTO `wo_order_event` (`order_id`,`from_status`,`to_status`,`action`,`operator`,`remark`,`occur_time`)
SELECT o.id, 40, 50, 'PASS', COALESCE(o.accepted_by, ''), '验收通过，工单关闭',
       DATE_ADD(o.finish_time, INTERVAL 10 MINUTE)
FROM `wo_order` o WHERE o.order_no LIKE 'WO-DEMO-%' AND o.status = 50 AND o.finish_time IS NOT NULL;
