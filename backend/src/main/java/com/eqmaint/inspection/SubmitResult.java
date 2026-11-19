package com.eqmaint.inspection;

public record SubmitResult(Long recordId, Integer result, int abnormalCount,
                           Long orderId, String orderNo) {
}