package com.eqmaint.inspection;

import java.math.BigDecimal;
import java.util.List;

public record PlanCreateReq(String planCode, String planName, Long assetId,
                            Integer cycleType, String executorRole, List<ItemReq> items) {

    public record ItemReq(String itemName, Integer checkMethod, String standardText,
                          BigDecimal minValue, BigDecimal maxValue, String unit,
                          Integer requiredPhoto) {
    }
}