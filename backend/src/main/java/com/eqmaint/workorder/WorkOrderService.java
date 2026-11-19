package com.eqmaint.workorder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eqmaint.asset.AssetMapper;
import com.eqmaint.asset.EqAsset;
import com.eqmaint.common.BizException;
import com.eqmaint.common.event.OrderClosedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WorkOrderService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderEventMapper eventMapper;
    private final AssetMapper assetMapper;
    private final WorkOrderStateMachine stateMachine;
    private final ApplicationEventPublisher publisher;

    public WorkOrderService(OrderMapper orderMapper,
                            OrderEventMapper eventMapper,
                            AssetMapper assetMapper,
                            WorkOrderStateMachine stateMachine,
                            ApplicationEventPublisher publisher) {
        this.orderMapper = orderMapper;
        this.eventMapper = eventMapper;
        this.assetMapper = assetMapper;
        this.stateMachine = stateMachine;
        this.publisher = publisher;
    }

    @Transactional
    public WoOrder create(Long assetId, String faultType, String faultDesc, Integer urgency, String reporter) {
        return create(assetId, 1, null, faultType, faultDesc, urgency, reporter);
    }

    @Transactional
    public WoOrder create(Long assetId, Integer sourceType, Long sourceRefId, String faultType,
                          String faultDesc, Integer urgency, String reporter) {
        EqAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BizException("设备不存在：" + assetId);
        }
        if (!StringUtils.hasText(faultDesc)) {
            throw new BizException("故障描述不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        WoOrder order = new WoOrder();
        order.setOrderNo(nextOrderNo(now));
        order.setAssetId(assetId);
        order.setSourceType(sourceType == null ? 1 : sourceType);
        order.setSourceRefId(sourceRefId);
        order.setFaultType(faultType == null ? "" : faultType);
        order.setFaultDesc(faultDesc);
        Integer defaultUrgency = (asset.getCriticalLevel() != null && asset.getCriticalLevel() == 1) ? 1 : 2;
        order.setUrgency(urgency == null ? defaultUrgency : urgency);
        order.setStatus(OrderStatus.REPORTED.getCode());
        order.setReporter(reporter == null ? "" : reporter);
        order.setReportTime(now);
        order.setDowntimeMinutes(0);
        order.setAiSummaryFlag(0);
        order.setVersion(0);
        orderMapper.insert(order);

        if (Integer.valueOf(1).equals(order.getUrgency())) {
            changeAssetStatus(assetId, 3);
        }
        saveEvent(order.getId(), order.getStatus(), order.getStatus(), "CREATE", order.getReporter(), "工单创建", now);
        return order;
    }

    @Transactional
    public WoOrder transition(Long id, OrderAction action, String operator, String remark) {
        WoOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("工单不存在：" + id);
        }
        OrderStatus current = OrderStatus.of(order.getStatus());
        if (stateMachine.isFinal(current)) {
            throw new BizException("工单已结束（" + current.getLabel() + "），不能再操作");
        }
        OrderStatus target = stateMachine.next(current, action);
        LocalDateTime now = LocalDateTime.now();

        LambdaUpdateWrapper<WoOrder> uw = new LambdaUpdateWrapper<WoOrder>()
                .eq(WoOrder::getId, id)
                .eq(WoOrder::getVersion, order.getVersion())
                .set(WoOrder::getStatus, target.getCode())
                .set(WoOrder::getVersion, order.getVersion() + 1)
                .set(WoOrder::getUpdateTime, now);

        switch (action) {
            case DISPATCH -> uw.set(WoOrder::getDispatcher, operator)
                    .set(WoOrder::getDispatchTime, now);
            case ACCEPT -> uw.set(WoOrder::getAssignee, operator)
                    .set(WoOrder::getAcceptTime, now)
                    .set(WoOrder::getStartTime, now);
            case FINISH -> {
                uw.set(WoOrder::getFinishTime, now);
                long minutes = order.getStartTime() == null
                        ? 0L
                        : Math.max(0L, Duration.between(order.getStartTime(), now).toMinutes());
                uw.set(WoOrder::getDowntimeMinutes, (int) minutes);
            }
            case PASS -> uw.set(WoOrder::getAcceptedBy, operator)
                    .set(WoOrder::getAcceptResult, 1);
            case REWORK -> uw.set(WoOrder::getAcceptedBy, operator)
                    .set(WoOrder::getAcceptResult, 2)
                    .set(WoOrder::getFinishTime, null);
            default -> {
            }
        }

        int rows = orderMapper.update(null, uw);
        if (rows == 0) {
            throw new BizException("工单已被他人修改，请刷新后重试（乐观锁冲突）");
        }
        saveEvent(id, order.getStatus(), target.getCode(), action.name(), operator, remark, now);
        if (action == OrderAction.PASS) {
            changeAssetStatus(order.getAssetId(), 1);
            // 事务提交后再索引，避免 RagIndexService 读到未提交数据；失败不影响关闭
            publisher.publishEvent(new OrderClosedEvent(id));
        }
        return orderMapper.selectById(id);
    }

    public Page<WoOrder> page(int pageNo, int size, Integer status, Long assetId) {
        LambdaQueryWrapper<WoOrder> qw = new LambdaQueryWrapper<>();
        qw.eq(status != null, WoOrder::getStatus, status)
          .eq(assetId != null, WoOrder::getAssetId, assetId)
          .orderByDesc(WoOrder::getReportTime);
        return orderMapper.selectPage(new Page<>(pageNo, size), qw);
    }

    public WorkOrderDetail detail(Long id) {
        WoOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("工单不存在：" + id);
        }
        List<WoOrderEvent> events = eventMapper.selectList(new LambdaQueryWrapper<WoOrderEvent>()
                .eq(WoOrderEvent::getOrderId, id)
                .orderByAsc(WoOrderEvent::getOccurTime)
                .orderByAsc(WoOrderEvent::getId));
        return new WorkOrderDetail(order, events);
    }

    public WoOrder saveSummary(Long id, String summary, boolean aiGenerated) {
        WoOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("工单不存在：" + id);
        }
        LambdaUpdateWrapper<WoOrder> uw = new LambdaUpdateWrapper<WoOrder>()
                .eq(WoOrder::getId, id)
                .set(WoOrder::getRepairSummary, summary)
                .set(WoOrder::getAiSummaryFlag, aiGenerated ? 1 : 0);
        orderMapper.update(null, uw);
        return orderMapper.selectById(id);
    }

    private void saveEvent(Long orderId, Integer from, Integer to, String action,
                           String operator, String remark, LocalDateTime occurTime) {
        WoOrderEvent event = new WoOrderEvent();
        event.setOrderId(orderId);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setAction(action);
        event.setOperator(operator == null ? "" : operator);
        event.setRemark(remark == null ? "" : remark);
        event.setOccurTime(occurTime);
        eventMapper.insert(event);
    }

    private void changeAssetStatus(Long assetId, Integer runStatus) {
        assetMapper.update(null, new LambdaUpdateWrapper<EqAsset>()
                .eq(EqAsset::getId, assetId)
                .set(EqAsset::getRunStatus, runStatus));
    }

    private String nextOrderNo(LocalDateTime now) {
        return "WO" + now.format(NO_FMT) + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }
}