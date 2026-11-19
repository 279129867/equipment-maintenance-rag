package com.eqmaint.inspection;

import java.util.List;

public record InspectionRecordDetail(EqInspectionRecord record,
                                    List<EqInspectionRecordItem> items,
                                    Long orderId,
                                    String orderNo) {
}