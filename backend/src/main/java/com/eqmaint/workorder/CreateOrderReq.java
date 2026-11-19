package com.eqmaint.workorder;

public record CreateOrderReq(Long assetId, String faultType, String faultDesc,
                             Integer urgency, String reporter) {
}