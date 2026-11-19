package com.eqmaint.inspection;

import java.math.BigDecimal;
import java.util.List;

public record RecordSubmitReq(Long planId, String inspector, Integer costMinutes,
                              String abnormalDesc, List<RecordItemReq> items) {

    public record RecordItemReq(Long itemId, Integer result, BigDecimal measureValue,
                                String photoUrl, String remark) {
    }
}