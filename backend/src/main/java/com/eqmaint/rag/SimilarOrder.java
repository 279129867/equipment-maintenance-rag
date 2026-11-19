package com.eqmaint.rag;

public record SimilarOrder(Long orderId, String orderNo, String faultType, String faultDesc,
                           String repairSummary, Integer downtimeMinutes, double score) {
}