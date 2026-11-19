package com.eqmaint.workorder;

import java.util.List;

public record WorkOrderDetail(WoOrder order, List<WoOrderEvent> events) {
}