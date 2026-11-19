package com.eqmaint.workorder;

import com.eqmaint.common.BizException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class WorkOrderStateMachine {

    private static final Map<OrderStatus, Map<OrderAction, OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        allow(OrderStatus.REPORTED, OrderAction.DISPATCH, OrderStatus.WAIT_ACCEPT);
        allow(OrderStatus.REPORTED, OrderAction.CANCEL, OrderStatus.CANCELED);
        allow(OrderStatus.REPORTED, OrderAction.REJECT, OrderStatus.REJECTED);

        allow(OrderStatus.WAIT_ACCEPT, OrderAction.ACCEPT, OrderStatus.REPAIRING);
        allow(OrderStatus.WAIT_ACCEPT, OrderAction.CANCEL, OrderStatus.CANCELED);

        allow(OrderStatus.REPAIRING, OrderAction.SUSPEND, OrderStatus.SUSPENDED);
        allow(OrderStatus.REPAIRING, OrderAction.FINISH, OrderStatus.WAIT_ACCEPTANCE);

        allow(OrderStatus.SUSPENDED, OrderAction.RESUME, OrderStatus.REPAIRING);
        allow(OrderStatus.SUSPENDED, OrderAction.FINISH, OrderStatus.WAIT_ACCEPTANCE);

        allow(OrderStatus.WAIT_ACCEPTANCE, OrderAction.PASS, OrderStatus.CLOSED);
        allow(OrderStatus.WAIT_ACCEPTANCE, OrderAction.REWORK, OrderStatus.REPAIRING);
    }

    private static void allow(OrderStatus from, OrderAction action, OrderStatus to) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(OrderAction.class)).put(action, to);
    }

    public OrderStatus next(OrderStatus current, OrderAction action) {
        Map<OrderAction, OrderStatus> actions = TRANSITIONS.get(current);
        OrderStatus target = actions == null ? null : actions.get(action);
        if (target == null) {
            throw new BizException("工单当前状态[" + current.getLabel() + "]不允许执行[" + action.getLabel() + "]");
        }
        return target;
    }

    public boolean isFinal(OrderStatus status) {
        return status == OrderStatus.CLOSED || status == OrderStatus.REJECTED || status == OrderStatus.CANCELED;
    }
}